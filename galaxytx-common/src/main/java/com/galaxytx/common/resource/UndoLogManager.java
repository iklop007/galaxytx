package com.galaxytx.common.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galaxytx.common.manager.DataSourceManager;
import com.galaxytx.common.model.TableRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

/**
 * Undo Log 管理器
 * 负责undo log的存储、查询和删除
 *
 * @author 刘志成
 * @date 2023-09-05
 */
public class UndoLogManager {
    private static final Logger logger = LoggerFactory.getLogger(UndoLogManager.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Undo log 状态
    public static final int UNDO_LOG_STATUS_NORMAL = 0;
    public static final int UNDO_LOG_STATUS_COMPENSATING = 1;
    public static final int UNDO_LOG_STATUS_COMPENSATED = 2;

    /**
     * 添加undo log
     */
    public void addUndoLog(String xid, Long branchId, String tableName,
                           TableRecords beforeImage, TableRecords afterImage,
                           String sql, Object[] parameters) throws SQLException {

        if (beforeImage == null && afterImage == null) {
            return; // 没有数据变化，不需要记录undo log
        }

        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            // 获取数据库连接（这里需要从数据源获取）
            conn = DataSourceManager.getConnection();

            String insertSql = "INSERT INTO undo_log (xid, branch_id, table_name, " +
                    "before_image, after_image, sql_text, parameters, log_status, " +
                    "create_time, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            pstmt = conn.prepareStatement(insertSql);
            pstmt.setString(1, xid);
            pstmt.setLong(2, branchId);
            pstmt.setString(3, tableName);
            pstmt.setString(4, serializeImage(beforeImage));
            pstmt.setString(5, serializeImage(afterImage));
            pstmt.setString(6, sql);
            pstmt.setString(7, serializeParameters(parameters));
            pstmt.setInt(8, UNDO_LOG_STATUS_NORMAL);
            pstmt.setTimestamp(9, new java.sql.Timestamp(System.currentTimeMillis()));
            pstmt.setTimestamp(10, new java.sql.Timestamp(System.currentTimeMillis()));

            pstmt.executeUpdate();

            logger.debug("Added undo log for xid: {}, branchId: {}, table: {}",
                    xid, branchId, tableName);

        } finally {
            closeResources(conn, pstmt, null);
        }
    }

    /**
     * 序列化前后镜像数据
     */
    private String serializeImage(TableRecords image) {
        if (image == null || image.isEmpty()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(image.toMapList());
        } catch (Exception e) {
            logger.error("Failed to serialize table records", e);
            return null;
        }
    }

    /**
     * 序列化参数
     */
    private String serializeParameters(Object[] parameters) {
        if (parameters == null || parameters.length == 0) {
            return null;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(parameters);
        } catch (Exception e) {
            logger.error("Failed to serialize parameters", e);
            return null;
        }
    }

    /**
     * 根据xid和branchId查询undo log
     */
    public UndoLog queryUndoLog(String xid, Long branchId) throws SQLException {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = DataSourceManager.getConnection();
            String sql = "SELECT * FROM undo_log WHERE xid = ? AND branch_id = ? AND log_status = ?";

            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, xid);
            pstmt.setLong(2, branchId);
            pstmt.setInt(3, UNDO_LOG_STATUS_NORMAL);

            rs = pstmt.executeQuery();

            if (rs.next()) {
                UndoLog undoLog = new UndoLog();
                undoLog.setId(rs.getLong("id"));
                undoLog.setXid(rs.getString("xid"));
                undoLog.setBranchId(rs.getLong("branch_id"));
                undoLog.setTableName(rs.getString("table_name"));
                undoLog.setBeforeImage(rs.getString("before_image"));
                undoLog.setAfterImage(rs.getString("after_image"));
                undoLog.setSqlText(rs.getString("sql_text"));
                undoLog.setParameters(rs.getString("parameters"));
                undoLog.setLogStatus(rs.getInt("log_status"));
                undoLog.setCreateTime(rs.getTimestamp("create_time"));
                undoLog.setUpdateTime(rs.getTimestamp("update_time"));
                return undoLog;
            }

