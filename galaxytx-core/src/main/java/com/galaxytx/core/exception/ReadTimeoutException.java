package com.galaxytx.core.exception;

/**
 * 读取超时异常
 */
public class ReadTimeoutException extends NetworkException {
    private final int timeoutMs;

    public ReadTimeoutException(String message, String remoteAddress, int timeoutMs) {
        super(message, remoteAddress);
        this.timeoutMs = timeoutMs;
    }

    public ReadTimeoutException(String message, String remoteAddress, int timeoutMs, Throwable cause) {
        super(message, remoteAddress, cause);
        this.timeoutMs = timeoutMs;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    @Override
    public String getReadableDescription() {
        return String.format("ReadTimeoutException{timeout=%dms, remote=%s}",
                timeoutMs, getRemoteAddress());
    }
}
