package com.galaxytx.datasource;

import com.galaxytx.core.common.TransactionContext;
import com.galaxytx.datasource.model.ParsedSql;
import com.galaxytx.datasource.model.SqlType;
import com.galaxytx.datasource.model.TableRecords;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * PreparedStatement 代理类
 * 用于拦截SQL执行，在分布式事务中生成前后镜像和undo log
 *
 * @author: 刘志成
 * @date: 2018/05/07
 */
public class PreparedStatementProxy implements PreparedStatement {
    private final PreparedStatement targetStatement;
    private final String originalSql;
    private final String resourceGroupId;
    private final SqlParser sqlParser;
    private final UndoLogManager undoLogManager;

    private Object[] parameters;
    private Map<Integer, Object> parameterMap;
    private boolean isBatch = false;
    private Map<Integer, Object[]> batchParameters;

    public PreparedStatementProxy(PreparedStatement targetStatement, String originalSql,
                                  String resourceGroupId, SqlParser sqlParser) {
        this.targetStatement = targetStatement;
        this.originalSql = originalSql;
        this.resourceGroupId = resourceGroupId;
        this.sqlParser = sqlParser;
        this.undoLogManager = new UndoLogManager();
        this.parameterMap = new HashMap<>();
        this.batchParameters = new HashMap<>();
    }

    @Override
    public int executeUpdate() throws SQLException {
        if (!TransactionContext.isInGlobalTransaction() || !sqlParser.isSupportedSql(originalSql)) {
            return targetStatement.executeUpdate();
        }

        try {
            // 解析SQL
            ParsedSql parsedSql = sqlParser.parse(originalSql);
            parsedSql.setParameters(getParametersArray());

            // 查询前镜像
            TableRecords beforeImage = queryBeforeImage(parsedSql);

            // 执行更新
            int result = targetStatement.executeUpdate();

            // 查询后镜像
            TableRecords afterImage = queryAfterImage(parsedSql, beforeImage);

            // 生成并保存undo log
            if (beforeImage != null || afterImage != null) {
                undoLogManager.addUndoLog(
                        TransactionContext.getXid(),
                        TransactionContext.getBranchId(),
                        parsedSql.getTableName(),
                        beforeImage,
                        afterImage,
                        originalSql,
                        getParametersArray()
                );
            }

            return result;

        } catch (Exception e) {
            // 如果undo log处理失败，仍然继续执行，但记录警告
            if (e instanceof SQLException) {
                throw (SQLException) e;
            }
            throw new SQLException("Failed to process distributed transaction logic", e);
        }
    }

    @Override
    public boolean execute() throws SQLException {
        if (!TransactionContext.isInGlobalTransaction() || !sqlParser.isSupportedSql(originalSql)) {
            return targetStatement.execute();
        }

        try {
            // 解析SQL
            ParsedSql parsedSql = sqlParser.parse(originalSql);
            parsedSql.setParameters(getParametersArray());

            // 查询前镜像（对于可能修改数据的execute操作）
            TableRecords beforeImage = null;
            if (sqlParser.isSupportedSql(originalSql)) {
                beforeImage = queryBeforeImage(parsedSql);
            }

            // 执行SQL
            boolean result = targetStatement.execute();

            // 如果是更新操作，查询后镜像
            TableRecords afterImage = null;
            if (sqlParser.isSupportedSql(originalSql) && targetStatement.getUpdateCount() > 0) {
                afterImage = queryAfterImage(parsedSql, beforeImage);
            }

            // 生成并保存undo log
            if (beforeImage != null || afterImage != null) {
                undoLogManager.addUndoLog(
                        TransactionContext.getXid(),
                        TransactionContext.getBranchId(),
                        parsedSql.getTableName(),
                        beforeImage,
                        afterImage,
                        originalSql,
                        getParametersArray()
                );
            }

            return result;

        } catch (Exception e) {
            if (e instanceof SQLException) {
                throw (SQLException) e;
            }
            throw new SQLException("Failed to process distributed transaction logic", e);
        }
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        // 查询操作不需要处理分布式事务逻辑
        return targetStatement.executeQuery();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        if (!TransactionContext.isInGlobalTransaction()) {
            return targetStatement.executeBatch();
        }

        this.isBatch = true;
        int[] results = targetStatement.executeBatch();

        // 批量处理每个SQL的undo log
        processBatchUndoLogs();

        return results;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return null;
    }

