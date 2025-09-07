package com.galaxytx.datasource;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

public class DataSourceProxy implements DataSource {

    private final DataSource targetDataSource;
    private final String resourceGroupId;

    public DataSourceProxy(DataSource targetDataSource, String resourceGroupId) {
        this.targetDataSource = targetDataSource;
        this.resourceGroupId = resourceGroupId;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return new ConnectionProxy(targetDataSource.getConnection(), resourceGroupId);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return new ConnectionProxy(targetDataSource.getConnection(username, password), resourceGroupId);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return targetDataSource.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        targetDataSource.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        targetDataSource.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return targetDataSource.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }
}
