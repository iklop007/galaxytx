package com.galaxytx.common.exception;

/**
 * 主机不可达异常
 */
public class HostUnreachableException extends NetworkException {
    public HostUnreachableException(String message, String remoteAddress) {
        super(message, remoteAddress);
    }

    public HostUnreachableException(String message, String remoteAddress, Throwable cause) {
        super(message, remoteAddress, cause);
    }

    @Override
    public String getReadableDescription() {
        return String.format("HostUnreachableException{remote=%s}", getRemoteAddress());
    }
}
