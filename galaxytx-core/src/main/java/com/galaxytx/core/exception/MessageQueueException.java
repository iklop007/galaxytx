package com.galaxytx.core.exception;

// import javax.jms.JMSException;

/**
 * 消息队列异常
 * 用于表示消息队列操作中的各种异常情况
 */
public class MessageQueueException extends RuntimeException {
    private final String resourceId;
    private final String queueName;
    private final String operation;
    private final int errorCode;
    private final String originalErrorCode;

    // 错误码定义
    public static final int ERROR_CONNECTION_FAILED = 1001;
    public static final int ERROR_CONNECTION_TIMEOUT = 1002;
    public static final int ERROR_AUTHENTICATION_FAILED = 1003;
    public static final int ERROR_QUEUE_NOT_FOUND = 1004;
    public static final int ERROR_MESSAGE_SEND_FAILED = 1005;
    public static final int ERROR_MESSAGE_RECEIVE_FAILED = 1006;
    public static final int ERROR_MESSAGE_ACK_FAILED = 1007;
    public static final int ERROR_TRANSACTION_FAILED = 1008;
    public static final int ERROR_SESSION_INVALID = 1009;
    public static final int ERROR_CONSUMER_INVALID = 1010;
    public static final int ERROR_PRODUCER_INVALID = 1011;
    public static final int ERROR_MESSAGE_FORMAT = 1012;
    public static final int ERROR_MESSAGE_SIZE_EXCEEDED = 1013;
    public static final int ERROR_QUEUE_FULL = 1014;
    public static final int ERROR_ACCESS_DENIED = 1015;
    public static final int ERROR_NETWORK = 1016;
    public static final int ERROR_PROTOCOL = 1017;
    public static final int ERROR_SERIALIZATION = 1018;
    public static final int ERROR_DESERIALIZATION = 1019;
    public static final int ERROR_UNKNOWN = 9999;

    public MessageQueueException(String message) {
        super(message);
        this.resourceId = null;
        this.queueName = null;
        this.operation = null;
        this.errorCode = ERROR_UNKNOWN;
        this.originalErrorCode = null;
    }

    public MessageQueueException(String message, String resourceId) {
        super(message + (resourceId != null ? " [resource: " + resourceId + "]" : ""));
        this.resourceId = resourceId;
        this.queueName = null;
        this.operation = null;
        this.errorCode = ERROR_UNKNOWN;
        this.originalErrorCode = null;
    }

    public MessageQueueException(String message, String resourceId, String queueName) {
        super(message + (resourceId != null ? " [resource: " + resourceId + ", queue: " + queueName + "]" : ""));
        this.resourceId = resourceId;
        this.queueName = queueName;
        this.operation = null;
        this.errorCode = ERROR_UNKNOWN;
        this.originalErrorCode = null;
    }

    public MessageQueueException(String message, String resourceId, String queueName, String operation) {
        super(message + (resourceId != null ? " [resource: " + resourceId + ", queue: " + queueName + ", operation: " + operation + "]" : ""));
        this.resourceId = resourceId;
        this.queueName = queueName;
        this.operation = operation;
        this.errorCode = ERROR_UNKNOWN;
        this.originalErrorCode = null;
    }

    public MessageQueueException(String message, String resourceId, String queueName, String operation, int errorCode) {
        super(message + (resourceId != null ? " [resource: " + resourceId + ", queue: " + queueName + ", operation: " + operation + ", code: " + errorCode + "]" : ""));
        this.resourceId = resourceId;
        this.queueName = queueName;
        this.operation = operation;
        this.errorCode = errorCode;
        this.originalErrorCode = null;
    }

    public MessageQueueException(String message, String resourceId, String queueName, String operation, int errorCode, String originalErrorCode) {
        super(message + (resourceId != null ? " [resource: " + resourceId + ", queue: " + queueName + ", operation: " + operation + ", code: " + errorCode + ", original: " + originalErrorCode + "]" : ""));
        this.resourceId = resourceId;
        this.queueName = queueName;
        this.operation = operation;
        this.errorCode = errorCode;
        this.originalErrorCode = originalErrorCode;
    }

