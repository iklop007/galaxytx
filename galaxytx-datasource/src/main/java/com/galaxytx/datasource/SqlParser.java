package com.galaxytx.datasource;

import com.galaxytx.datasource.model.ParsedSql;
import com.galaxytx.datasource.model.SqlType;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL解析器
 * 用于解析SQL语句，提取表名、操作类型等信息
 */
public class SqlParser {
    private static final Logger logger = LoggerFactory.getLogger(SqlParser.class);

    // 简单的SQL类型匹配正则表达式（用于快速判断）
    private static final Pattern SELECT_PATTERN = Pattern.compile("^\\s*SELECT", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSERT_PATTERN = Pattern.compile("^\\s*INSERT", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE_PATTERN = Pattern.compile("^\\s*UPDATE", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_PATTERN = Pattern.compile("^\\s*DELETE", Pattern.CASE_INSENSITIVE);

    /**
     * 解析SQL语句
     */
    public ParsedSql parse(String sql) throws SQLException {
        if (sql == null || sql.trim().isEmpty()) {
            throw new SQLException("SQL statement is empty");
        }

        try {
            // 使用JSQLParser进行解析
            Statement statement = CCJSqlParserUtil.parse(sql);
            return parseWithJSQLParser(statement, sql);

        } catch (JSQLParserException e) {
            logger.warn("JSQLParser failed to parse SQL: {}, fallback to simple parser", sql, e);
            // 如果JSQLParser解析失败，使用简单的正则表达式解析
            return parseWithSimpleParser(sql);
        }
    }

    /**
     * 使用JSQLParser进行详细解析
     */
    private ParsedSql parseWithJSQLParser(Statement statement, String originalSql) throws SQLException {
        ParsedSql parsedSql = new ParsedSql();
        parsedSql.setOriginalSql(originalSql);

        if (statement instanceof Select) {
            parsedSql.setSqlType(SqlType.SELECT);
            Select select = (Select) statement;
            TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
            List<String> tableList = tablesNamesFinder.getTableList(select);
            if (!tableList.isEmpty()) {
                parsedSql.setTableName(tableList.get(0)); // 通常第一个表是主表
            }

        } else if (statement instanceof Insert) {
            parsedSql.setSqlType(SqlType.INSERT);
            Insert insert = (Insert) statement;
            Table table = insert.getTable();
            if (table != null) {
                parsedSql.setTableName(table.getName());
            }
            // 解析列信息
            if (insert.getColumns() != null) {
                parsedSql.setColumns(insert.getColumns());
            }

        } else if (statement instanceof Update) {
            parsedSql.setSqlType(SqlType.UPDATE);
            Update update = (Update) statement;
            if (update.getTable() != null) {
                parsedSql.setTableName(update.getTable().getName());
            }
            // 解析where条件
            Expression where = update.getWhere();
            if (where != null) {
                parsedSql.setWhereExpression(where.toString());
            }

        } else if (statement instanceof Delete) {
            parsedSql.setSqlType(SqlType.DELETE);
            Delete delete = (Delete) statement;
            if (delete.getTable() != null) {
                parsedSql.setTableName(delete.getTable().getName());
            }
            // 解析where条件
            Expression where = delete.getWhere();
            if (where != null) {
                parsedSql.setWhereExpression(where.toString());
            }

        } else {
            throw new SQLException("Unsupported SQL type: " + statement.getClass().getSimpleName());
        }

        return parsedSql;
    }

    /**
     * 使用简单解析器进行基本解析（fallback方案）
     */
    private ParsedSql parseWithSimpleParser(String sql) throws SQLException {
        ParsedSql parsedSql = new ParsedSql();
        parsedSql.setOriginalSql(sql);

        // 判断SQL类型
        if (SELECT_PATTERN.matcher(sql).find()) {
            parsedSql.setSqlType(SqlType.SELECT);
            parsedSql.setTableName(extractTableNameFromSelect(sql));

        } else if (INSERT_PATTERN.matcher(sql).find()) {
            parsedSql.setSqlType(SqlType.INSERT);
            parsedSql.setTableName(extractTableNameFromInsert(sql));

        } else if (UPDATE_PATTERN.matcher(sql).find()) {
            parsedSql.setSqlType(SqlType.UPDATE);
            parsedSql.setTableName(extractTableNameFromUpdate(sql));

        } else if (DELETE_PATTERN.matcher(sql).find()) {
            parsedSql.setSqlType(SqlType.DELETE);
            parsedSql.setTableName(extractTableNameFromDelete(sql));

        } else {
            throw new SQLException("Unsupported SQL type: " + sql);
        }

        return parsedSql;
    }

    /**
     * 从SELECT语句中提取表名
     */
    private String extractTableNameFromSelect(String sql) {
        // 简单的正则表达式匹配，实际需要更复杂的处理
        Pattern pattern = Pattern.compile("FROM\\s+([\\w_]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 从INSERT语句中提取表名
     */
    private String extractTableNameFromInsert(String sql) {
        Pattern pattern = Pattern.compile("INTO\\s+([\\w_]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 从UPDATE语句中提取表名
     */
    private String extractTableNameFromUpdate(String sql) {
        Pattern pattern = Pattern.compile("UPDATE\\s+([\\w_]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 从DELETE语句中提取表名
     */
    private String extractTableNameFromDelete(String sql) {
        Pattern pattern = Pattern.compile("DELETE\\s+FROM\\s+([\\w_]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // 尝试另一种格式: DELETE FROM table_name
        pattern = Pattern.compile("FROM\\s+([\\w_]+)", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(sql);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * 检查SQL是否支持分布式事务（只有DML语句支持）
     */
    public boolean isSupportedSql(String sql) {
        if (sql == null) return false;

        String trimmedSql = sql.trim().toUpperCase();
        return trimmedSql.startsWith("INSERT") ||
                trimmedSql.startsWith("UPDATE") ||
                trimmedSql.startsWith("DELETE");
    }

    /**
     * 检查SQL是否为查询语句
     */
    public boolean isSelectSql(String sql) {
        if (sql == null) return false;
        return SELECT_PATTERN.matcher(sql).find();
    }

    /**
     * 生成查询前镜像的SQL
     */
    public String buildSelectSqlForBeforeImage(ParsedSql parsedSql) {
        switch (parsedSql.getSqlType()) {
            case UPDATE:
            case DELETE:
                // 对于UPDATE和DELETE，需要查询修改前的数据
                return String.format("SELECT * FROM %s WHERE %s",
                        parsedSql.getTableName(),
                        parsedSql.getWhereExpression());

            case INSERT:
                // 对于INSERT，通常不需要前镜像，因为之前没有数据
                return null;

            default:
                return null;
        }
    }

    /**
     * 生成查询后镜像的SQL
     */
    public String buildSelectSqlForAfterImage(ParsedSql parsedSql, Object[] primaryKeys) {
        if (primaryKeys == null || primaryKeys.length == 0) {
            return null;
        }

        StringBuilder whereClause = new StringBuilder();
        for (int i = 0; i < primaryKeys.length; i++) {
            if (i > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append("id = ?"); // 假设主键字段名为id
        }

        return String.format("SELECT * FROM %s WHERE %s",
                parsedSql.getTableName(), whereClause.toString());
    }
}