package com.galaxytx.core.client.external;

import com.galaxytx.core.exception.NetworkException;
import com.galaxytx.core.exception.NetworkExceptionUtils;
import com.galaxytx.core.model.CommunicationResult;
import com.galaxytx.core.model.ServiceEndpoint;
import com.galaxytx.core.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 外部服务客户端
 * 用于与外部RESTful服务进行通信，支持事务的确认和取消操作
 */
public class ExternalServiceClient {
    private static final Logger logger = LoggerFactory.getLogger(ExternalServiceClient.class);

    private final ServiceEndpoint endpoint;
    private final HttpClient httpClient;
    private final ExternalServiceConfig config;

    // 请求头常量
    private static final String HEADER_XID = "X-Transaction-ID";
    private static final String HEADER_BRANCH_ID = "X-Branch-ID";
    private static final String HEADER_SERVICE_GROUP = "X-Service-Group";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TYPE_JSON = "application/json";

    public ExternalServiceClient(ServiceEndpoint endpoint, ExternalServiceConfig config) {
        this.endpoint = endpoint;
        this.config = config;
        this.httpClient = createHttpClient(config);
    }

    /**
     * 确认事务（Confirm操作）
     */
    public CommunicationResult confirmTransaction(String xid, long branchId) {
        return executeTransactionOperation(xid, branchId, "confirm");
    }

    /**
     * 取消事务（Cancel操作）
     */
    public CommunicationResult cancelTransaction(String xid, long branchId) {
        return executeTransactionOperation(xid, branchId, "cancel");
    }

    /**
     * 执行事务操作（Confirm/Cancel）
     */
    private CommunicationResult executeTransactionOperation(String xid, long branchId, String operation) {
        String url = buildOperationUrl(operation);
        String requestBody = buildRequestBody(xid, branchId, operation);

        try {
            HttpRequest request = buildHttpRequest(url, requestBody);
            HttpResponse<String> response = sendRequest(request);

            return processResponse(response, operation);

        } catch (IOException e) {
            return handleIOException(e, url, operation);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CommunicationResult.failure("Operation interrupted: " + operation);
        } catch (Exception e) {
            return handleUnexpectedException(e, url, operation);
        }
    }

    /**
     * 构建操作URL
     */
    private String buildOperationUrl(String operation) {
        String baseUrl = endpoint.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String path = config.getOperationPath(operation);
        if (path.startsWith("/")) {
            return baseUrl + path;
        } else {
            return baseUrl + "/" + path;
        }
    }

    /**
     * 构建请求体
     */
    private String buildRequestBody(String xid, long branchId, String operation) {
        TransactionRequest request = new TransactionRequest();
        request.setXid(xid);
        request.setBranchId(branchId);
        request.setOperation(operation);
        request.setTimestamp(System.currentTimeMillis());
        request.setServiceGroup(endpoint.getServiceGroup());

        // 添加自定义参数
        if (config.getCustomParameters() != null) {
            request.setParameters(config.getCustomParameters());
        }

        return JsonUtils.toJson(request);
    }

    /**
     * 构建HTTP请求
     */
    private HttpRequest buildHttpRequest(String url, String requestBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .header(HEADER_XID, endpoint.getServiceGroup())
                .timeout(Duration.ofMillis(config.getRequestTimeout()));

        // 添加认证头
        addAuthenticationHeaders(builder);

        // 添加自定义头
        addCustomHeaders(builder);

        // 设置请求方法和体
        if (config.isUsePostForOperation()) {
            builder.POST(HttpRequest.BodyPublishers.ofString(requestBody));
        } else {
            builder.PUT(HttpRequest.BodyPublishers.ofString(requestBody));
        }

        return builder.build();
    }

    /**
     * 添加认证头
     */
    private void addAuthenticationHeaders(HttpRequest.Builder builder) {
        if (config.getAuthType() != null) {
            switch (config.getAuthType()) {
                case BASIC:
                    String credentials = endpoint.getUsername() + ":" + endpoint.getPassword();
                    String encoded = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
                    builder.header("Authorization", "Basic " + encoded);
                    break;
                case BEARER:
                    if (endpoint.getAccessToken() != null) {
                        builder.header("Authorization", "Bearer " + endpoint.getAccessToken());
                    }
                    break;
                case API_KEY:
                    if (endpoint.getApiKey() != null) {
                        builder.header("X-API-Key", endpoint.getApiKey());
                    }
                    break;
            }
        }
    }

    /**
     * 添加自定义头
     */
    private void addCustomHeaders(HttpRequest.Builder builder) {
        if (config.getCustomHeaders() != null) {
            config.getCustomHeaders().forEach(builder::header);
        }
    }

