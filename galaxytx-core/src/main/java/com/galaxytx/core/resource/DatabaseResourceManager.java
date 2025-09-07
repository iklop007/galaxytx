package com.galaxytx.core.resource;

import com.galaxytx.core.model.BranchTransaction;
import com.galaxytx.core.model.CommunicationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库资源管理器
 * 负责处理数据库类型资源的提交和回滚操作
 *
 * @author 刘志成
 * @date 2023-09-07
 */
@Component
public class DatabaseResourceManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseResourceManager.class);

    private final Map<String, DataSource> dataSourceMap = new ConcurrentHashMap<>();
    private final DatabaseOperationTemplate operationTemplate;

    @Autowired
    public DatabaseResourceManager(DatabaseOperationTemplate operationTemplate) {
        this.operationTemplate = operationTemplate;
    }

    /**
     * 注册数据源
     */
    public void registerDataSource(String resourceId, DataSource dataSource) {
        dataSourceMap.put(resourceId, dataSource);
        logger.info("Registered datasource for resource: {}", resourceId);
    }

    /**
     * 提交数据库分支事务
     */
    public CommunicationResult commit(BranchTransaction branch) {
        String resourceId = branch.getResourceId();
        String xid = branch.getXid();
        long branchId = branch.getBranchId();

        logger.info("Committing database transaction: xid={}, branchId={}, resourceId={}",
                xid, branchId, resourceId);

        try {
            DataSource dataSource = getDataSource(resourceId);
            if (dataSource == null) {
                return CommunicationResult.failure("DataSource not found for resource: " + resourceId);
            }

            try (Connection connection = dataSource.getConnection()) {
                // 检查连接是否支持XA
                if (isXASupported(connection)) {
                    return commitXA(connection, branch);
                } else {
                    return commitLocal(connection, branch);
                }
            }

        } catch (SQLException e) {
            logger.error("Database commit failed: xid={}, branchId={}", xid, branchId, e);
            return CommunicationResult.failure("Database commit failed: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during database commit: xid={}, branchId={}", xid, branchId, e);
            return CommunicationResult.failure("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * 回滚数据库分支事务
     */
    public CommunicationResult rollback(BranchTransaction branch) {
        String resourceId = branch.getResourceId();
        String xid = branch.getXid();
        long branchId = branch.getBranchId();

        logger.info("Rolling back database transaction: xid={}, branchId={}, resourceId={}",
                xid, branchId, resourceId);

        try {
            DataSource dataSource = getDataSource(resourceId);
            if (dataSource == null) {
                return CommunicationResult.failure("DataSource not found for resource: " + resourceId);
            }

            try (Connection connection = dataSource.getConnection()) {
                if (isXASupported(connection)) {
                    return rollbackXA(connection, branch);
                } else {
                    return rollbackLocal(connection, branch);
                }
            }

        } catch (SQLException e) {
            logger.error("Database rollback failed: xid={}, branchId={}", xid, branchId, e);
            return CommunicationResult.failure("Database rollback failed: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during database rollback: xid={}, branchId={}", xid, branchId, e);
            return CommunicationResult.failure("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * XA事务提交
     */
    private CommunicationResult commitXA(Connection connection, BranchTransaction branch) throws SQLException {
        return operationTemplate.executeXAOperation(connection, branch, true);
    }

    /**
     * XA事务回滚
     */
    private CommunicationResult rollbackXA(Connection connection, BranchTransaction branch) throws SQLException {
        return operationTemplate.executeXAOperation(connection, branch, false);
    }

    /**
     * 本地事务提交（AT模式）
     */
    private CommunicationResult commitLocal(Connection connection, BranchTransaction branch) throws SQLException {
        return operationTemplate.executeLocalOperation(connection, branch, true);
    }

    /**
     * 本地事务回滚（AT模式）
     */
    private CommunicationResult rollbackLocal(Connection connection, BranchTransaction branch) throws SQLException {
        return operationTemplate.executeLocalOperation(connection, branch, false);
    }

    /**
     * 检查数据库是否支持XA事务
     */
    private boolean isXASupported(Connection connection) throws SQLException {
        try {
            // 检查数据库元数据是否支持XA
            return connection.getMetaData().supportsMultipleTransactions() &&
                    isXADriver(connection.getMetaData().getDriverName());
        } catch (Exception e) {
            logger.warn("Failed to check XA support, assuming no XA support", e);
            return false;
        }
    }

    /**
     * 检查驱动是否支持XA
     */
    private boolean isXADriver(String driverName) {
        if (driverName == null) {
            return false;
        }
        String lowerDriver = driverName.toLowerCase();
        return lowerDriver.contains("xa") ||
                lowerDriver.contains("oracle") ||
                lowerDriver.contains("mysql") ||
                lowerDriver.contains("postgresql") ||
                lowerDriver.contains("db2");
    }

    /**
     * 获取数据源
     */
    private DataSource getDataSource(String resourceId) {
        DataSource dataSource = dataSourceMap.get(resourceId);
        if (dataSource == null) {
            logger.warn("DataSource not found for resource: {}", resourceId);
        }
        return dataSource;
    }

    /**
     * 获取所有注册的数据源
     */
    public Map<String, DataSource> getDataSources() {
        return new ConcurrentHashMap<>(dataSourceMap);
    }

    /**
     * 移除数据源
     */
    public void removeDataSource(String resourceId) {
        dataSourceMap.remove(resourceId);
        logger.info("Removed datasource for resource: {}", resourceId);
    }

    /**
     * 检查资源是否存在
     */
    public boolean containsResource(String resourceId) {
        return dataSourceMap.containsKey(resourceId);
    }

    /**
     * 清理所有资源
     */
    public void cleanup() {
        dataSourceMap.clear();
        logger.info("Cleaned up all database resources");
    }
}