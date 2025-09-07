package com.galaxytx.common.exception;

/**
 * 连接重置异常
 */
public class ConnectionResetException extends NetworkException {
    public ConnectionResetException(String message, String remoteAddress) {
        super(message, remoteAddress);
    }

    public ConnectionResetException(String message, String remoteAddress, Throwable cause) {
        super(message, remoteAddress, cause);
    }

    @Override
    public String getReadableDescription() {
        return String.format("ConnectionResetException{remote=%s}", getRemoteAddress());
    }
}


