package com.galaxytx.datasource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据源管理器
 */
public class DataSourceManager {
    private static DataSource dataSource;

    public static void setDataSource(DataSource ds) {
        dataSource = ds;
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new RuntimeException("DataSource not initialized.");
        }
        return dataSource.getConnection();
    }
}