    public MessageQueueException(String message, Throwable cause) {
        super(message, cause);
        this.resourceId = null;
        this.queueName = null;
        this.operation = null;
        this.errorCode = ERROR_UNKNOWN;
        this.originalErrorCode = null;
    }

    public MessageQueueException(String message, String resourceId, Throwable cause) {
        super(message + (resourceId != null ? " [resource: " + resourceId + "]" : ""), cause);
        this.resourceId = resourceId;
        this.queueName = null;
        this.operation = null;
        this.errorCode = ERROR_UNKNOWN;
        this.originalErrorCode = null;
    }

    public MessageQueueException(String message, String resourceId, String queueName, Throwable cause) {
        super(message + (resourceId != null ? " [resource: " + resourceId + ", queue: " + queueName + "]" : ""), cause);
        this.resourceId = resourceId;
        this.queueName = queueName;
        this.operation = null;
        this.errorCode = ERROR_UNKNOWN;
        this.originalErrorCode = null;
    }

    public MessageQueueException(String message, String resourceId, String queueName, String operation, Throwable cause) {
        super(message + (resourceId != null ? " [resource: " + resourceId + ", queue: " + queueName + ", operation: " + operation + "]" : ""), cause);
        this.resourceId = resourceId;
        this.queueName = queueName;
        this.operation = operation;
        this.errorCode = ERROR_UNKNOWN;
        this.originalErrorCode = null;
    }

    public MessageQueueException(String message, String resourceId, String queueName, String operation, int errorCode, Throwable cause) {
        super(message + (resourceId != null ? " [resource: " + resourceId + ", queue: " + queueName + ", operation: " + operation + ", code: " + errorCode + "]" : ""), cause);
        this.resourceId = resourceId;
        this.queueName = queueName;
        this.operation = operation;
        this.errorCode = errorCode;
        this.originalErrorCode = null;
    }

    public MessageQueueException(String message, String resourceId, String queueName, String operation, int errorCode, String originalErrorCode, Throwable cause) {
        super(message + (resourceId != null ? " [resource: " + resourceId + ", queue: " + queueName + ", operation: " + operation + ", code: " + errorCode + ", original: " + originalErrorCode + "]" : ""), cause);
        this.resourceId = resourceId;
        this.queueName = queueName;
        this.operation = operation;
        this.errorCode = errorCode;
        this.originalErrorCode = originalErrorCode;
    }

    // Getter方法
    public String getResourceId() {
        return resourceId;
    }

    public String getQueueName() {
        return queueName;
    }

