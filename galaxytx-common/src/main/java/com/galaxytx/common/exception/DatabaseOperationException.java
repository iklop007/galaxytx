package com.galaxytx.common.exception;

/**
 * 数据库操作异常
 */
public class DatabaseOperationException extends RuntimeException {
    public DatabaseOperationException(String message) {
        super(message);
    }

    public DatabaseOperationException(String message, Throwable cause) {
        super(message, cause);
    }

    public DatabaseOperationException(String message, String sqlState, int errorCode) {
        super(String.format("%s [SQLState: %s, ErrorCode: %d]", message, sqlState, errorCode));
    }
}