package com.galaxytx.core.exception;

/**
 * 连接超时异常
 */
public class ConnectionTimeoutException extends NetworkException {
    private final int timeoutMs;

    public ConnectionTimeoutException(String message, String remoteAddress, int timeoutMs) {
        super(message, remoteAddress);
        this.timeoutMs = timeoutMs;
    }

    public ConnectionTimeoutException(String message, String remoteAddress, int timeoutMs, Throwable cause) {
        super(message, remoteAddress, cause);
        this.timeoutMs = timeoutMs;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    @Override
    public String getReadableDescription() {
        return String.format("ConnectionTimeoutException{timeout=%dms, remote=%s}",
                timeoutMs, getRemoteAddress());
    }
}






