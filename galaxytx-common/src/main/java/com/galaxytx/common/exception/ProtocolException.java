package com.galaxytx.common.exception;

/**
 * 协议异常
 */
public class ProtocolException extends NetworkException {
    private final String protocol;

    public ProtocolException(String message, String remoteAddress, String protocol) {
        super(message, remoteAddress);
        this.protocol = protocol;
    }

    public ProtocolException(String message, String remoteAddress, String protocol, Throwable cause) {
        super(message, remoteAddress, cause);
        this.protocol = protocol;
    }

    public String getProtocol() {
        return protocol;
    }

    @Override
    public String getReadableDescription() {
        return String.format("ProtocolException{protocol=%s, remote=%s}", protocol, getRemoteAddress());
    }
}
