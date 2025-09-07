package com.galaxytx.common.resource;

import com.galaxytx.common.exception.DatabaseOperationException;
import com.galaxytx.common.model.BranchTransaction;
import com.galaxytx.common.model.CommunicationResult;
import com.galaxytx.common.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库操作模板
 * 提供统一的数据库事务操作模板，支持XA事务和AT模式
 */
@Component
public class DatabaseOperationTemplate {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseOperationTemplate.class);

    // Undo log SQL
    private static final String INSERT_UNDO_LOG_SQL =
            "INSERT INTO undo_log (xid, branch_id, table_name, sql_type, " +
                    "before_image, after_image, sql_text, parameters, log_status, create_time) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";

    private static final String SELECT_UNDO_LOG_SQL =
            "SELECT * FROM undo_log WHERE xid = ? AND branch_id = ? AND log_status = 0 " +
                    "ORDER BY create_time DESC";

    private static final String DELETE_UNDO_LOG_SQL =
            "DELETE FROM undo_log WHERE xid = ? AND branch_id = ?";

    private static final String UPDATE_UNDO_LOG_STATUS_SQL =
            "UPDATE undo_log SET log_status = ? WHERE xid = ? AND branch_id = ?";

    // 数据源缓存（用于补偿操作）
    private final Map<String, DataSource> dataSourceCache = new ConcurrentHashMap<>();

    @Autowired
    private UndoLogManager undoLogManager;

    /**
     * 执行XA事务操作
     */
    public CommunicationResult executeXAOperation(Connection connection, BranchTransaction branch, boolean isCommit)
            throws SQLException {

        String xid = branch.getXid();
        long branchId = branch.getBranchId();
        String resourceId = branch.getResourceId();

        logger.debug("Executing XA operation: xid={}, branchId={}, operation={}",
                xid, branchId, isCommit ? "COMMIT" : "ROLLBACK");

        try {
            if (isCommit) {
                // XA提交
                return commitXA(connection, branch);
            } else {
                // XA回滚
                return rollbackXA(connection, branch);
            }
        } catch (SQLException e) {
            logger.error("XA operation failed: xid={}, branchId={}", xid, branchId, e);
            throw new DatabaseOperationException("XA operation failed", e);
        }
    }

    /**
     * 执行本地事务操作（AT模式）
     */
    public CommunicationResult executeLocalOperation(Connection connection, BranchTransaction branch, boolean isCommit)
            throws SQLException {

        String xid = branch.getXid();
        long branchId = branch.getBranchId();

        logger.debug("Executing local operation: xid={}, branchId={}, operation={}",
                xid, branchId, isCommit ? "COMMIT" : "ROLLBACK");

        try {
            if (isCommit) {
                // AT模式提交：删除undo log
                return commitLocal(connection, branch);
            } else {
                // AT模式回滚：执行补偿操作
                return rollbackLocal(connection, branch);
            }
        } catch (SQLException e) {
            logger.error("Local operation failed: xid={}, branchId={}", xid, branchId, e);
            throw new DatabaseOperationException("Local operation failed", e);
        }
    }

    /**
     * XA事务提交
     */
    private CommunicationResult commitXA(Connection connection, BranchTransaction branch) throws SQLException {
        try {
            // XA事务提交
            if (!connection.getAutoCommit()) {
                connection.commit();
            }

            logger.info("XA transaction committed successfully: xid={}, branchId={}",
                    branch.getXid(), branch.getBranchId());
            return CommunicationResult.success();

        } catch (SQLException e) {
            logger.error("XA commit failed: xid={}, branchId={}", branch.getXid(), branch.getBranchId(), e);
            // 尝试回滚
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                logger.error("XA rollback also failed after commit failure", rollbackEx);
            }
            throw e;
        }
    }

    /**
     * XA事务回滚
     */
    private CommunicationResult rollbackXA(Connection connection, BranchTransaction branch) throws SQLException {
        try {
            // XA事务回滚
            if (!connection.getAutoCommit()) {
                connection.rollback();
            }

            logger.info("XA transaction rolled back successfully: xid={}, branchId={}",
                    branch.getXid(), branch.getBranchId());
            return CommunicationResult.success();

        } catch (SQLException e) {
            logger.error("XA rollback failed: xid={}, branchId={}", branch.getXid(), branch.getBranchId(), e);
            throw e;
        }
    }

    /**
     * AT模式提交：清理undo log
     */
    private CommunicationResult commitLocal(Connection connection, BranchTransaction branch) throws SQLException {
        String xid = branch.getXid();
        long branchId = branch.getBranchId();

        try {
            // 删除undo log
            int deletedRows = deleteUndoLog(connection, xid, branchId);

            logger.info("Local transaction committed, deleted {} undo logs: xid={}, branchId={}",
                    deletedRows, xid, branchId);
            return CommunicationResult.success();

        } catch (SQLException e) {
            logger.error("Failed to delete undo log during commit: xid={}, branchId={}", xid, branchId, e);
            // 即使undo log删除失败，仍然认为提交成功（最终一致性通过其他机制保证）
            return CommunicationResult.success();
        }
    }

    /**
     * AT模式回滚：执行补偿操作
     */
    private CommunicationResult rollbackLocal(Connection connection, BranchTransaction branch) throws SQLException {
        String xid = branch.getXid();
        long branchId = branch.getBranchId();
        String resourceId = branch.getResourceId();

        try {
            // 1. 查询undo log
            Map<String, Object> undoLog = findUndoLog(connection, xid, branchId);
            if (undoLog == null || undoLog.isEmpty()) {
                logger.warn("No undo log found for rollback: xid={}, branchId={}", xid, branchId);
                return CommunicationResult.failure("No undo log found for rollback");
            }

            // 2. 执行补偿操作
            boolean compensationSuccess = executeCompensation(connection, undoLog, branch);

            if (compensationSuccess) {
                // 3. 删除undo log
                deleteUndoLog(connection, xid, branchId);

                logger.info("Local transaction rolled back successfully: xid={}, branchId={}", xid, branchId);
                return CommunicationResult.success();
            } else {
                logger.error("Compensation failed: xid={}, branchId={}", xid, branchId);
                return CommunicationResult.failure("Compensation failed");
            }

        } catch (SQLException e) {
            logger.error("Local rollback failed: xid={}, branchId={}", xid, branchId, e);
            throw e;
        }
    }

    /**
     * 执行补偿操作
     */
    private boolean executeCompensation(Connection connection, Map<String, Object> undoLog, BranchTransaction branch)
            throws SQLException {

        String sqlType = (String) undoLog.get("sql_type");
        String tableName = (String) undoLog.get("table_name");
        String beforeImage = (String) undoLog.get("before_image");
        String afterImage = (String) undoLog.get("after_image");
        String sqlText = (String) undoLog.get("sql_text");
        String parameters = (String) undoLog.get("parameters");

        logger.debug("Executing compensation: type={}, table={}, xid={}, branchId={}",
                sqlType, tableName, branch.getXid(), branch.getBranchId());

        try {
            switch (sqlType.toUpperCase()) {
                case "INSERT":
                    return compensateInsert(connection, tableName, beforeImage, afterImage);
                case "UPDATE":
                    return compensateUpdate(connection, tableName, beforeImage, afterImage);
                case "DELETE":
                    return compensateDelete(connection, tableName, beforeImage, afterImage);
                default:
                    logger.warn("Unsupported SQL type for compensation: {}", sqlType);
                    return false;
            }
        } catch (Exception e) {
            logger.error("Compensation execution failed: xid={}, branchId={}",
                    branch.getXid(), branch.getBranchId(), e);
            return false;
        }
    }

    /**
     * 补偿INSERT操作（执行DELETE）
     */
    private boolean compensateInsert(Connection connection, String tableName,
                                     String beforeImage, String afterImage) throws SQLException {

        // INSERT操作的补偿是DELETE
        // 从afterImage中提取主键信息
        Map<String, Object> rowData = parseImageData(afterImage);
        if (rowData == null || rowData.isEmpty()) {
            logger.warn("No data found in afterImage for INSERT compensation");
            return false;
        }

        String deleteSql = buildDeleteSql(tableName, rowData);
        try (PreparedStatement pstmt = connection.prepareStatement(deleteSql)) {
            setParameters(pstmt, rowData, 1);
            int affectedRows = pstmt.executeUpdate();

            logger.debug("INSERT compensation executed: {} rows affected", affectedRows);
            return affectedRows > 0;
        }
    }

    /**
     * 补偿UPDATE操作（执行反向UPDATE）
     */
    private boolean compensateUpdate(Connection connection, String tableName,
                                     String beforeImage, String afterImage) throws SQLException {

        // UPDATE操作的补偿是用beforeImage数据恢复
        Map<String, Object> beforeData = parseImageData(beforeImage);
        Map<String, Object> afterData = parseImageData(afterImage);

        if (beforeData == null || afterData == null) {
            logger.warn("Invalid image data for UPDATE compensation");
            return false;
        }

        String updateSql = buildUpdateSql(tableName, beforeData, afterData);
        try (PreparedStatement pstmt = connection.prepareStatement(updateSql)) {
            int paramIndex = setUpdateParameters(pstmt, beforeData, afterData);
            setWhereParameters(pstmt, afterData, paramIndex);

            int affectedRows = pstmt.executeUpdate();
            logger.debug("UPDATE compensation executed: {} rows affected", affectedRows);
            return affectedRows > 0;
        }
    }

    /**
     * 补偿DELETE操作（执行INSERT）
     */
    private boolean compensateDelete(Connection connection, String tableName,
                                     String beforeImage, String afterImage) throws SQLException {

        // DELETE操作的补偿是INSERT
        Map<String, Object> rowData = parseImageData(beforeImage);
        if (rowData == null || rowData.isEmpty()) {
            logger.warn("No data found in beforeImage for DELETE compensation");
            return false;
        }

        String insertSql = buildInsertSql(tableName, rowData);
        try (PreparedStatement pstmt = connection.prepareStatement(insertSql)) {
            setParameters(pstmt, rowData, 1);
            int affectedRows = pstmt.executeUpdate();

            logger.debug("DELETE compensation executed: {} rows affected", affectedRows);
            return affectedRows > 0;
        }
    }

    /**
     * 查询undo log
     */
    private Map<String, Object> findUndoLog(Connection connection, String xid, long branchId) throws SQLException {
        try (PreparedStatement pstmt = connection.prepareStatement(SELECT_UNDO_LOG_SQL)) {
            pstmt.setString(1, xid);
            pstmt.setLong(2, branchId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    // 将ResultSet转换为Map
                    return resultSetToMap(rs);
                }
            }
        }
        return null;
    }

    /**
     * 删除undo log
     */
    private int deleteUndoLog(Connection connection, String xid, long branchId) throws SQLException {
        try (PreparedStatement pstmt = connection.prepareStatement(DELETE_UNDO_LOG_SQL)) {
            pstmt.setString(1, xid);
            pstmt.setLong(2, branchId);
            return pstmt.executeUpdate();
        }
    }

    /**
     * 更新undo log状态
     */
    private int updateUndoLogStatus(Connection connection, String xid, long branchId, int status) throws SQLException {
        try (PreparedStatement pstmt = connection.prepareStatement(UPDATE_UNDO_LOG_STATUS_SQL)) {
            pstmt.setInt(1, status);
            pstmt.setString(2, xid);
            pstmt.setLong(3, branchId);
            return pstmt.executeUpdate();
        }
    }

    /**
     * 构建DELETE SQL
     */
    private String buildDeleteSql(String tableName, Map<String, Object> rowData) {
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(tableName).append(" WHERE ");

        boolean first = true;
        for (String column : rowData.keySet()) {
            if (!first) {
                sql.append(" AND ");
            }
            sql.append(column).append(" = ?");
            first = false;
        }

        return sql.toString();
    }

    /**
     * 构建UPDATE SQL
     */
    private String buildUpdateSql(String tableName, Map<String, Object> newData, Map<String, Object> whereData) {
        StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");

        // SET 部分
        boolean first = true;
        for (String column : newData.keySet()) {
            if (!first) {
                sql.append(", ");
            }
            sql.append(column).append(" = ?");
            first = false;
        }

        // WHERE 部分
        sql.append(" WHERE ");
        first = true;
        for (String column : whereData.keySet()) {
            if (!first) {
                sql.append(" AND ");
            }
            sql.append(column).append(" = ?");
            first = false;
        }

        return sql.toString();
    }

    /**
     * 构建INSERT SQL
     */
    private String buildInsertSql(String tableName, Map<String, Object> rowData) {
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
        StringBuilder values = new StringBuilder("VALUES (");

        boolean first = true;
        for (String column : rowData.keySet()) {
            if (!first) {
                sql.append(", ");
                values.append(", ");
            }
            sql.append(column);
            values.append("?");
            first = false;
        }

        sql.append(") ").append(values).append(")");
        return sql.toString();
    }

    /**
     * 设置参数
     */
    private void setParameters(PreparedStatement pstmt, Map<String, Object> parameters, int startIndex)
            throws SQLException {

        int index = startIndex;
        for (Object value : parameters.values()) {
            pstmt.setObject(index++, value);
        }
    }

    /**
     * 设置UPDATE参数（SET部分和WHERE部分）
     */
    private int setUpdateParameters(PreparedStatement pstmt, Map<String, Object> setData,
                                    Map<String, Object> whereData) throws SQLException {

        int index = 1;
        // 先设置SET部分的参数
        for (Object value : setData.values()) {
            pstmt.setObject(index++, value);
        }
        // 再设置WHERE部分的参数
        for (Object value : whereData.values()) {
            pstmt.setObject(index++, value);
        }
        return index;
    }

    /**
     * 设置WHERE参数
     */
    private void setWhereParameters(PreparedStatement pstmt, Map<String, Object> whereData, int startIndex)
            throws SQLException {

        int index = startIndex;
        for (Object value : whereData.values()) {
            pstmt.setObject(index++, value);
        }
    }

    /**
     * 解析镜像数据
     */
    private Map<String, Object> parseImageData(String imageData) {
        if (imageData == null || imageData.trim().isEmpty()) {
            return null;
        }
        try {
            // 假设镜像数据是JSON格式
            return JsonUtils.fromJson(imageData, Map.class);
        } catch (Exception e) {
            logger.warn("Failed to parse image data: {}", imageData, e);
            return null;
        }
    }

    /**
     * ResultSet转换为Map
     */
    private Map<String, Object> resultSetToMap(ResultSet rs) throws SQLException {
        Map<String, Object> result = new java.util.HashMap<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnName(i);
            Object value = rs.getObject(i);
            result.put(columnName, value);
        }

        return result;
    }

    /**
     * 获取数据源（用于补偿操作）
     */
    public DataSource getDataSource(String resourceId) {
        return dataSourceCache.get(resourceId);
    }

    /**
     * 注册数据源
     */
    public void registerDataSource(String resourceId, DataSource dataSource) {
        dataSourceCache.put(resourceId, dataSource);
    }

    /**
     * 检查连接是否支持XA
     */
    public boolean isXASupported(Connection connection) {
        try {
            // 检查数据库是否支持XA
            DatabaseMetaData metaData = connection.getMetaData();
            return metaData.supportsMultipleTransactions() &&
                    !connection.getAutoCommit() &&
                    isXADriver(metaData.getDriverName());
        } catch (SQLException e) {
            logger.warn("Failed to check XA support", e);
            return false;
        }
    }

    /**
     * 检查驱动是否支持XA
     */
    private boolean isXADriver(String driverName) {
        if (driverName == null) return false;
        String lowerName = driverName.toLowerCase();
        return lowerName.contains("xa") ||
                lowerName.contains("oracle") ||
                lowerName.contains("mysql") ||
                lowerName.contains("postgresql") ||
                lowerName.contains("db2");
    }

    /**
     * 执行SQL语句（用于测试和工具方法）
     */
    public int executeSql(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }
            return pstmt.executeUpdate();
        }
    }

    /**
     * 查询数据（用于测试和工具方法）
     */
    public ResultSet executeQuery(Connection connection, String sql, Object... params) throws SQLException {
        PreparedStatement pstmt = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            pstmt.setObject(i + 1, params[i]);
        }
        return pstmt.executeQuery();
    }
}