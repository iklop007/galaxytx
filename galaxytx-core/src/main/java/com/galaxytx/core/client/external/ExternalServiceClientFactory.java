package com.galaxytx.core.client.external;

import com.galaxytx.core.model.ServiceEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外部服务客户端工厂
 */
public class ExternalServiceClientFactory {
    private static final Logger logger = LoggerFactory.getLogger(ExternalServiceClientFactory.class);

    // 客户端缓存
    private static final Map<String, ExternalServiceClient> clientCache = new ConcurrentHashMap<>();

    // 默认配置
    private static final ExternalServiceConfig DEFAULT_CONFIG = createDefaultConfig();

    /**
     * 获取外部服务客户端
     */
    public static ExternalServiceClient getExternalServiceClient(String resourceId) {
        return clientCache.computeIfAbsent(resourceId, key -> {
            ServiceEndpoint endpoint = resolveServiceEndpoint(resourceId);
            ExternalServiceConfig config = loadServiceConfig(resourceId);
            return new ExternalServiceClient(endpoint, config);
        });
    }

    /**
     * 解析服务端点
     */
    private static ServiceEndpoint resolveServiceEndpoint(String resourceId) {
        ServiceEndpoint endpoint = new ServiceEndpoint();

        // 根据resourceId解析服务地址
        // 这里可以从配置中心、服务发现、或直接映射获取
        if (resourceId.startsWith("http://") || resourceId.startsWith("https://")) {
            endpoint.setBaseUrl(resourceId);
        } else {
            // 从服务发现或配置获取
            String baseUrl = ServiceDiscovery.lookup(resourceId);
            if (baseUrl == null) {
                throw new IllegalArgumentException("Cannot resolve service endpoint for: " + resourceId);
            }
            endpoint.setBaseUrl(baseUrl);
        }

        endpoint.setResourceId(resourceId);
        endpoint.setServiceGroup("default");

        // 加载认证信息（可以从安全配置中获取）
        loadAuthenticationInfo(endpoint, resourceId);

        return endpoint;
    }

    /**
     * 加载服务配置
     */
    private static ExternalServiceConfig loadServiceConfig(String resourceId) {
        // 这里可以从配置中心获取特定资源的配置
        // 如果没有特定配置，返回默认配置

        ExternalServiceConfig config = new ExternalServiceConfig();

        // 可以根据resourceId设置不同的超时时间
        if (resourceId.contains("critical")) {
            config.setRequestTimeout(30000);
            config.setConnectTimeout(10000);
        } else if (resourceId.contains("slow")) {
            config.setRequestTimeout(60000);
        }

        // 设置认证类型
        if (resourceId.contains("secure")) {
            config.setAuthType(ExternalServiceConfig.AuthType.BEARER);
        }

        return config;
    }

    /**
     * 加载认证信息
     */
    private static void loadAuthenticationInfo(ServiceEndpoint endpoint, String resourceId) {
        // 从安全配置或密钥管理服务获取认证信息
        // 这里简化实现

        if (resourceId.contains("secure-api")) {
            endpoint.setAccessToken("your-access-token");
        } else if (resourceId.contains("basic-auth")) {
            endpoint.setUsername("username");
            endpoint.setPassword("password");
        } else if (resourceId.contains("api-key")) {
            endpoint.setApiKey("your-api-key");
        }
    }

    /**
     * 创建默认配置
     */
    private static ExternalServiceConfig createDefaultConfig() {
        ExternalServiceConfig config = new ExternalServiceConfig();
        config.setConnectTimeout(5000);
        config.setRequestTimeout(10000);
        config.setSlowRequestThreshold(3000);
        config.setFollowRedirects(false);
        config.setEnableMetrics(true);
        config.setUsePostForOperation(true);
        config.setAuthType(ExternalServiceConfig.AuthType.NONE);
        return config;
    }

    /**
     * 清除客户端缓存
     */
    public static void clearCache() {
        clientCache.values().forEach(ExternalServiceClient::close);
        clientCache.clear();
    }

    /**
     * 移除特定客户端
     */
    public static void removeClient(String resourceId) {
        ExternalServiceClient client = clientCache.remove(resourceId);
        if (client != null) {
            client.close();
        }
    }
}

/**
 * 服务发现工具（简化实现）
 */
class ServiceDiscovery {
    private static final Map<String, String> SERVICE_MAPPING = Map.of(
            "user-service", "https://api.user-service.com",
            "order-service", "https://api.order-service.com",
            "inventory-service", "https://api.inventory-service.com",
            "payment-service", "https://api.payment-service.com"
    );

    public static String lookup(String serviceName) {
        return SERVICE_MAPPING.get(serviceName);
    }
}