    public String getOperation() {
        return operation;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getOriginalErrorCode() {
        return originalErrorCode;
    }

    /**
     * 判断是否是可重试的异常
     */
    public boolean isRetryable() {
        return errorCode == ERROR_CONNECTION_TIMEOUT ||
                errorCode == ERROR_NETWORK ||
                errorCode == ERROR_QUEUE_FULL ||
                errorCode == ERROR_UNKNOWN;
    }

    /**
     * 判断是否是连接相关异常
     */
    public boolean isConnectionError() {
        return errorCode == ERROR_CONNECTION_FAILED ||
                errorCode == ERROR_CONNECTION_TIMEOUT ||
                errorCode == ERROR_NETWORK;
    }

    /**
     * 判断是否是认证相关异常
     */
    public boolean isAuthenticationError() {
        return errorCode == ERROR_AUTHENTICATION_FAILED ||
                errorCode == ERROR_ACCESS_DENIED;
    }

    /**
     * 判断是否是消息相关异常
     */
    public boolean isMessageError() {
        return errorCode == ERROR_MESSAGE_SEND_FAILED ||
                errorCode == ERROR_MESSAGE_RECEIVE_FAILED ||
                errorCode == ERROR_MESSAGE_FORMAT ||
                errorCode == ERROR_MESSAGE_SIZE_EXCEEDED ||
                errorCode == ERROR_SERIALIZATION ||
                errorCode == ERROR_DESERIALIZATION;
    }

    /**
     * 判断是否是资源未找到异常
     */
    public boolean isResourceNotFound() {
        return errorCode == ERROR_QUEUE_NOT_FOUND;
    }

    /**
     * 获取异常的人类可读描述
     */
    public String getReadableDescription() {
        StringBuilder sb = new StringBuilder("MessageQueueException");
        if (resourceId != null) sb.append(" [resource: ").append(resourceId).append("]");
        if (queueName != null) sb.append(" [queue: ").append(queueName).append("]");
        if (operation != null) sb.append(" [operation: ").append(operation).append("]");
        if (errorCode != ERROR_UNKNOWN) sb.append(" [code: ").append(errorCode).append("]");
        if (originalErrorCode != null) sb.append(" [original: ").append(originalErrorCode).append("]");
        sb.append(": ").append(getMessage());
        return sb.toString();
    }

    /**
     * 从JMSException创建MessageQueueException
     */
    /*public static MessageQueueException fromJMSException(JMSException jmsException, String resourceId, String queueName, String operation) {
        String errorCode = null;
        try {
            errorCode = jmsException.getErrorCode();
        } catch (Exception ignored) {
        }

        String message = jmsException.getMessage();
        int customErrorCode = mapJmsErrorCodeToCustomCode(errorCode, message);

        return new MessageQueueException(
                "JMS operation failed: " + message,
                resourceId,
                queueName,
                operation,
                customErrorCode,
                errorCode,
                jmsException
        );
    }*/

    /**
     * 将JMS错误码映射到自定义错误码
     */
    private static int mapJmsErrorCodeToCustomCode(String jmsErrorCode, String message) {
        if (jmsErrorCode == null) {
            jmsErrorCode = "";
        }
        if (message == null) {
            message = "";
        }

        String lowerMessage = message.toLowerCase();
        String lowerErrorCode = jmsErrorCode.toLowerCase();

        if (lowerMessage.contains("connection") || lowerMessage.contains("connect")) {
            return ERROR_CONNECTION_FAILED;
        } else if (lowerMessage.contains("timeout") || lowerMessage.contains("timed out")) {
            return ERROR_CONNECTION_TIMEOUT;
        } else if (lowerMessage.contains("auth") || lowerMessage.contains("login") || lowerMessage.contains("password")) {
            return ERROR_AUTHENTICATION_FAILED;
        } else if (lowerMessage.contains("not found") || lowerMessage.contains("no such")) {
            return ERROR_QUEUE_NOT_FOUND;
        } else if (lowerMessage.contains("send") || lowerMessage.contains("produce")) {
            return ERROR_MESSAGE_SEND_FAILED;
        } else if (lowerMessage.contains("receive") || lowerMessage.contains("consume")) {
            return ERROR_MESSAGE_RECEIVE_FAILED;
        } else if (lowerMessage.contains("acknowledge") || lowerMessage.contains("commit") || lowerMessage.contains("rollback")) {
            return ERROR_MESSAGE_ACK_FAILED;
        } else if (lowerMessage.contains("transaction")) {
            return ERROR_TRANSACTION_FAILED;
        } else if (lowerMessage.contains("session")) {
            return ERROR_SESSION_INVALID;
        } else if (lowerMessage.contains("consumer")) {
            return ERROR_CONSUMER_INVALID;
        } else if (lowerMessage.contains("producer")) {
            return ERROR_PRODUCER_INVALID;
        } else if (lowerMessage.contains("format") || lowerMessage.contains("invalid")) {
            return ERROR_MESSAGE_FORMAT;
        } else if (lowerMessage.contains("size") || lowerMessage.contains("too large")) {
            return ERROR_MESSAGE_SIZE_EXCEEDED;
        } else if (lowerMessage.contains("full") || lowerMessage.contains("limit")) {
            return ERROR_QUEUE_FULL;
        } else if (lowerMessage.contains("access") || lowerMessage.contains("denied") || lowerMessage.contains("permission")) {
            return ERROR_ACCESS_DENIED;
        } else if (lowerMessage.contains("network")) {
            return ERROR_NETWORK;
        } else if (lowerMessage.contains("protocol")) {
            return ERROR_PROTOCOL;
        }

        return ERROR_UNKNOWN;
    }

    /**
     * 创建连接失败异常
     */
    public static MessageQueueException connectionFailed(String resourceId, String brokerUrl, Throwable cause) {
        return new MessageQueueException(
                "Failed to connect to message queue: " + brokerUrl,
                resourceId,
                null,
                "connect",
                ERROR_CONNECTION_FAILED,
                cause
        );
    }

    /**
     * 创建连接超时异常
     */
    public static MessageQueueException connectionTimeout(String resourceId, String brokerUrl, long timeoutMs) {
        return new MessageQueueException(
                "Connection timeout after " + timeoutMs + "ms: " + brokerUrl,
                resourceId,
                null,
                "connect",
                ERROR_CONNECTION_TIMEOUT
        );
    }

    /**
     * 创建认证失败异常
     */
    public static MessageQueueException authenticationFailed(String resourceId, String username, Throwable cause) {
        return new MessageQueueException(
                "Authentication failed for user: " + username,
                resourceId,
                null,
                "authenticate",
                ERROR_AUTHENTICATION_FAILED,
                cause
        );
    }

    /**
     * 创建队列未找到异常
     */
    public static MessageQueueException queueNotFound(String resourceId, String queueName) {
        return new MessageQueueException(
                "Queue not found: " + queueName,
                resourceId,
                queueName,
                "access",
                ERROR_QUEUE_NOT_FOUND
        );
    }

    /**
     * 创建消息发送失败异常
     */
    public static MessageQueueException messageSendFailed(String resourceId, String queueName, Throwable cause) {
        return new MessageQueueException(
                "Failed to send message to queue: " + queueName,
                resourceId,
                queueName,
                "send",
                ERROR_MESSAGE_SEND_FAILED,
                cause
        );
    }

    /**
     * 创建消息接收失败异常
     */
    public static MessageQueueException messageReceiveFailed(String resourceId, String queueName, Throwable cause) {
        return new MessageQueueException(
                "Failed to receive message from queue: " + queueName,
                resourceId,
                queueName,
                "receive",
                ERROR_MESSAGE_RECEIVE_FAILED,
                cause
        );
    }

    /**
     * 创建消息确认失败异常
     */
    public static MessageQueueException messageAckFailed(String resourceId, String queueName, String operation, Throwable cause) {
        return new MessageQueueException(
                "Failed to " + operation + " message",
                resourceId,
                queueName,
                operation,
                ERROR_MESSAGE_ACK_FAILED,
                cause
        );
    }

    /**
     * 创建事务失败异常
     */
    public static MessageQueueException transactionFailed(String resourceId, String operation, Throwable cause) {
        return new MessageQueueException(
                "Transaction " + operation + " failed",
                resourceId,
                null,
                "transaction-" + operation,
                ERROR_TRANSACTION_FAILED,
                cause
        );
    }

    /**
     * 创建序列化异常
     */
    public static MessageQueueException serializationFailed(String resourceId, String queueName, Throwable cause) {
        return new MessageQueueException(
                "Message serialization failed",
                resourceId,
                queueName,
                "serialize",
                ERROR_SERIALIZATION,
                cause
        );
    }

    /**
     * 创建反序列化异常
     */
    public static MessageQueueException deserializationFailed(String resourceId, String queueName, Throwable cause) {
        return new MessageQueueException(
                "Message deserialization failed",
                resourceId,
                queueName,
                "deserialize",
                ERROR_DESERIALIZATION,
                cause
        );
    }

    /**
     * 创建网络异常
     */
    public static MessageQueueException networkError(String resourceId, String operation, Throwable cause) {
        return new MessageQueueException(
                "Network error during " + operation,
                resourceId,
                null,
                operation,
                ERROR_NETWORK,
                cause
        );
    }

    @Override
    public String toString() {
        return getReadableDescription();
    }
}