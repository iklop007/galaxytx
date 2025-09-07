package com.galaxytx.core.exception;

/**
 * 网络异常基类
 * 用于表示分布式事务中的网络通信问题
 */
public class NetworkException extends RuntimeException {
    private final String remoteAddress;
    private final int errorCode;
    private final long timestamp;

    public NetworkException(String message) {
        super(message);
        this.remoteAddress = null;
        this.errorCode = 0;
        this.timestamp = System.currentTimeMillis();
    }

    public NetworkException(String message, String remoteAddress) {
        super(message + (remoteAddress != null ? " [remote: " + remoteAddress + "]" : ""));
        this.remoteAddress = remoteAddress;
        this.errorCode = 0;
        this.timestamp = System.currentTimeMillis();
    }

    public NetworkException(String message, String remoteAddress, Throwable cause) {
        super(message + (remoteAddress != null ? " [remote: " + remoteAddress + "]" : ""), cause);
        this.remoteAddress = remoteAddress;
        this.errorCode = 0;
        this.timestamp = System.currentTimeMillis();
    }

    public NetworkException(String message, String remoteAddress, int errorCode, Throwable cause) {
        super(message + (remoteAddress != null ? " [remote: " + remoteAddress + "]" : "") +
                " [code: " + errorCode + "]", cause);
        this.remoteAddress = remoteAddress;
        this.errorCode = errorCode;
        this.timestamp = System.currentTimeMillis();
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 获取异常的人类可读描述
     */
    public String getReadableDescription() {
        return String.format("NetworkException{message=%s, remote=%s, code=%d, time=%tF %tT}",
                getMessage(), remoteAddress, errorCode, timestamp, timestamp);
    }

    /**
     * 判断是否是连接超时异常
     */
    public boolean isConnectionTimeout() {
        return this instanceof ConnectionTimeoutException;
    }

    /**
     * 判断是否是读取超时异常
     */
    public boolean isReadTimeout() {
        return this instanceof ReadTimeoutException;
    }

    /**
     * 判断是否是连接拒绝异常
     */
    public boolean isConnectionRefused() {
        return this instanceof ConnectionRefusedException;
    }

    /**
     * 判断是否是可重试的异常
     */
    public boolean isRetryable() {
        // 连接超时、读取超时通常可以重试
        if (isConnectionTimeout() || isReadTimeout()) {
            return true;
        }
        // 连接拒绝可能需要等待服务恢复
        if (isConnectionRefused()) {
            return true;
        }
        // 网络不可达可能需要检查网络配置
        if (this instanceof NetworkUnreachableException) {
            return false; // 通常需要人工干预
        }
        // 协议错误通常不能重试
        if (this instanceof ProtocolException) {
            return false;
        }
        // 默认情况下，网络异常可以重试
        return true;
    }
}