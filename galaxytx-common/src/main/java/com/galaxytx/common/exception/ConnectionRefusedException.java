package com.galaxytx.common.exception;

/**
 * 连接拒绝异常
 */
public class ConnectionRefusedException extends NetworkException {
    public ConnectionRefusedException(String message, String remoteAddress) {
        super(message, remoteAddress);
    }

    public ConnectionRefusedException(String message, String remoteAddress, Throwable cause) {
        super(message, remoteAddress, cause);
    }

    @Override
    public String getReadableDescription() {
        return String.format("ConnectionRefusedException{remote=%s}", getRemoteAddress());
    }
}
