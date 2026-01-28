package com.lingsql.security;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.datasource.model.TenantDbConfig;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.util.TablesNamesFinder;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * SQL 安全校验器，负责白名单与敏感字段验证。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Component
@RequiredArgsConstructor
public class SqlSecurityChecker {

    private final LingSqlProperties properties;

    /**
     * 校验 SQL 是否安全。
     *
     * @param statement SQL 语句对象
     * @param config 租户配置
     * @author 余炘洋
     * @date 2026-01-22
     */
    public void check(String sql, TenantDbConfig config) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select select)) {
                throw new BizException(ErrorCode.SQL_INVALID, "安全警告：禁止执行非查询类 SQL 语句！");
            }

            PlainSelect plainSelect = select.getPlainSelect();
            if (plainSelect == null) {
                throw new BizException(ErrorCode.SQL_INVALID, "安全警告：仅支持单条 SELECT 查询");
            }
            validateLimit(plainSelect);
            validateTenantCondition(config, plainSelect.getWhere());
            validateTables(statement, config);
            validateColumns(plainSelect, config);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SQL_INVALID, "SQL 解析失败或包含非法指令: " + e.getMessage(), e);
        }
    }

    /**
     * 校验 LIMIT 合规性。
     *
     * @param plainSelect 查询体
     * @author 余炘洋
     * @date 2026-01-22
     */
    private void validateLimit(PlainSelect plainSelect) {
        if (plainSelect.getLimit() == null || plainSelect.getLimit().getRowCount() == null) {
            throw new BizException(ErrorCode.SQL_INVALID, "安全警告：SQL 必须包含 LIMIT " + properties.getSecurity().getDefaultLimit());
        }
        try {
            long limit = Long.parseLong(plainSelect.getLimit().getRowCount().toString());
            if (limit > properties.getSecurity().getDefaultLimit()) {
                throw new BizException(ErrorCode.SQL_INVALID, "安全警告：LIMIT 超过允许范围");
            }
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.SQL_INVALID, "安全警告：LIMIT 格式非法");
        }
    }

    /**
     * 校验逻辑隔离条件是否存在。
     *
     * @param config 租户配置
     * @param where  WHERE 条件
     * @author 余炘洋
     * @date 2026-01-22
     */
    private void validateTenantCondition(TenantDbConfig config, Expression where) {
        if (!config.isLogicalIsolation()) {
            return;
        }
        if (where == null) {
            throw new BizException(ErrorCode.SECURITY_VIOLATION, "安全警告：逻辑隔离模式缺少 tenant 条件");
        }
        ColumnDetectVisitor visitor = new ColumnDetectVisitor(config.resolveLogicColumn());
        where.accept(visitor);
        if (!visitor.isFound()) {
            throw new BizException(ErrorCode.SECURITY_VIOLATION, "安全警告：逻辑隔离模式缺少 tenant 条件");
        }
    }

    /**
     * 校验表白名单。
     *
     * @param sql    SQL 文本
     * @param config 租户配置
     * @author 余炘洋
     * @date 2026-01-22
     */
    private void validateTables(Statement statement, TenantDbConfig config) {
        Set<String> allowed = parseCsv(config.getWhiteListTables());
        if (allowed.isEmpty()) {
            return;
        }
        TablesNamesFinder finder = new TablesNamesFinder();
        Set<String> tables = finder.getTables(statement);
        for (String table : tables) {
            if (!allowed.contains(table.toLowerCase())) {
                throw new BizException(ErrorCode.SECURITY_VIOLATION, "安全警告：访问未授权表 " + table);
            }
        }
    }

    /**
     * 校验字段白名单与敏感字段。
     *
     * @param plainSelect 查询体
     * @param config      租户配置
     * @author 余炘洋
     * @date 2026-01-22
     */
    private void validateColumns(PlainSelect plainSelect, TenantDbConfig config) {
        Set<String> whiteColumns = parseCsv(config.getWhiteListColumns());
        Set<String> sensitiveColumns = parseCsv(config.getSensitiveColumns());

        ColumnCollector collector = new ColumnCollector();
        for (SelectItem<?> item : plainSelect.getSelectItems()) {
            Expression expression = item.getExpression();
            if (expression instanceof AllColumns || expression instanceof AllTableColumns) {
                if (!sensitiveColumns.isEmpty()) {
                    throw new BizException(ErrorCode.SECURITY_VIOLATION, "安全警告：禁止使用 * 查询敏感字段");
                }
                if (!whiteColumns.isEmpty()) {
                    throw new BizException(ErrorCode.SECURITY_VIOLATION, "安全警告：字段白名单开启时禁止使用 *");
                }
                continue;
            }
            if (expression != null) {
                expression.accept(collector);
            }
        }

        if (plainSelect.getWhere() != null) {
            plainSelect.getWhere().accept(collector);
        }
        if (plainSelect.getGroupBy() != null && plainSelect.getGroupBy().getGroupByExpressionList() != null) {
            for (Object exprObj : plainSelect.getGroupBy().getGroupByExpressionList()) {
                if (exprObj instanceof Expression expr) {
                    expr.accept(collector);
                }
            }
        }
        if (plainSelect.getOrderByElements() != null) {
            plainSelect.getOrderByElements().forEach(element -> {
                if (element.getExpression() != null) {
                    element.getExpression().accept(collector);
                }
            });
        }

        for (String column : collector.getColumns()) {
            if (!whiteColumns.isEmpty() && !whiteColumns.contains(column)) {
                throw new BizException(ErrorCode.SECURITY_VIOLATION, "安全警告：字段未在白名单内 " + column);
            }
            if (sensitiveColumns.contains(column)) {
                throw new BizException(ErrorCode.SECURITY_VIOLATION, "安全警告：访问敏感字段 " + column);
            }
        }
    }

    /**
     * 解析逗号分隔字符串为集合。
     *
     * @param text 原始文本
     * @return 集合
     * @author 余炘洋
     * @date 2026-01-22
     */
    private Set<String> parseCsv(String text) {
        Set<String> set = new HashSet<>();
        if (!StringUtils.hasText(text)) {
            return set;
        }
        String[] items = text.split(",");
        for (String item : items) {
            String value = item.trim();
            if (!value.isEmpty()) {
                set.add(value.toLowerCase());
            }
        }
        return set;
    }

    /**
     * 列收集访问器。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private static class ColumnCollector extends ExpressionDeParser {
        private final Set<String> columns = new HashSet<>();

        /**
         * 访问列节点，收集字段名。
         *
         * @param column 列
         * @author 余炘洋
         * @date 2026-01-22
         */
        @Override
        public void visit(Column column) {
            if (column.getColumnName() != null) {
                columns.add(column.getColumnName().toLowerCase());
                if (column.getTable() != null && StringUtils.hasText(column.getTable().getName())) {
                    columns.add((column.getTable().getName() + "." + column.getColumnName()).toLowerCase());
                }
            }
            super.visit(column);
        }

        /**
         * 访问表达式列表。
         *
         * @param expressionList 表达式列表
         * @author 余炘洋
         * @date 2026-01-22
         */
        @Override
        public void visit(ExpressionList<?> expressionList) {
            for (Expression expression : expressionList) {
                expression.accept(this);
            }
        }

        /**
         * 获取收集的字段集合。
         *
         * @return 字段集合
         * @author 余炘洋
         * @date 2026-01-22
         */
        public Set<String> getColumns() {
            return columns;
        }
    }

    /**
     * tenant 条件检测访问器。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private static class ColumnDetectVisitor extends ExpressionDeParser {
        private final String columnName;
        private boolean found = false;

        /**
         * 构造函数。
         *
         * @param columnName 字段名
         * @author 余炘洋
         * @date 2026-01-22
         */
        private ColumnDetectVisitor(String columnName) {
            this.columnName = columnName == null ? "" : columnName.toLowerCase();
        }

        /**
         * 访问列节点。
         *
         * @param column 列
         * @author 余炘洋
         * @date 2026-01-22
         */
        @Override
        public void visit(Column column) {
            if (column.getColumnName() != null && column.getColumnName().equalsIgnoreCase(columnName)) {
                found = true;
            }
            super.visit(column);
        }

        /**
         * 是否命中字段。
         *
         * @return 是否命中
         * @author 余炘洋
         * @date 2026-01-22
         */
        private boolean isFound() {
            return found;
        }
    }
}
