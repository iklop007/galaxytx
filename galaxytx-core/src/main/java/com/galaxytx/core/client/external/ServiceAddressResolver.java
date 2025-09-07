package com.galaxytx.core.client.external;

import com.galaxytx.core.config.ServiceDiscoveryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 服务地址解析器
 * 支持多种方式解析资源ID到具体的服务地址
 */
@Component
public class ServiceAddressResolver {
    private static final Logger logger = LoggerFactory.getLogger(ServiceAddressResolver.class);

    // 服务地址缓存
    private static final Map<String, String> SERVICE_ADDRESS_CACHE = new ConcurrentHashMap<>();
    // 失败的服务地址缓存（避免频繁查询失败的服务）
    private static final Map<String, Long> FAILED_SERVICE_CACHE = new ConcurrentHashMap<>();

    private static final long FAILURE_CACHE_DURATION = 30000; // 30秒

    @Autowired(required = false)
    private static ServiceDiscoveryConfig serviceDiscoveryConfig;

    @Autowired(required = false)
    private static Environment environment;

    // 服务发现客户端（可选）
    private Object serviceDiscoveryClient;

    // URL模式匹配
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.*");
    private static final Pattern SERVICE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern SERVICE_WITH_NAMESPACE_PATTERN = Pattern.compile("^([a-zA-Z0-9_-]+)\\.([a-zA-Z0-9_-]+)$");

    /**
     * 获取服务地址（主方法）
     */
    public static String getServiceAddress(String resourceId) {
        if (resourceId == null || resourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Resource ID cannot be null or empty");
        }

        // 检查失败缓存
        if (isInFailureCache(resourceId)) {
            throw new ServiceResolutionException("Service is in failure cache: " + resourceId);
        }

        // 首先检查缓存
        String cachedAddress = SERVICE_ADDRESS_CACHE.get(resourceId);
        if (cachedAddress != null) {
            logger.debug("Using cached service address for {}: {}", resourceId, cachedAddress);
            return cachedAddress;
        }

        try {
            String serviceAddress = resolveServiceAddress(resourceId);

            // 验证地址格式
            validateServiceAddress(serviceAddress);

            // 缓存成功的结果
            SERVICE_ADDRESS_CACHE.put(resourceId, serviceAddress);
            logger.info("Resolved service address for {}: {}", resourceId, serviceAddress);

            return serviceAddress;

        } catch (Exception e) {
            // 加入失败缓存
            addToFailureCache(resourceId);
            throw new ServiceResolutionException("Failed to resolve service address for: " + resourceId, e);
        }
    }

    /**
     * 解析服务地址（核心逻辑）
     */
    private static String resolveServiceAddress(String resourceId) {
        // 1. 如果已经是完整的URL，直接返回
        if (isFullUrl(resourceId)) {
            return normalizeUrl(resourceId);
        }

        // 2. 检查配置中的显式映射
        String configuredAddress = getFromConfiguration(resourceId);
        if (configuredAddress != null) {
            return configuredAddress;
        }

        // 3. 尝试从服务发现解析
        String discoveredAddress = resolveFromServiceDiscovery(resourceId);
        if (discoveredAddress != null) {
            return discoveredAddress;
        }

        // 4. 尝试模式匹配
        String patternAddress = resolveFromPattern(resourceId);
        if (patternAddress != null) {
            return patternAddress;
        }

        // 5. 默认策略
        return resolveWithDefaultStrategy(resourceId);
    }

    /**
     * 检查是否是完整的URL
     */
    private static boolean isFullUrl(String resourceId) {
        return URL_PATTERN.matcher(resourceId).matches();
    }

    /**
     * 规范化URL
     */
    private static String normalizeUrl(String url) {
        try {
            URI uri = new URI(url);
            String normalized = uri.normalize().toString();

            // 确保以/结尾的URL正确处理
            if (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }

            return normalized;
        } catch (URISyntaxException e) {
            throw new ServiceResolutionException("Invalid URL format: " + url, e);
        }
    }

