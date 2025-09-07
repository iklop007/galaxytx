package com.galaxytx.core.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 通信结果封装类
 * 用于统一表示各种通信操作的结果，包括成功、失败、超时等状态
 *
 * @author 刘志成
 * @date 2023-09-05
 */
public class CommunicationResult {
    private final Status status;
    private final String errorMessage;
    private final Throwable cause;
    private final Map<String, Object> metadata;
    private final long timestamp;
    private final long durationMs;
    private final String operation;
    private final String target;

    public Object getError() {
        return errorMessage;
    }

    /**
     * 通信状态枚举
     */
    public enum Status {
        SUCCESS,           // 操作成功
        FAILURE,           // 操作失败（业务逻辑错误）
        TIMEOUT,           // 操作超时
        NETWORK_ERROR,     // 网络错误
        PROTOCOL_ERROR,    // 协议错误
        AUTH_ERROR,        // 认证错误
        RESOURCE_ERROR,    // 资源错误（如服务不可用）
        RETRYABLE_ERROR,   // 可重试错误
        NON_RETRYABLE_ERROR, // 不可重试错误
        UNKNOWN_ERROR      // 未知错误
    }

    // 私有构造函数，使用Builder模式
    private CommunicationResult(Builder builder) {
        this.status = builder.status;
        this.errorMessage = builder.errorMessage;
        this.cause = builder.cause;
        this.metadata = builder.metadata;
        this.timestamp = builder.timestamp;
        this.durationMs = builder.durationMs;
        this.operation = builder.operation;
        this.target = builder.target;
    }

    /**
     * 创建成功结果
     */
    public static CommunicationResult success() {
        return new Builder(Status.SUCCESS).build();
    }

    public static CommunicationResult success(String operation, String target) {
        return new Builder(Status.SUCCESS)
                .operation(operation)
                .target(target)
                .build();
    }

