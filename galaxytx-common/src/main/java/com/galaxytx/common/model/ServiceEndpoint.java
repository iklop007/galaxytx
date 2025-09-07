package com.galaxytx.common.model;

/**
 * 服务端点信息
 */
public class ServiceEndpoint {
    private String baseUrl;
    private String serviceGroup;
    private String username;
    private String password;
    private String accessToken;
    private String apiKey;
    private String resourceId;

    // Getter和Setter方法
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getServiceGroup() { return serviceGroup; }
    public void setServiceGroup(String serviceGroup) { this.serviceGroup = serviceGroup; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
}
