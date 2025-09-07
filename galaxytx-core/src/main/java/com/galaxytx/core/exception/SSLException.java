package com.galaxytx.core.exception;

/**
 * SSL/TLS异常
 */
public class SSLException extends NetworkException {
    private final String sslProtocol;

    public SSLException(String message, String remoteAddress, String sslProtocol) {
        super(message, remoteAddress);
        this.sslProtocol = sslProtocol;
    }

    public SSLException(String message, String remoteAddress, String sslProtocol, Throwable cause) {
        super(message, remoteAddress, cause);
        this.sslProtocol = sslProtocol;
    }

    public String getSslProtocol() {
        return sslProtocol;
    }

    @Override
    public String getReadableDescription() {
        return String.format("SSLException{protocol=%s, remote=%s}", sslProtocol, getRemoteAddress());
    }
}
