package com.galaxytx.common.exception;

/**
 * 网络不可达异常
 */
public class NetworkUnreachableException extends NetworkException {
    public NetworkUnreachableException(String message, String remoteAddress) {
        super(message, remoteAddress);
    }

    public NetworkUnreachableException(String message, String remoteAddress, Throwable cause) {
        super(message, remoteAddress, cause);
    }

    @Override
    public String getReadableDescription() {
        return String.format("NetworkUnreachableException{remote=%s}", getRemoteAddress());
    }
}
