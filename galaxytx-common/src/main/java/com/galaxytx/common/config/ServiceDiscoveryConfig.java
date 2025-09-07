package com.galaxytx.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务发现配置
 */
@Component
@ConfigurationProperties(prefix = "galaxytx.service-discovery")
public class ServiceDiscoveryConfig {
    private boolean enabled = false;
    private String discoveryType = "static"; // static, consul, eureka, nacos, kubernetes, dns
    private String defaultProtocol = "http";
    private String defaultDomain = "localhost";
    private int defaultPort = 8080;
    private long cacheDuration = 300000; // 5分钟
    private int retryAttempts = 3;
    private long retryInterval = 1000;

    private Map<String, String> serviceMappings = new HashMap<>();

    // Getter和Setter方法
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getDiscoveryType() { return discoveryType; }
    public void setDiscoveryType(String discoveryType) { this.discoveryType = discoveryType; }

    public String getDefaultProtocol() { return defaultProtocol; }
    public void setDefaultProtocol(String defaultProtocol) { this.defaultProtocol = defaultProtocol; }

    public String getDefaultDomain() { return defaultDomain; }
    public void setDefaultDomain(String defaultDomain) { this.defaultDomain = defaultDomain; }

    public int getDefaultPort() { return defaultPort; }
    public void setDefaultPort(int defaultPort) { this.defaultPort = defaultPort; }

    public long getCacheDuration() { return cacheDuration; }
    public void setCacheDuration(long cacheDuration) { this.cacheDuration = cacheDuration; }

    public int getRetryAttempts() { return retryAttempts; }
    public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }

    public long getRetryInterval() { return retryInterval; }
    public void setRetryInterval(long retryInterval) { this.retryInterval = retryInterval; }

    public Map<String, String> getServiceMappings() { return serviceMappings; }
    public void setServiceMappings(Map<String, String> serviceMappings) { this.serviceMappings = serviceMappings; }

    public boolean isServiceDiscoveryEnabled() {
        return enabled && discoveryType != null && !discoveryType.isEmpty();
    }
}