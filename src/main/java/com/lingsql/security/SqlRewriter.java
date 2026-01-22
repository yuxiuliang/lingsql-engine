package com.lingsql.security;

import com.lingsql.config.LingSqlProperties;
import com.lingsql.datasource.model.TenantDbConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import org.springframework.stereotype.Component;

/**
 * SQL 强制改写器：注入 LIMIT 与 tenant_id 条件。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlRewriter {

    private final LingSqlProperties properties;

    /**
     * 强制改写 SQL，包含 LIMIT 与逻辑隔离条件。
     *
     * @param sql      原始 SQL
     * @param config   租户配置
     * @param tenantId 租户标识
     * @return 改写后的 SQL
     * @author 余炘洋
     * @date 2026-01-22
     */
    public String rewrite(String sql, TenantDbConfig config, String tenantId) {
        try {
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            PlainSelect plainSelect = select.getPlainSelect();
            if (plainSelect == null) {
                return sql;
            }

            injectLimit(plainSelect);

            if (config.isLogicalIsolation()) {
                injectTenantCondition(plainSelect, config.resolveLogicColumn(), tenantId);
            }

            return select.toString();
        } catch (Exception e) {
            log.warn("SQL 改写失败，返回原 SQL: {}", e.getMessage());
            return sql;
        }
    }

    /**
     * 注入 LIMIT 限制。
     *
     * @param plainSelect 解析后的查询体
     * @author 余炘洋
     * @date 2026-01-22
     */
    private void injectLimit(PlainSelect plainSelect) {
        int limitValue = properties.getSecurity().getDefaultLimit();
        Limit limit = plainSelect.getLimit();
        if (limit == null) {
            limit = new Limit();
            limit.setRowCount(new LongValue(limitValue));
            plainSelect.setLimit(limit);
            return;
        }
        if (limit.getRowCount() != null) {
            try {
                long current = Long.parseLong(limit.getRowCount().toString());
                if (current > limitValue) {
                    limit.setRowCount(new LongValue(limitValue));
                }
            } catch (NumberFormatException ex) {
                limit.setRowCount(new LongValue(limitValue));
            }
        }
    }

    /**
     * 注入租户隔离条件。
     *
     * @param plainSelect 查询体
     * @param columnName  隔离字段名
     * @param tenantId    租户标识
     * @author 余炘洋
     * @date 2026-01-22
     */
    private void injectTenantCondition(PlainSelect plainSelect, String columnName, String tenantId) {
        if (containsColumn(plainSelect.getWhere(), columnName)) {
            return;
        }
        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(new Column(columnName));
        equalsTo.setRightExpression(new StringValue(tenantId));
        Expression where = plainSelect.getWhere();
        if (where == null) {
            plainSelect.setWhere(equalsTo);
        } else {
            plainSelect.setWhere(new AndExpression(where, equalsTo));
        }
    }

    /**
     * 判断条件中是否已包含指定字段。
     *
     * @param expression 条件表达式
     * @param columnName 字段名
     * @return 是否包含
     * @author 余炘洋
     * @date 2026-01-22
     */
    private boolean containsColumn(Expression expression, String columnName) {
        if (expression == null) {
            return false;
        }
        if (columnName == null || columnName.isBlank()) {
            return false;
        }
        ColumnDetectVisitor visitor = new ColumnDetectVisitor(columnName);
        expression.accept(visitor);
        return visitor.isFound();
    }

    /**
     * 字段检测访问器。
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
         * 访问列节点以检测字段。
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