    public static CommunicationResult success(String operation, String target, long durationMs) {
        return new Builder(Status.SUCCESS)
                .operation(operation)
                .target(target)
                .durationMs(durationMs)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static CommunicationResult failure(String errorMessage) {
        return new Builder(Status.FAILURE)
                .errorMessage(errorMessage)
                .build();
    }

    public static CommunicationResult failure(String errorMessage, String operation, String target) {
        return new Builder(Status.FAILURE)
                .errorMessage(errorMessage)
                .operation(operation)
                .target(target)
                .build();
    }

    public static CommunicationResult failure(String errorMessage, Throwable cause) {
        return new Builder(Status.FAILURE)
                .errorMessage(errorMessage)
                .cause(cause)
                .build();
    }

    /**
     * 创建超时结果
     */
    public static CommunicationResult timeout(String errorMessage) {
        return new Builder(Status.TIMEOUT)
                .errorMessage(errorMessage)
                .build();
    }

    public static CommunicationResult timeout(String errorMessage, long timeoutMs) {
        return new Builder(Status.TIMEOUT)
                .errorMessage(errorMessage + " (timeout: " + timeoutMs + "ms)")
                .metadata("timeoutMs", timeoutMs)
                .build();
    }

    /**
     * 创建网络错误结果
     */
    public static CommunicationResult networkError(String errorMessage) {
        return new Builder(Status.NETWORK_ERROR)
                .errorMessage(errorMessage)
                .build();
    }

    public static CommunicationResult networkError(String errorMessage, Throwable cause) {
        return new Builder(Status.NETWORK_ERROR)
                .errorMessage(errorMessage)
                .cause(cause)
                .build();
    }

    /**
     * 创建协议错误结果
     */
    public static CommunicationResult protocolError(String errorMessage) {
        return new Builder(Status.PROTOCOL_ERROR)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 创建认证错误结果
     */
    public static CommunicationResult authError(String errorMessage) {
        return new Builder(Status.AUTH_ERROR)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 创建资源错误结果
     */
    public static CommunicationResult resourceError(String errorMessage) {
        return new Builder(Status.RESOURCE_ERROR)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 创建可重试错误结果
     */
    public static CommunicationResult retryableError(String errorMessage) {
        return new Builder(Status.RETRYABLE_ERROR)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 创建不可重试错误结果
     */
    public static CommunicationResult nonRetryableError(String errorMessage) {
        return new Builder(Status.NON_RETRYABLE_ERROR)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 创建未知错误结果
     */
    public static CommunicationResult unknownError(String errorMessage) {
        return new Builder(Status.UNKNOWN_ERROR)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 从HTTP状态码创建结果
     */
    public static CommunicationResult fromHttpStatusCode(int statusCode, String operation, String target) {
        return fromHttpStatusCode(statusCode, null, operation, target);
    }

    public static CommunicationResult fromHttpStatusCode(int statusCode, String responseBody, String operation, String target) {
        Builder builder = new Builder()
                .operation(operation)
                .target(target)
                .metadata("httpStatusCode", statusCode);

        if (responseBody != null) {
            builder.metadata("responseBody", responseBody);
        }

        if (statusCode >= 200 && statusCode < 300) {
            return builder.status(Status.SUCCESS).build();
        } else if (statusCode == 401 || statusCode == 403) {
            return builder.status(Status.AUTH_ERROR)
                    .errorMessage("Authentication failed, status: " + statusCode)
                    .build();
        } else if (statusCode == 404) {
            return builder.status(Status.RESOURCE_ERROR)
                    .errorMessage("Resource not found, status: " + statusCode)
                    .build();
        } else if (statusCode == 408 || statusCode == 504) {
            return builder.status(Status.TIMEOUT)
                    .errorMessage("Request timeout, status: " + statusCode)
                    .build();
        } else if (statusCode >= 400 && statusCode < 500) {
            return builder.status(Status.NON_RETRYABLE_ERROR)
                    .errorMessage("Client error, status: " + statusCode)
                    .build();
        } else if (statusCode >= 500) {
            return builder.status(Status.RETRYABLE_ERROR)
                    .errorMessage("Server error, status: " + statusCode)
                    .build();
        } else {
            return builder.status(Status.UNKNOWN_ERROR)
                    .errorMessage("Unexpected status code: " + statusCode)
                    .build();
        }
    }

    /**
     * 从异常创建结果
     */
    public static CommunicationResult fromException(Exception e, String operation, String target) {
        return fromException(e, operation, target, 0);
    }

    public static CommunicationResult fromException(Exception e, String operation, String target, long durationMs) {
        Builder builder = new Builder()
                .operation(operation)
                .target(target)
                .durationMs(durationMs)
                .cause(e)
                .errorMessage(e.getMessage());

        String className = e.getClass().getSimpleName();
        builder.metadata("exceptionClass", className);

        if (e instanceof java.net.SocketTimeoutException) {
            return builder.status(Status.TIMEOUT).build();
        } else if (e instanceof java.net.ConnectException) {
            return builder.status(Status.NETWORK_ERROR).build();
        } else if (e instanceof java.io.IOException) {
            return builder.status(Status.NETWORK_ERROR).build();
        } else if (e instanceof javax.net.ssl.SSLException) {
            return builder.status(Status.PROTOCOL_ERROR).build();
        } else if (e instanceof java.lang.IllegalArgumentException) {
            return builder.status(Status.NON_RETRYABLE_ERROR).build();
        } else {
            return builder.status(Status.UNKNOWN_ERROR).build();
        }
    }

    // Getter方法
    public Status getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Throwable getCause() { return cause; }
    public Map<String, Object> getMetadata() { return metadata; }
    public long getTimestamp() { return timestamp; }
    public long getDurationMs() { return durationMs; }
    public String getOperation() { return operation; }
    public String getTarget() { return target; }

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    /**
     * 判断是否失败
     */
    public boolean isFailure() {
        return !isSuccess();
    }

    /**
     * 判断是否可重试
     */
    public boolean isRetryable() {
        return status == Status.TIMEOUT ||
                status == Status.NETWORK_ERROR ||
                status == Status.RESOURCE_ERROR ||
                status == Status.RETRYABLE_ERROR ||
                status == Status.UNKNOWN_ERROR; // 未知错误默认可重试
    }

    /**
     * 判断是否超时
     */
    public boolean isTimeout() {
        return status == Status.TIMEOUT;
    }

    /**
     * 判断是否是网络错误
     */
    public boolean isNetworkError() {
        return status == Status.NETWORK_ERROR;
    }

    /**
     * 获取元数据值
     */
    public Object getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }

    public String getMetadataAsString(String key) {
        Object value = getMetadata(key);
        return value != null ? value.toString() : null;
    }

    public Integer getMetadataAsInt(String key) {
        Object value = getMetadata(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    public Long getMetadataAsLong(String key) {
        Object value = getMetadata(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    /**
     * 获取人类可读的结果描述
     */
    public String getReadableDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("CommunicationResult{status=").append(status);

        if (operation != null) {
            sb.append(", operation=").append(operation);
        }

        if (target != null) {
            sb.append(", target=").append(target);
        }

        if (errorMessage != null) {
            sb.append(", error='").append(errorMessage).append("'");
        }

        if (durationMs > 0) {
            sb.append(", duration=").append(durationMs).append("ms");
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * 获取详细的调试信息
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Status: ").append(status).append("\n");

        if (operation != null) {
            sb.append("Operation: ").append(operation).append("\n");
        }

        if (target != null) {
            sb.append("Target: ").append(target).append("\n");
        }

        if (errorMessage != null) {
            sb.append("Error: ").append(errorMessage).append("\n");
        }

        if (cause != null) {
            sb.append("Cause: ").append(cause.getClass().getSimpleName())
                    .append(" - ").append(cause.getMessage()).append("\n");
        }

        if (durationMs > 0) {
            sb.append("Duration: ").append(durationMs).append("ms\n");
        }

        sb.append("Timestamp: ").append(timestamp).append(" (")
                .append(java.time.Instant.ofEpochMilli(timestamp)).append(")\n");

        if (metadata != null && !metadata.isEmpty()) {
            sb.append("Metadata:\n");
            metadata.forEach((key, value) ->
                    sb.append("  ").append(key).append(": ").append(value).append("\n"));
        }

        return sb.toString();
    }

    /**
     * 转换为Map格式（用于序列化）
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("status", status.name());
        map.put("success", isSuccess());
        map.put("retryable", isRetryable());

        if (errorMessage != null) {
            map.put("errorMessage", errorMessage);
        }

        if (operation != null) {
            map.put("operation", operation);
        }

        if (target != null) {
            map.put("target", target);
        }

        map.put("timestamp", timestamp);
        map.put("durationMs", durationMs);

        if (metadata != null) {
            map.put("metadata", new HashMap<>(metadata));
        }

        if (cause != null) {
            map.put("causeClass", cause.getClass().getName());
            map.put("causeMessage", cause.getMessage());
        }

        return map;
    }

    /**
     * Builder模式
     */
    public static class Builder {
        private Status status;
        private String errorMessage;
        private Throwable cause;
        private Map<String, Object> metadata = new HashMap<>();
        private long timestamp = System.currentTimeMillis();
        private long durationMs;
        private String operation;
        private String target;

        public Builder() {}

        public Builder(Status status) {
            this.status = status;
        }

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public CommunicationResult build() {
            return new CommunicationResult(this);
        }
    }

    @Override
    public String toString() {
        return getReadableDescription();
    }
}