    /**
     * 从配置中获取服务地址
     */
    private static String getFromConfiguration(String resourceId) {
        // 首先检查Spring环境配置
        if (environment != null) {
            String configKey = "galaxytx.service.mapping." + resourceId;
            String address = environment.getProperty(configKey);
            if (address != null && !address.trim().isEmpty()) {
                return address;
            }
        }

        // 检查自定义配置
        if (serviceDiscoveryConfig != null) {
            Map<String, String> serviceMappings = serviceDiscoveryConfig.getServiceMappings();
            if (serviceMappings != null && serviceMappings.containsKey(resourceId)) {
                return serviceMappings.get(resourceId);
            }
        }

        return null;
    }

    /**
     * 从服务发现解析地址
     */
    private static String resolveFromServiceDiscovery(String resourceId) {
        // 如果配置了服务发现且可用
        if (serviceDiscoveryConfig != null && serviceDiscoveryConfig.isServiceDiscoveryEnabled()) {
            try {
                return discoverService(resourceId);
            } catch (Exception e) {
                logger.warn("Service discovery failed for {}: {}", resourceId, e.getMessage());
                // 服务发现失败不影响其他解析方式
            }
        }
        return null;
    }

    /**
     * 实际的服务发现逻辑
     */
    private static String discoverService(String serviceName) {
        // 这里可以集成各种服务发现机制
        // 例如：Consul, Eureka, Nacos, Zookeeper, Kubernetes等

        String discoveryType = serviceDiscoveryConfig.getDiscoveryType();

        switch (discoveryType.toLowerCase()) {
            case "consul":
                return discoverWithConsul(serviceName);
            case "eureka":
                return discoverWithEureka(serviceName);
            case "nacos":
                return discoverWithNacos(serviceName);
            case "kubernetes":
                return discoverWithKubernetes(serviceName);
            case "static":
                return discoverWithStaticConfig(serviceName);
            case "dns":
                return discoverWithDns(serviceName);
            default:
                logger.warn("Unsupported discovery type: {}", discoveryType);
                return null;
        }
    }

    /**
     * 通过模式匹配解析地址
     */
    private static String resolveFromPattern(String resourceId) {
        // 检查是否是服务名.命名空间的格式
        Matcher namespaceMatcher = SERVICE_WITH_NAMESPACE_PATTERN.matcher(resourceId);
        if (namespaceMatcher.matches()) {
            String serviceName = namespaceMatcher.group(1);
            String namespace = namespaceMatcher.group(2);
            return resolveNamespacedService(serviceName, namespace);
        }

        // 检查是否是纯服务名
        if (SERVICE_NAME_PATTERN.matcher(resourceId).matches()) {
            // 尝试常见的服务地址模式
            return tryCommonPatterns(resourceId);
        }

        return null;
    }

    /**
     * 默认解析策略
     */
    private static String resolveWithDefaultStrategy(String resourceId) {
        // 1. 尝试作为服务名处理
        if (SERVICE_NAME_PATTERN.matcher(resourceId).matches()) {
            return buildDefaultServiceUrl(resourceId);
        }

        // 2. 尝试作为主机名处理
        if (isLikelyHostname(resourceId)) {
            return buildUrlFromHostname(resourceId);
        }

        // 3. 无法解析
        throw new ServiceResolutionException("Cannot resolve service address for: " + resourceId);
    }

    /**
     * 构建默认的服务URL
     */
    private static String buildDefaultServiceUrl(String serviceName) {
        String protocol = serviceDiscoveryConfig != null ?
                serviceDiscoveryConfig.getDefaultProtocol() : "http";
        String domain = serviceDiscoveryConfig != null ?
                serviceDiscoveryConfig.getDefaultDomain() : "localhost";
        int port = serviceDiscoveryConfig != null ?
                serviceDiscoveryConfig.getDefaultPort() : 8080;

        return String.format("%s://%s.%s:%d", protocol, serviceName, domain, port);
    }

    /**
     * 从主机名构建URL
     */
    private static String buildUrlFromHostname(String hostname) {
        String protocol = serviceDiscoveryConfig != null ?
                serviceDiscoveryConfig.getDefaultProtocol() : "http";
        int port = serviceDiscoveryConfig != null ?
                serviceDiscoveryConfig.getDefaultPort() : 8080;

        return String.format("%s://%s:%d", protocol, hostname, port);
    }