    /**
     * 处理批量操作的undo log
     */
    private void processBatchUndoLogs() throws SQLException {
        if (batchParameters.isEmpty()) {
            return;
        }

        try {
            ParsedSql parsedSql = sqlParser.parse(originalSql);

            for (Map.Entry<Integer, Object[]> entry : batchParameters.entrySet()) {
                int batchIndex = entry.getKey();
                Object[] params = entry.getValue();
                parsedSql.setParameters(params);

                // 查询前镜像
                TableRecords beforeImage = queryBeforeImage(parsedSql);

                // 重新设置参数并执行单条SQL来获取后镜像
                setParameters(targetStatement, params);
                targetStatement.executeUpdate();

                // 查询后镜像
                TableRecords afterImage = queryAfterImage(parsedSql, beforeImage);

                // 生成并保存undo log
                if (beforeImage != null || afterImage != null) {
                    undoLogManager.addUndoLog(
                            TransactionContext.getXid(),
                            TransactionContext.getBranchId(),
                            parsedSql.getTableName(),
                            beforeImage,
                            afterImage,
                            originalSql,
                            params
                    );
                }
            }

        } catch (Exception e) {
            throw new SQLException("Failed to process batch undo logs", e);
        } finally {
            // 清空批量参数
            batchParameters.clear();
            this.isBatch = false;
        }
    }

    /**
     * 查询前镜像数据
     */
    private TableRecords queryBeforeImage(ParsedSql parsedSql) throws SQLException {
        String selectSql = sqlParser.buildSelectSqlForBeforeImage(parsedSql);
        if (selectSql == null) {
            return null;
        }

        try (PreparedStatement stmt = targetStatement.getConnection().prepareStatement(selectSql)) {
            setParameters(stmt, parsedSql.getParameters());
            try (ResultSet rs = stmt.executeQuery()) {
                return TableRecords.buildRecords(parsedSql.getTableName(), rs);
            }
        }
    }

    /**
     * 查询后镜像数据
     */
    private TableRecords queryAfterImage(ParsedSql parsedSql, TableRecords beforeImage) throws SQLException {
        // 获取主键值（从beforeImage或通过其他方式）
        Object[] primaryKeys = extractPrimaryKeys(beforeImage, parsedSql);
        if (primaryKeys == null || primaryKeys.length == 0) {
            return null;
        }

        String selectSql = sqlParser.buildSelectSqlForAfterImage(parsedSql, primaryKeys);
        if (selectSql == null) {
            return null;
        }

        try (PreparedStatement stmt = targetStatement.getConnection().prepareStatement(selectSql)) {
            // 设置主键参数
            for (int i = 0; i < primaryKeys.length; i++) {
                stmt.setObject(i + 1, primaryKeys[i]);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                return TableRecords.buildRecords(parsedSql.getTableName(), rs);
            }
        }
    }

    /**
     * 提取主键值
     */
    private Object[] extractPrimaryKeys(TableRecords beforeImage, ParsedSql parsedSql) {
        if (beforeImage != null && !beforeImage.isEmpty()) {
            // 从beforeImage中提取主键
            return beforeImage.getPrimaryKeyValues().toArray();
        }

        // 对于INSERT操作，可能需要其他方式获取主键
        // 这里简化处理，实际需要更复杂的主键探测逻辑
        if (parsedSql.getSqlType() == SqlType.INSERT) {
            return new Object[]{parsedSql.getParameters()[0]};
        }
        if (parsedSql.getSqlType() == SqlType.UPDATE) {
            // 对于UPDATE操作，可能需要其他方式获取主键
            // 这里简化处理，实际需要更复杂主键探测逻辑
            return new Object[]{parsedSql.getParameters()[0]};
        }
        if (parsedSql.getSqlType() == SqlType.DELETE) {
            // 对于DELETE操作，可能需要其他方式获取主键
            // 这里简化处理，实际需要更复杂主键探测逻辑
            return new Object[]{parsedSql.getParameters()[0]};
        }

        return null;
    }

    /**
     * 设置PreparedStatement参数
     */
    private void setParameters(PreparedStatement stmt, Object[] parameters) throws SQLException {
        if (parameters != null) {
            for (int i = 0; i < parameters.length; i++) {
                stmt.setObject(i + 1, parameters[i]);
            }
        }
    }

    /**
     * 获取参数数组
     */
    private Object[] getParametersArray() {
        if (parameters != null) {
            return parameters;
        }

        // 从parameterMap构建参数数组
        if (!parameterMap.isEmpty()) {
            int maxIndex = parameterMap.keySet().stream().max(Integer::compareTo).orElse(0);
            Object[] params = new Object[maxIndex];
            for (Map.Entry<Integer, Object> entry : parameterMap.entrySet()) {
                params[entry.getKey() - 1] = entry.getValue();
            }
            return params;
        }

        return new Object[0];
    }