            return null;

        } finally {
            closeResources(conn, pstmt, rs);
        }
    }

    /**
     * 删除undo log
     */
    public void deleteUndoLog(String xid, Long branchId) throws SQLException {
        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = DataSourceManager.getConnection();
            String sql = "DELETE FROM undo_log WHERE xid = ? AND branch_id = ?";

            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, xid);
            pstmt.setLong(2, branchId);

            int affectedRows = pstmt.executeUpdate();
            logger.debug("Deleted {} undo logs for xid: {}, branchId: {}",
                    affectedRows, xid, branchId);

        } finally {
            closeResources(conn, pstmt, null);
        }
    }

    /**
     * 执行补偿操作（回滚）
     */
    public boolean compensate(String xid, Long branchId) throws SQLException {
        UndoLog undoLog = queryUndoLog(xid, branchId);
        if (undoLog == null) {
            logger.warn("No undo log found for compensation: xid={}, branchId={}", xid, branchId);
            return false;
        }

        try {
            // 标记undo log为补偿中状态
            markUndoLogStatus(undoLog.getId(), UNDO_LOG_STATUS_COMPENSATING);

            // 执行实际的补偿操作
            boolean success = executeCompensation(undoLog);

            // 更新undo log状态
            markUndoLogStatus(undoLog.getId(),
                    success ? UNDO_LOG_STATUS_COMPENSATED : UNDO_LOG_STATUS_NORMAL);

            return success;

        } catch (Exception e) {
            logger.error("Compensation failed for xid: {}, branchId: {}", xid, branchId, e);
            markUndoLogStatus(undoLog.getId(), UNDO_LOG_STATUS_NORMAL);
            return false;
        }
    }

    /**
     * 执行具体的补偿操作
     */
    private boolean executeCompensation(UndoLog undoLog) throws SQLException {
        // 这里需要根据beforeImage生成反向SQL并执行
        // 例如：如果是INSERT操作，补偿就是DELETE
        // 如果是UPDATE操作，补偿就是用beforeImage数据恢复
        // 如果是DELETE操作，补偿就是INSERT

        // 简化实现，实际需要复杂的SQL生成逻辑
        logger.info("Executing compensation for table: {}, sql: {}",
                undoLog.getTableName(), undoLog.getSqlText());
        return true;
    }

    /**
     * 更新undo log状态
     */
    private void markUndoLogStatus(Long undoLogId, int status) throws SQLException {
        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = DataSourceManager.getConnection();
            String sql = "UPDATE undo_log SET log_status = ?, update_time = ? WHERE id = ?";

            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, status);
            pstmt.setTimestamp(2, new java.sql.Timestamp(System.currentTimeMillis()));
            pstmt.setLong(3, undoLogId);

            pstmt.executeUpdate();

        } finally {
            closeResources(conn, pstmt, null);
        }
    }

    /**
     * 资源关闭工具方法
     */
    private void closeResources(Connection conn, PreparedStatement pstmt, ResultSet rs) {
        try {
            if (rs != null) rs.close();
        } catch (SQLException e) {
            logger.warn("Failed to close ResultSet", e);
        }

        try {
            if (pstmt != null) pstmt.close();
        } catch (SQLException e) {
            logger.warn("Failed to close PreparedStatement", e);
        }

        try {
            if (conn != null && !conn.isClosed()) conn.close();
        } catch (SQLException e) {
            logger.warn("Failed to close Connection", e);
        }
    }

    /**
     * Undo Log 实体类
     */
    public static class UndoLog {
        private Long id;
        private String xid;
        private Long branchId;
        private String tableName;
        private String beforeImage;
        private String afterImage;
        private String sqlText;
        private String parameters;
        private Integer logStatus;
        private Date createTime;
        private Date updateTime;

        // Getter和Setter方法
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getXid() { return xid; }
        public void setXid(String xid) { this.xid = xid; }

        public Long getBranchId() { return branchId; }
        public void setBranchId(Long branchId) { this.branchId = branchId; }

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }

        public String getBeforeImage() { return beforeImage; }
        public void setBeforeImage(String beforeImage) { this.beforeImage = beforeImage; }

        public String getAfterImage() { return afterImage; }
        public void setAfterImage(String afterImage) { this.afterImage = afterImage; }

        public String getSqlText() { return sqlText; }
        public void setSqlText(String sqlText) { this.sqlText = sqlText; }

        public String getParameters() { return parameters; }
        public void setParameters(String parameters) { this.parameters = parameters; }

        public Integer getLogStatus() { return logStatus; }
        public void setLogStatus(Integer logStatus) { this.logStatus = logStatus; }

        public Date getCreateTime() { return createTime; }
        public void setCreateTime(Date createTime) { this.createTime = createTime; }

        public Date getUpdateTime() { return updateTime; }
        public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
    }
}