    /**
     * 尝试常见模式
     */
    private static String tryCommonPatterns(String serviceName) {
        List<String> patterns = Arrays.asList(
                "http://" + serviceName + ".service.consul",
                "http://" + serviceName + ".default.svc.cluster.local",
                "http://" + serviceName + ".local",
                "https://api." + serviceName + ".com",
                "http://api-" + serviceName + ".example.com"
        );

        for (String pattern : patterns) {
            logger.debug("Trying pattern: {}", pattern);
            // 这里可以添加实际的验证逻辑
            if (isLikelyValidUrl(pattern)) {
                return pattern;
            }
        }

        return null;
    }

    /**
     * 验证服务地址格式
     */
    private static void validateServiceAddress(String address) {
        try {
            URI uri = new URI(address);
            if (uri.getScheme() == null) {
                throw new ServiceResolutionException("Service address must have a scheme: " + address);
            }
            if (uri.getHost() == null) {
                throw new ServiceResolutionException("Service address must have a host: " + address);
            }
        } catch (URISyntaxException e) {
            throw new ServiceResolutionException("Invalid service address format: " + address, e);
        }
    }

    /**
     * 失败缓存管理
     */
    private static boolean isInFailureCache(String resourceId) {
        Long failureTime = FAILED_SERVICE_CACHE.get(resourceId);
        if (failureTime != null) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - failureTime < FAILURE_CACHE_DURATION) {
                return true;
            } else {
                // 过期了，移除
                FAILED_SERVICE_CACHE.remove(resourceId);
            }
        }
        return false;
    }

    private static void addToFailureCache(String resourceId) {
        FAILED_SERVICE_CACHE.put(resourceId, System.currentTimeMillis());
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        SERVICE_ADDRESS_CACHE.clear();
        FAILED_SERVICE_CACHE.clear();
    }

    public void clearCache(String resourceId) {
        SERVICE_ADDRESS_CACHE.remove(resourceId);
        FAILED_SERVICE_CACHE.remove(resourceId);
    }

    // 以下是一些服务发现的示例实现（需要相应的客户端依赖）

    private static String discoverWithConsul(String serviceName) {
        logger.debug("Discovering service with Consul: {}", serviceName);
        // 实际集成需要consul客户端
        // ConsulClient client = new ConsulClient();
        // List<HealthService> services = client.getHealthServices(serviceName, true, null).getValue();
        // return selectBestService(services);
        return null;
    }

    private static String discoverWithEureka(String serviceName) {
        logger.debug("Discovering service with Eureka: {}", serviceName);
        // 需要Eureka客户端
        return null;
    }

    private static String discoverWithNacos(String serviceName) {
        logger.debug("Discovering service with Nacos: {}", serviceName);
        // 需要Nacos客户端
        return null;
    }

    private static String discoverWithKubernetes(String serviceName) {
        logger.debug("Discovering service with Kubernetes: {}", serviceName);
        // Kubernetes服务发现逻辑
        return String.format("http://%s.default.svc.cluster.local:8080", serviceName);
    }

    private static String discoverWithStaticConfig(String serviceName) {
        logger.debug("Discovering service with static config: {}", serviceName);
        return getFromConfiguration(serviceName);
    }

    private static String discoverWithDns(String serviceName) {
        logger.debug("Discovering service with DNS: {}", serviceName);
        // DNS服务发现
        return String.format("http://%s:8080", serviceName);
    }

    private static String resolveNamespacedService(String serviceName, String namespace) {
        // 处理带命名空间的服务
        if ("default".equalsIgnoreCase(namespace)) {
            return buildDefaultServiceUrl(serviceName);
        }
        return String.format("http://%s.%s.svc.cluster.local:8080", serviceName, namespace);
    }

    private static boolean isLikelyHostname(String resourceId) {
        // 简单的主机名验证
        return resourceId.contains(".") && !resourceId.contains("/") && !resourceId.contains(":");
    }

    private static boolean isLikelyValidUrl(String url) {
        try {
            new URI(url);
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * 服务解析异常
     */
    public static class ServiceResolutionException extends RuntimeException {
        public ServiceResolutionException(String message) {
            super(message);
        }

        public ServiceResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}