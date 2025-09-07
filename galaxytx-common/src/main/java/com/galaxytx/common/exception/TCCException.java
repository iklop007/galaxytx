package com.galaxytx.common.exception;

/**
 * TCC操作异常
 * 用于表示TCC模式下的各种异常情况
 */
public class TCCException extends RuntimeException {
    private final String resourceId;
    private final String operation;
    private final String phase;
    private final int errorCode;

    // 错误码定义
    public static final int ERROR_RESOURCE_NOT_FOUND = 1001;
    public static final int ERROR_METHOD_NOT_FOUND = 1002;
    public static final int ERROR_METHOD_INVOCATION = 1003;
    public static final int ERROR_INVALID_PARAMETER = 1004;
    public static final int ERROR_TIMEOUT = 1005;
    public static final int ERROR_NETWORK = 1006;
    public static final int ERROR_UNKNOWN = 9999;

    public TCCException(String message) {
        super(message);
        this.resourceId = null;
        this.operation = null;
        this.phase = null;
        this.errorCode = ERROR_UNKNOWN;
    }

    public TCCException(String message, String resourceId) {
        super(message + (resourceId != null ? " [resource: " + resourceId + "]" : ""));
        this.resourceId = resourceId;
        this.operation = null;
        this.phase = null;
        this.errorCode = ERROR_UNKNOWN;
    }

    public TCCException(String message, String resourceId, String operation) {
        super(message + (resourceId != null ? " [resource: " + resourceId + ", operation: " + operation + "]" : ""));
        this.resourceId = resourceId;
        this.operation = operation;
        this.phase = null;
        this.errorCode = ERROR_UNKNOWN;
    }

    public TCCException(String message, String resourceId, String operation, String phase) {
        super(message + (resourceId != null ? " [resource: " + resourceId + ", operation: " + operation + ", phase: " + phase + "]" : ""));
        this.resourceId = resourceId;
        this.operation = operation;
        this.phase = phase;
        this.errorCode = ERROR_UNKNOWN;
    }

    public TCCException(String message, String resourceId, String operation, String phase, int errorCode) {
        super(message + (resourceId != null ? " [resource: " + resourceId + ", operation: " + operation + ", phase: " + phase + ", code: " + errorCode + "]" : ""));
        this.resourceId = resourceId;
        this.operation = operation;
        this.phase = phase;
        this.errorCode = errorCode;
    }

    public TCCException(String message, Throwable cause) {
        super(message, cause);
        this.resourceId = null;
        this.operation = null;
        this.phase = null;
        this.errorCode = ERROR_UNKNOWN;
    }

    public TCCException(String message, String resourceId, Throwable cause) {
        super(message + (resourceId != null ? " [resource: " + resourceId + "]" : ""), cause);
        this.resourceId = resourceId;
        this.operation = null;
        this.phase = null;
        this.errorCode = ERROR_UNKNOWN;
    }

    public TCCException(String message, String resourceId, String operation, Throwable cause) {
        super(message + (resourceId != null ? " [resource: " + resourceId + ", operation: " + operation + "]" : ""), cause);
        this.resourceId = resourceId;
        this.operation = operation;
        this.phase = null;
        this.errorCode = ERROR_UNKNOWN;
    }

    public TCCException(String message, String resourceId, String operation, String phase, Throwable cause) {
        super(message + (resourceId != null ? " [resource: " + resourceId + ", operation: " + operation + ", phase: " + phase + "]" : ""), cause);
        this.resourceId = resourceId;
        this.operation = operation;
        this.phase = phase;
        this.errorCode = ERROR_UNKNOWN;
    }

    public TCCException(String message, String resourceId, String operation, String phase, int errorCode, Throwable cause) {
        super(message + (resourceId != null ? " [resource: " + resourceId + ", operation: " + operation + ", phase: " + phase + ", code: " + errorCode + "]" : ""), cause);
        this.resourceId = resourceId;
        this.operation = operation;
        this.phase = phase;
        this.errorCode = errorCode;
    }

    // Getter方法
    public String getResourceId() {
        return resourceId;
    }

    public String getOperation() {
        return operation;
    }

    public String getPhase() {
        return phase;
    }

    public int getErrorCode() {
        return errorCode;
    }

    /**
     * 判断是否是可重试的异常
     */
    public boolean isRetryable() {
        return errorCode == ERROR_TIMEOUT ||
                errorCode == ERROR_NETWORK ||
                errorCode == ERROR_UNKNOWN;
    }

    /**
     * 判断是否是资源未找到异常
     */
    public boolean isResourceNotFound() {
        return errorCode == ERROR_RESOURCE_NOT_FOUND;
    }

    /**
     * 判断是否是方法未找到异常
     */
    public boolean isMethodNotFound() {
        return errorCode == ERROR_METHOD_NOT_FOUND;
    }

    /**
     * 获取异常的人类可读描述
     */
    public String getReadableDescription() {
        StringBuilder sb = new StringBuilder("TCCException");
        if (resourceId != null) sb.append(" [resource: ").append(resourceId).append("]");
        if (operation != null) sb.append(" [operation: ").append(operation).append("]");
        if (phase != null) sb.append(" [phase: ").append(phase).append("]");
        if (errorCode != ERROR_UNKNOWN) sb.append(" [code: ").append(errorCode).append("]");
        sb.append(": ").append(getMessage());
        return sb.toString();
    }

    /**
     * 创建资源未找到异常
     */
    public static TCCException resourceNotFound(String resourceId) {
        return new TCCException("TCC resource not found", resourceId, null, null, ERROR_RESOURCE_NOT_FOUND);
    }

    /**
     * 创建方法未找到异常
     */
    public static TCCException methodNotFound(String resourceId, String methodName) {
        return new TCCException("TCC method not found: " + methodName, resourceId, methodName, null, ERROR_METHOD_NOT_FOUND);
    }

    /**
     * 创建方法调用异常
     */
    public static TCCException methodInvocationFailed(String resourceId, String methodName, Throwable cause) {
        return new TCCException("TCC method invocation failed: " + methodName, resourceId, methodName, null, ERROR_METHOD_INVOCATION, cause);
    }

    /**
     * 创建超时异常
     */
    public static TCCException timeout(String resourceId, String operation, long timeoutMs) {
        return new TCCException("TCC operation timeout after " + timeoutMs + "ms", resourceId, operation, null, ERROR_TIMEOUT);
    }

    /**
     * 创建网络异常
     */
    public static TCCException networkError(String resourceId, String operation, Throwable cause) {
        return new TCCException("TCC network error", resourceId, operation, null, ERROR_NETWORK, cause);
    }
}