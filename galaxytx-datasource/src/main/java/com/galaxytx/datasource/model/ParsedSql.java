package com.galaxytx.datasource.model;

import net.sf.jsqlparser.schema.Column;

import java.util.List;

/**
 * 解析后的SQL信息
 */
public class ParsedSql {
    private String originalSql;
    private SqlType sqlType;
    private String tableName;
    private String whereExpression;
    private List<Column> columns;
    private Object[] parameters;

    public ParsedSql() {}

    // Getters and Setters
    public String getOriginalSql() { return originalSql; }
    public void setOriginalSql(String originalSql) { this.originalSql = originalSql; }

    public SqlType getSqlType() { return sqlType; }
    public void setSqlType(SqlType sqlType) { this.sqlType = sqlType; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getWhereExpression() { return whereExpression; }
    public void setWhereExpression(String whereExpression) { this.whereExpression = whereExpression; }

    public List<Column> getColumns() { return columns; }
    public void setColumns(List<Column> columns) { this.columns = columns; }

    public Object[] getParameters() { return parameters; }
    public void setParameters(Object[] parameters) { this.parameters = parameters; }

    @Override
    public String toString() {
        return "ParsedSql{" +
                "sqlType=" + sqlType +
                ", tableName='" + tableName + '\'' +
                ", whereExpression='" + whereExpression + '\'' +
                '}';
    }
}