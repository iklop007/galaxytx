package com.galaxytx.common.exception;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * 网络异常工具类
 */
public class NetworkExceptionUtils {

    private NetworkExceptionUtils() {
        // 工具类
    }

    /**
     * 将Java标准异常转换为自定义网络异常
     */
    public static NetworkException convertToNetworkException(Exception e, String remoteAddress) {
        return convertToNetworkException(e, remoteAddress, 0);
    }

    /**
     * 将Java标准异常转换为自定义网络异常（带超时时间）
     */
    public static NetworkException convertToNetworkException(Exception e, String remoteAddress, int timeoutMs) {
        if (e instanceof SocketTimeoutException) {
            if (e.getMessage() != null && e.getMessage().contains("connect")) {
                return new ConnectionTimeoutException("Connection timeout", remoteAddress, timeoutMs, e);
            } else {
                return new ReadTimeoutException("Read timeout", remoteAddress, timeoutMs, e);
            }
        } else if (e instanceof ConnectException) {
            return new ConnectionRefusedException("Connection refused", remoteAddress, e);
        } else if (e instanceof UnknownHostException) {
            return new HostUnreachableException("Unknown host", remoteAddress, e);
        } else if (e instanceof SSLHandshakeException) {
            return new SSLException("SSL handshake failed", remoteAddress, "TLS", e);
        } else if (e instanceof IOException) {
            String message = e.getMessage();
            if (message != null) {
                if (message.contains("Connection reset")) {
                    return new ConnectionResetException("Connection reset", remoteAddress, e);
                } else if (message.contains("Network is unreachable")) {
                    return new NetworkUnreachableException("Network unreachable", remoteAddress, e);
                }
            }
            return new NetworkException("IO error: " + message, remoteAddress, e);
        } else {
            return new NetworkException("Network communication error", remoteAddress, e);
        }
    }

    /**
     * 判断异常是否是可重试的网络异常
     */
    public static boolean isRetryableNetworkException(Throwable e) {
        if (e instanceof NetworkException) {
            return ((NetworkException) e).isRetryable();
        }

        // 检查标准异常类型
        if (e instanceof SocketTimeoutException) {
            return true; // 超时通常可以重试
        } else if (e instanceof ConnectException) {
            return true; // 连接拒绝通常可以重试（等待服务恢复）
        } else if (e instanceof UnknownHostException) {
            return false; // 未知主机需要人工干预
        } else if (e instanceof SSLHandshakeException) {
            return false; // SSL握手错误通常需要配置修复
        }

        return false;
    }

    /**
     * 获取异常的严重级别
     */
    public static Severity getExceptionSeverity(NetworkException e) {
        if (e instanceof ConnectionTimeoutException || e instanceof ReadTimeoutException) {
            return Severity.WARNING; // 超时通常是临时性问题
        } else if (e instanceof ConnectionRefusedException) {
            return Severity.ERROR; // 连接拒绝可能表示服务宕机
        } else if (e instanceof NetworkUnreachableException || e instanceof HostUnreachableException) {
            return Severity.CRITICAL; // 网络不可达需要立即处理
        } else if (e instanceof SSLException) {
            return Severity.ERROR; // SSL错误需要配置修复
        } else if (e instanceof ProtocolException) {
            return Severity.CRITICAL; // 协议错误通常无法自动恢复
        }

        return Severity.ERROR;
    }

    public enum Severity {
        INFO,       // 信息级别
        WARNING,    // 警告级别
        ERROR,      // 错误级别
        CRITICAL    // 严重级别
    }

    /**
     * 创建连接超时异常
     */
    public static ConnectionTimeoutException createConnectionTimeout(String remoteAddress, int timeoutMs) {
        return new ConnectionTimeoutException(
                String.format("Connection to %s timed out after %dms", remoteAddress, timeoutMs),
                remoteAddress, timeoutMs
        );
    }

    /**
     * 创建读取超时异常
     */
    public static ReadTimeoutException createReadTimeout(String remoteAddress, int timeoutMs) {
        return new ReadTimeoutException(
                String.format("Read from %s timed out after %dms", remoteAddress, timeoutMs),
                remoteAddress, timeoutMs
        );
    }
}