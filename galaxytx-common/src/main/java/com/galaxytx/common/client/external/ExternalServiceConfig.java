package com.galaxytx.common.client.external;

import javax.net.ssl.SSLContext;
import java.net.Authenticator;
import java.net.ProxySelector;
import java.util.Map;

/**
 * 外部服务配置
 */
public class ExternalServiceConfig {
    private int connectTimeout = 5000;
    private int requestTimeout = 10000;
    private int slowRequestThreshold = 3000;
    private boolean followRedirects = false;
    private boolean enableMetrics = true;
    private boolean usePostForOperation = true;

    private AuthType authType;
    private SSLContext sslContext;
    private ProxySelector proxy;
    private Authenticator authenticator;

    private Map<String, String> customHeaders;
    private Map<String, Object> customParameters;

    private String confirmPath = "/transaction/confirm";
    private String cancelPath = "/transaction/cancel";
    private String healthPath = "/health";

    public enum AuthType {
        NONE, BASIC, BEARER, API_KEY
    }

    // Getter和Setter方法
    public int getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }

    public int getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(int requestTimeout) { this.requestTimeout = requestTimeout; }

    public int getSlowRequestThreshold() { return slowRequestThreshold; }
    public void setSlowRequestThreshold(int slowRequestThreshold) { this.slowRequestThreshold = slowRequestThreshold; }

    public boolean isFollowRedirects() { return followRedirects; }
    public void setFollowRedirects(boolean followRedirects) { this.followRedirects = followRedirects; }

    public boolean isEnableMetrics() { return enableMetrics; }
    public void setEnableMetrics(boolean enableMetrics) { this.enableMetrics = enableMetrics; }

    public boolean isUsePostForOperation() { return usePostForOperation; }
    public void setUsePostForOperation(boolean usePostForOperation) { this.usePostForOperation = usePostForOperation; }

    public AuthType getAuthType() { return authType; }
    public void setAuthType(AuthType authType) { this.authType = authType; }

    public SSLContext getSslContext() { return sslContext; }
    public void setSslContext(SSLContext sslContext) { this.sslContext = sslContext; }

    public ProxySelector getProxy() { return proxy; }
    public void setProxy(ProxySelector proxy) { this.proxy = proxy; }

    public Authenticator getAuthenticator() { return authenticator; }
    public void setAuthenticator(Authenticator authenticator) { this.authenticator = authenticator; }

    public Map<String, String> getCustomHeaders() { return customHeaders; }
    public void setCustomHeaders(Map<String, String> customHeaders) { this.customHeaders = customHeaders; }

    public Map<String, Object> getCustomParameters() { return customParameters; }
    public void setCustomParameters(Map<String, Object> customParameters) { this.customParameters = customParameters; }

    public String getConfirmPath() { return confirmPath; }
    public void setConfirmPath(String confirmPath) { this.confirmPath = confirmPath; }

    public String getCancelPath() { return cancelPath; }
    public void setCancelPath(String cancelPath) { this.cancelPath = cancelPath; }

    public String getHealthPath() { return healthPath; }
    public void setHealthPath(String healthPath) { this.healthPath = healthPath; }

    /**
     * 获取操作路径
     */
    public String getOperationPath(String operation) {
        switch (operation) {
            case "confirm": return confirmPath;
            case "cancel": return cancelPath;
            case "health": return healthPath;
            default: return "/transaction/" + operation;
        }
    }
}
