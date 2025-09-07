package com.galaxytx.datasource.model;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 表记录信息容器（用于前后镜像数据存储）
 * 支持灵活的数据结构和多种访问方式
 *
 * @author 刘志成
 * @date 2023/07/05
 */
public class TableRecords {
    private String tableName;
    private List<RowData> rows;
    private String[] columnNames;
    private Map<String, Integer> columnIndexMap;

    public TableRecords(String tableName) {
        this.tableName = tableName;
        this.rows = new ArrayList<>();
    }

    /**
     * 从ResultSet构建表记录（完整版本）
     */
    public static TableRecords buildRecords(String tableName, ResultSet resultSet) throws SQLException {
        return buildRecords(tableName, resultSet, true, null);
    }

    /**
     * 从ResultSet构建表记录（灵活版本）
     * @param tableName 表名
     * @param resultSet 结果集
     * @param includeAllColumns 是否包含所有列
     * @param specificColumns 指定要包含的列（如果includeAllColumns为false时生效）
     */
    public static TableRecords buildRecords(String tableName, ResultSet resultSet,
                                            boolean includeAllColumns, List<String> specificColumns) throws SQLException {
        TableRecords records = new TableRecords(tableName);
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();

        // 初始化列信息
        records.initializeColumnInfo(metaData, includeAllColumns, specificColumns);

        // 处理每一行数据
        while (resultSet.next()) {
            RowData rowData = new RowData();
            Map<String, Object> columnValueMap = new HashMap<>();
            Object[] values = new Object[records.columnNames.length];

            for (int i = 0; i < records.columnNames.length; i++) {
                String columnName = records.columnNames[i];
                Object value = resultSet.getObject(columnName);

                values[i] = value;
                columnValueMap.put(columnName, value);
            }

            rowData.setValues(values);
            rowData.setColumnNames(records.columnNames);
            rowData.setColumnValueMap(columnValueMap);
            rowData.setMetaData(metaData);

            records.addRow(rowData);
        }

        return records;
    }

    /**
     * 从ResultSet构建表记录（只包含指定列）
     */
    public static TableRecords buildRecordsWithColumns(String tableName, ResultSet resultSet,
                                                       String... columns) throws SQLException {
        List<String> columnList = List.of(columns);
        return buildRecords(tableName, resultSet, false, columnList);
    }

