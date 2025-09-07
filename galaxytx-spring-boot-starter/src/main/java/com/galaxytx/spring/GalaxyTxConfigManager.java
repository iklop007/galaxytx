package com.galaxytx.spring;

import org.springframework.stereotype.Component;

/**
 * GalaxyTX 配置管理器
 */
@Component
public class GalaxyTxConfigManager {
    private final GalaxyTxProperties properties;

    public GalaxyTxConfigManager(GalaxyTxProperties properties) {
        this.properties = properties;
    }

    public GalaxyTxProperties getProperties() {
        return properties;
    }

    public String getApplicationId() {
        return properties.getApplicationId();
    }

    public String getServerAddress() {
        return properties.getServer().getAddress();
    }

    public int getServerPort() {
        return properties.getServer().getPort();
    }

    public int getDefaultTimeout() {
        return properties.getTransaction().getDefaultTimeout();
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }
}