    /**
     * 发送请求
     */
    private HttpResponse<String> sendRequest(HttpRequest request) throws IOException, InterruptedException {
        if (config.isEnableMetrics()) {
            long startTime = System.nanoTime();
            try {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } finally {
                long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
                recordMetrics(request.uri().toString(), duration);
            }
        } else {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    /**
     * 处理响应
     */
    private CommunicationResult processResponse(HttpResponse<String> response, String operation) {
        int statusCode = response.statusCode();
        String responseBody = response.body();

        logger.debug("{} operation response: status={}, body={}", operation, statusCode, responseBody);

        if (statusCode >= 200 && statusCode < 300) {
            // 成功响应
            return CommunicationResult.success();
        } else if (statusCode >= 400 && statusCode < 500) {
            // 客户端错误，通常不需要重试
            return handleClientError(statusCode, responseBody, operation);
        } else if (statusCode >= 500) {
            // 服务端错误，可以重试
            return handleServerError(statusCode, responseBody, operation);
        } else {
            // 其他状态码
            return CommunicationResult.failure("Unexpected status code: " + statusCode);
        }
    }

    /**
     * 处理客户端错误
     */
    private CommunicationResult handleClientError(int statusCode, String responseBody, String operation) {
        switch (statusCode) {
            case 400:
                return CommunicationResult.failure("Bad request for " + operation);
            case 401:
                return CommunicationResult.failure("Unauthorized for " + operation);
            case 403:
                return CommunicationResult.failure("Forbidden for " + operation);
            case 404:
                return CommunicationResult.failure("Service not found for " + operation);
            case 409:
                // 冲突错误，可能表示重复操作
                return CommunicationResult.failure("Conflict in " + operation);
            default:
                return CommunicationResult.failure("Client error: " + statusCode + " for " + operation);
        }
    }

    /**
     * 处理服务端错误
     */
    private CommunicationResult handleServerError(int statusCode, String responseBody, String operation) {
        switch (statusCode) {
            case 500:
                return CommunicationResult.failure("Internal server error during " + operation);
            case 502:
                return CommunicationResult.networkError("Bad gateway during " + operation);
            case 503:
                return CommunicationResult.failure("Service unavailable for " + operation);
            case 504:
                return CommunicationResult.timeout("Gateway timeout during " + operation);
            default:
                return CommunicationResult.failure("Server error: " + statusCode + " for " + operation);
        }
    }

    /**
     * 处理IO异常
     */
    private CommunicationResult handleIOException(IOException e, String url, String operation) {
        NetworkException networkException = NetworkExceptionUtils.convertToNetworkException(
                e, url, config.getRequestTimeout()
        );

        logger.warn("Network error during {} operation to {}: {}",
                operation, url, networkException.getReadableDescription());

        if (networkException.isConnectionTimeout() || networkException.isReadTimeout()) {
            return CommunicationResult.timeout(networkException.getMessage());
        } else {
            return CommunicationResult.networkError(networkException.getMessage());
        }
    }

    /**
     * 处理未知异常
     */
    private CommunicationResult handleUnexpectedException(Exception e, String url, String operation) {
        logger.error("Unexpected error during {} operation to {}: {}",
                operation, url, e.getMessage(), e);
        return CommunicationResult.failure("Unexpected error: " + e.getMessage());
    }

    /**
     * 创建HTTP客户端
     */
    private HttpClient createHttpClient(ExternalServiceConfig config) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getConnectTimeout()))
                .followRedirects(config.isFollowRedirects() ?
                        HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER);

        // 配置SSL
        if (config.getSslContext() != null) {
            builder.sslContext(config.getSslContext());
        }

        // 配置代理
        if (config.getProxy() != null) {
            builder.proxy(config.getProxy());
        }

        // 配置认证
        if (config.getAuthenticator() != null) {
            builder.authenticator(config.getAuthenticator());
        }

        return builder.build();
    }

    /**
     * 记录指标数据
     */
    private void recordMetrics(String url, long durationMs) {
        // 这里可以集成到监控系统
        logger.debug("Request to {} took {}ms", url, durationMs);

        if (durationMs > config.getSlowRequestThreshold()) {
            logger.warn("Slow request detected: {}ms to {}", durationMs, url);
        }
    }

    /**
     * 关闭客户端
     */
    public void close() {
        // HTTPClient不需要显式关闭，但可以清理资源
        logger.info("ExternalServiceClient closed for: {}", endpoint.getBaseUrl());
    }

    /**
     * 获取服务端点信息
     */
    public ServiceEndpoint getEndpoint() {
        return endpoint;
    }

    /**
     * 获取配置信息
     */
    public ExternalServiceConfig getConfig() {
        return config;
    }

    /**
     * 健康检查
     */
    public boolean healthCheck() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildOperationUrl("health")))
                    .GET()
                    .timeout(Duration.ofMillis(5000))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            logger.warn("Health check failed for {}: {}", endpoint.getBaseUrl(), e.getMessage());
            return false;
        }
    }
}