    /**
     * 从ResultSet构建表记录（排除指定列）
     */
    public static TableRecords buildRecordsExcludeColumns(String tableName, ResultSet resultSet,
                                                          String... excludeColumns) throws SQLException {
        try {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            List<String> includedColumns = new ArrayList<>();
            List<String> excludeList = List.of(excludeColumns);

            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnName(i);
                if (!excludeList.contains(columnName.toLowerCase()) &&
                        !excludeList.contains(columnName.toUpperCase())) {
                    includedColumns.add(columnName);
                }
            }

            return buildRecords(tableName, resultSet, false, includedColumns);
        } catch (SQLException e) {
            // 如果排除列失败，回退到包含所有列
            return buildRecords(tableName, resultSet, true, null);
        }
    }

    /**
     * 初始化列信息
     */
    private void initializeColumnInfo(ResultSetMetaData metaData,
                                      boolean includeAllColumns, List<String> specificColumns) throws SQLException {
        int columnCount = metaData.getColumnCount();

        if (includeAllColumns) {
            // 包含所有列
            this.columnNames = new String[columnCount];
            this.columnIndexMap = new HashMap<>();

            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnName(i);
                this.columnNames[i - 1] = columnName;
                this.columnIndexMap.put(columnName.toLowerCase(), i - 1);
                this.columnIndexMap.put(columnName.toUpperCase(), i - 1);
            }
        } else if (specificColumns != null && !specificColumns.isEmpty()) {
            // 包含指定列
            this.columnNames = specificColumns.toArray(new String[0]);
            this.columnIndexMap = new HashMap<>();

            for (int i = 0; i < specificColumns.size(); i++) {
                String columnName = specificColumns.get(i);
                this.columnIndexMap.put(columnName.toLowerCase(), i);
                this.columnIndexMap.put(columnName.toUpperCase(), i);
            }
        } else {
            throw new IllegalArgumentException("Either include all columns or provide specific columns");
        }
    }

    /**
     * 获取指定列的值列表
     */
    public List<Object> getColumnValues(String columnName) {
        List<Object> values = new ArrayList<>();
        Integer columnIndex = columnIndexMap.get(columnName.toLowerCase());

        if (columnIndex != null) {
            for (RowData row : rows) {
                values.add(row.getValues()[columnIndex]);
            }
        }

        return values;
    }

    /**
     * 获取主键值列表（假设主键列名为id）
     */
    public List<Object> getPrimaryKeyValues() {
        return getColumnValues("id");
    }

    /**
     * 获取指定列的唯一值
     */
    public List<Object> getDistinctColumnValues(String columnName) {
        List<Object> values = new ArrayList<>();
        Integer columnIndex = columnIndexMap.get(columnName.toLowerCase());

        if (columnIndex != null) {
            for (RowData row : rows) {
                Object value = row.getValues()[columnIndex];
                if (!values.contains(value)) {
                    values.add(value);
                }
            }
        }

        return values;
    }

    /**
     * 根据列名和值过滤行
     */
    public TableRecords filter(String columnName, Object value) {
        TableRecords filtered = new TableRecords(this.tableName);
        filtered.columnNames = this.columnNames;
        filtered.columnIndexMap = this.columnIndexMap;

        Integer columnIndex = columnIndexMap.get(columnName.toLowerCase());
        if (columnIndex != null) {
            for (RowData row : rows) {
                if (value.equals(row.getValues()[columnIndex])) {
                    filtered.addRow(row);
                }
            }
        }

        return filtered;
    }

    /**
     * 转换为Map列表（便于JSON序列化）
     */
    public List<Map<String, Object>> toMapList() {
        List<Map<String, Object>> result = new ArrayList<>();

        for (RowData row : rows) {
            Map<String, Object> map = new HashMap<>();
            for (int i = 0; i < columnNames.length; i++) {
                map.put(columnNames[i], row.getValues()[i]);
            }
            result.add(map);
        }

        return result;
    }

    /**
     * 获取第一行数据
     */
    public RowData getFirstRow() {
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 获取最后一行数据
     */
    public RowData getLastRow() {
        return rows.isEmpty() ? null : rows.get(rows.size() - 1);
    }

    /**
     * 获取行数
     */
    public int getRowCount() {
        return rows.size();
    }

    /**
     * 获取列数
     */
    public int getColumnCount() {
        return columnNames != null ? columnNames.length : 0;
    }

    /**
     * 获取列名数组
     */
    public String[] getColumnNames() {
        return columnNames;
    }

    public void addRow(RowData rowData) {
        rows.add(rowData);
    }

    public List<RowData> getRows() {
        return rows;
    }

    public String getTableName() {
        return tableName;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /**
     * 行数据容器
     */
    public static class RowData {
        private Object[] values;
        private String[] columnNames;
        private Map<String, Object> columnValueMap;
        private ResultSetMetaData metaData;

        public Object[] getValues() { return values; }
        public void setValues(Object[] values) { this.values = values; }

        public String[] getColumnNames() { return columnNames; }
        public void setColumnNames(String[] columnNames) { this.columnNames = columnNames; }

        public Map<String, Object> getColumnValueMap() { return columnValueMap; }
        public void setColumnValueMap(Map<String, Object> columnValueMap) {
            this.columnValueMap = columnValueMap;
        }

        public ResultSetMetaData getMetaData() { return metaData; }
        public void setMetaData(ResultSetMetaData metaData) { this.metaData = metaData; }

        /**
         * 获取指定列的值
         */
        public Object getValue(String columnName) {
            if (columnValueMap != null) {
                return columnValueMap.get(columnName);
            }

            // 回退到数组查找
            if (columnNames != null) {
                for (int i = 0; i < columnNames.length; i++) {
                    if (columnNames[i].equalsIgnoreCase(columnName)) {
                        return values[i];
                    }
                }
            }

            return null;
        }

        /**
         * 获取主键值（假设主键列名为id）
         */
        public Object getPrimaryKeyValue() {
            return getValue("id");
        }

        /**
         * 检查是否包含指定列
         */
        public boolean containsColumn(String columnName) {
            if (columnValueMap != null) {
                return columnValueMap.containsKey(columnName);
            }

            if (columnNames != null) {
                for (String name : columnNames) {
                    if (name.equalsIgnoreCase(columnName)) {
                        return true;
                    }
                }
            }

            return false;
        }

        /**
         * 转换为Map（便于JSON序列化）
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();

            if (columnValueMap != null) {
                map.putAll(columnValueMap);
            } else if (columnNames != null && values != null) {
                for (int i = 0; i < columnNames.length; i++) {
                    map.put(columnNames[i], values[i]);
                }
            }

            return map;
        }

        @Override
        public String toString() {
            return toMap().toString();
        }
    }

    /**
     * 获取列索引映射（用于快速查找）
     */
    public Map<String, Integer> getColumnIndexMap() {
        return columnIndexMap;
    }

    /**
     * 清空所有数据
     */
    public void clear() {
        rows.clear();
        columnNames = null;
        columnIndexMap = null;
    }

    @Override
    public String toString() {
        return "TableRecords{" +
                "tableName='" + tableName + '\'' +
                ", rowCount=" + getRowCount() +
                ", columnCount=" + getColumnCount() +
                '}';
    }
}