    // ========== PreparedStatement 参数设置方法 ==========

    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        targetStatement.setNull(parameterIndex, sqlType);
        parameterMap.put(parameterIndex, null);
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        targetStatement.setBoolean(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {
        targetStatement.setByte(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        targetStatement.setShort(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        targetStatement.setInt(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        targetStatement.setLong(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        targetStatement.setFloat(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        targetStatement.setDouble(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        targetStatement.setBigDecimal(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setString(int parameterIndex, String x) throws SQLException {
        targetStatement.setString(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        targetStatement.setBytes(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setDate(int parameterIndex, Date x) throws SQLException {
        targetStatement.setDate(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setTime(int parameterIndex, Time x) throws SQLException {
        targetStatement.setTime(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        targetStatement.setTimestamp(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        targetStatement.setObject(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        targetStatement.setObject(parameterIndex, x, targetSqlType);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        targetStatement.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        targetStatement.setAsciiStream(parameterIndex, x, length);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        targetStatement.setBinaryStream(parameterIndex, x, length);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        targetStatement.setCharacterStream(parameterIndex, reader, length);
        parameterMap.put(parameterIndex, reader);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        targetStatement.setAsciiStream(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        targetStatement.setBinaryStream(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        targetStatement.setCharacterStream(parameterIndex, reader);
        parameterMap.put(parameterIndex, reader);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        targetStatement.setNCharacterStream(parameterIndex, value);
        parameterMap.put(parameterIndex, value);
    }

    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        targetStatement.setClob(parameterIndex, reader);
        parameterMap.put(parameterIndex, reader);
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        targetStatement.setBlob(parameterIndex, inputStream);
        parameterMap.put(parameterIndex, inputStream);
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        targetStatement.setNClob(parameterIndex, reader);
        parameterMap.put(parameterIndex, reader);
    }

    // ========== 批量操作相关方法 ==========

    @Override
    public void addBatch() throws SQLException {
        targetStatement.addBatch();
        // 保存当前参数集用于后续的undo log处理
        int batchIndex = batchParameters.size() + 1;
        batchParameters.put(batchIndex, getParametersArray());
        // 清空当前参数，为下一批准备
        parameterMap.clear();
    }

    @Override
    public void clearBatch() throws SQLException {
        targetStatement.clearBatch();
        batchParameters.clear();
    }

    // ========== 其他委托方法 ==========

    @Override
    public void clearParameters() throws SQLException {
        targetStatement.clearParameters();
        parameterMap.clear();
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        return targetStatement.getResultSet();
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return targetStatement.getUpdateCount();
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return targetStatement.getMoreResults();
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return targetStatement.getMoreResults(current);
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return targetStatement.getGeneratedKeys();
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return targetStatement.executeUpdate(sql, autoGeneratedKeys);
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return targetStatement.executeUpdate(sql, columnIndexes);
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return targetStatement.executeUpdate(sql, columnNames);
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return targetStatement.execute(sql, autoGeneratedKeys);
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return targetStatement.execute(sql, columnIndexes);
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return targetStatement.execute(sql, columnNames);
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return targetStatement.getResultSetHoldability();
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        targetStatement.setMaxRows(max);
    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        targetStatement.setEscapeProcessing(enable);
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return targetStatement.getQueryTimeout();
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        targetStatement.setQueryTimeout(seconds);
    }

    @Override
    public void cancel() throws SQLException {
        targetStatement.cancel();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return targetStatement.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        targetStatement.clearWarnings();
    }

    @Override
    public void setCursorName(String name) throws SQLException {
        targetStatement.setCursorName(name);
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        return targetStatement.execute(sql);
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        targetStatement.setFetchSize(rows);
    }

    @Override
    public int getFetchSize() throws SQLException {
        return targetStatement.getFetchSize();
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return targetStatement.getResultSetConcurrency();
    }

    @Override
    public int getResultSetType() throws SQLException {
        return targetStatement.getResultSetType();
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        targetStatement.addBatch(sql);
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        targetStatement.setFetchDirection(direction);
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return targetStatement.getFetchDirection();
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        targetStatement.setMaxFieldSize(max);
    }

    @Override
    public int getMaxRows() throws SQLException {
        return targetStatement.getMaxRows();
    }

    // ========== 其他接口方法委托 ==========

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return targetStatement.getMetaData();
    }

    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        return targetStatement.getParameterMetaData();
    }

    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException {
        targetStatement.setArray(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        targetStatement.setAsciiStream(parameterIndex, x, length);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        targetStatement.setUnicodeStream(parameterIndex, x, length);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        targetStatement.setBinaryStream(parameterIndex, x, length);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        targetStatement.setBlob(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        targetStatement.setCharacterStream(parameterIndex, reader, length);
        parameterMap.put(parameterIndex, reader);
    }

    @Override
    public void setClob(int parameterIndex, Clob x) throws SQLException {
        targetStatement.setClob(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        targetStatement.setDate(parameterIndex, x, cal);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        targetStatement.setTime(parameterIndex, x, cal);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        targetStatement.setTimestamp(parameterIndex, x, cal);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {

    }

    @Override
    public void setURL(int parameterIndex, URL x) throws SQLException {
        targetStatement.setURL(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {
        targetStatement.setNString(parameterIndex, value);
        parameterMap.put(parameterIndex, value);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        targetStatement.setNCharacterStream(parameterIndex, value, length);
        parameterMap.put(parameterIndex, value);
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        targetStatement.setNClob(parameterIndex, value);
        parameterMap.put(parameterIndex, value);
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        targetStatement.setClob(parameterIndex, reader, length);
        parameterMap.put(parameterIndex, reader);
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        targetStatement.setBlob(parameterIndex, inputStream, length);
        parameterMap.put(parameterIndex, inputStream);
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        targetStatement.setNClob(parameterIndex, reader, length);
        parameterMap.put(parameterIndex, reader);
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        targetStatement.setSQLXML(parameterIndex, xmlObject);
        parameterMap.put(parameterIndex, xmlObject);
    }

    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        targetStatement.setRowId(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException {
        targetStatement.setRef(parameterIndex, x);
        parameterMap.put(parameterIndex, x);
    }

    // ========== 自动关闭相关方法 ==========

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        return targetStatement.executeQuery(sql);
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        return targetStatement.executeUpdate(sql);
    }

    @Override
    public void close() throws SQLException {
        targetStatement.close();
        parameterMap.clear();
        batchParameters.clear();
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return targetStatement.getMaxFieldSize();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return targetStatement.isClosed();
    }

    @Override
    public boolean isPoolable() throws SQLException {
        return targetStatement.isPoolable();
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        targetStatement.setPoolable(poolable);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return targetStatement.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return targetStatement.isWrapperFor(iface);
    }

    // ========== JDBC 4.1 新增方法 ==========

    @Override
    public void closeOnCompletion() throws SQLException {
        targetStatement.closeOnCompletion();
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return targetStatement.isCloseOnCompletion();
    }

    // ========== JDBC 4.2 新增方法 ==========

    @Override
    public long getLargeUpdateCount() throws SQLException {
        return targetStatement.getLargeUpdateCount();
    }

    @Override
    public void setLargeMaxRows(long max) throws SQLException {
        targetStatement.setLargeMaxRows(max);
    }

    @Override
    public long getLargeMaxRows() throws SQLException {
        return targetStatement.getLargeMaxRows();
    }

    @Override
    public long[] executeLargeBatch() throws SQLException {
        if (!TransactionContext.isInGlobalTransaction()) {
            return targetStatement.executeLargeBatch();
        }

        this.isBatch = true;
        long[] results = targetStatement.executeLargeBatch();
        processBatchUndoLogs();
        return results;
    }

    @Override
    public long executeLargeUpdate() throws SQLException {
        if (!TransactionContext.isInGlobalTransaction() || !sqlParser.isSupportedSql(originalSql)) {
            return targetStatement.executeLargeUpdate();
        }

        try {
            ParsedSql parsedSql = sqlParser.parse(originalSql);
            parsedSql.setParameters(getParametersArray());

            TableRecords beforeImage = queryBeforeImage(parsedSql);
            long result = targetStatement.executeLargeUpdate();
            TableRecords afterImage = queryAfterImage(parsedSql, beforeImage);

            if (beforeImage != null || afterImage != null) {
                undoLogManager.addUndoLog(
                        TransactionContext.getXid(),
                        TransactionContext.getBranchId(),
                        parsedSql.getTableName(),
                        beforeImage,
                        afterImage,
                        originalSql,
                        getParametersArray()
                );
            }

            return result;

        } catch (Exception e) {
            if (e instanceof SQLException) {
                throw (SQLException) e;
            }
            throw new SQLException("Failed to process distributed transaction logic", e);
        }
    }
}