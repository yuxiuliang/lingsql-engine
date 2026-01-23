package com.lingsql.security;

import com.lingsql.config.LingSqlProperties;
import com.lingsql.metadata.service.MetadataExtService;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.rbac.model.DataScopeRule;
import com.lingsql.rbac.model.RbacDataScopeType;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import net.sf.jsqlparser.expression.Alias;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final MetadataExtService metadataExtService;

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
                String isolationColumn = resolveIsolationColumn(plainSelect, config, tenantId);
                injectTenantCondition(plainSelect, isolationColumn, tenantId);
            }
            maskSensitiveColumns(plainSelect, config);
            injectDataScope(plainSelect);

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
        equalsTo.setLeftExpression(buildColumn(plainSelect, columnName));
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
     * 解析隔离字段名，表级配置优先。
     *
     * @param plainSelect 查询体
     * @param config      租户配置
     * @param tenantId    租户标识
     * @return 隔离字段
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String resolveIsolationColumn(PlainSelect plainSelect, TenantDbConfig config, String tenantId) {
        String defaultColumn = config.resolveLogicColumn();
        if (plainSelect.getFromItem() == null || plainSelect.getJoins() != null && !plainSelect.getJoins().isEmpty()) {
            return defaultColumn;
        }
        if (plainSelect.getFromItem() instanceof net.sf.jsqlparser.schema.Table table) {
            String tableName = table.getName();
            return metadataExtService.resolveIsolationField(tenantId, tableName).orElse(defaultColumn);
        }
        return defaultColumn;
    }

    /**
     * 构建带别名的列。
     *
     * @param plainSelect 查询体
     * @param columnName  字段名
     * @return 列对象
     * @author 余炘洋
     * @date 2026-01-23
     */
    private Column buildColumn(PlainSelect plainSelect, String columnName) {
        Column column = new Column(columnName);
        if (plainSelect.getFromItem() instanceof net.sf.jsqlparser.schema.Table table) {
            if (table.getAlias() != null && table.getAlias().getName() != null) {
                column.setTable(new net.sf.jsqlparser.schema.Table(table.getAlias().getName()));
            }
        }
        return column;
    }

    /**
     * 注入数据权限条件。
     *
     * @param plainSelect 查询体
     * @author 余炘洋
     * @date 2026-01-23
     */
    private void injectDataScope(PlainSelect plainSelect) {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        if (session == null || session.getDataScope() == null) {
            return;
        }
        DataScopeRule rule = session.getDataScope();
        String scopeType = rule.getScopeType();
        if (scopeType == null) {
            return;
        }
        String normalized = scopeType.toUpperCase();
        if (RbacDataScopeType.ALL.equals(normalized) || RbacDataScopeType.TENANT.equals(normalized)) {
            return;
        }
        if (RbacDataScopeType.SELF.equals(normalized)) {
            String columnName = rule.getScopeColumn();
            if (columnName == null || columnName.isBlank()) {
                columnName = "creator_id";
            }
            if (containsColumn(plainSelect.getWhere(), columnName)) {
                return;
            }
            EqualsTo equalsTo = new EqualsTo();
            equalsTo.setLeftExpression(buildColumn(plainSelect, columnName));
            equalsTo.setRightExpression(new LongValue(String.valueOf(session.getUserId())));
            Expression where = plainSelect.getWhere();
            if (where == null) {
                plainSelect.setWhere(equalsTo);
            } else {
                plainSelect.setWhere(new AndExpression(where, equalsTo));
            }
            return;
        }
        if (RbacDataScopeType.CUSTOM.equals(normalized)) {
            if (rule.getScopeRule() == null || rule.getScopeRule().isBlank()) {
                return;
            }
            try {
                Expression custom = CCJSqlParserUtil.parseCondExpression(rule.getScopeRule());
                Expression where = plainSelect.getWhere();
                if (where == null) {
                    plainSelect.setWhere(custom);
                } else {
                    plainSelect.setWhere(new AndExpression(where, custom));
                }
            } catch (Exception ex) {
                log.warn("数据权限条件解析失败: {}", ex.getMessage());
            }
        }
    }

    /**
     * 屏蔽敏感字段，替换为 NULL AS 字段名。
     *
     * @param plainSelect 查询体
     * @param config      租户配置
     * @author 余炘洋
     * @date 2026-01-23
     */
    private void maskSensitiveColumns(PlainSelect plainSelect, TenantDbConfig config) {
        if (config == null) {
            return;
        }
        Set<String> sensitiveColumns = parseCsv(config.getSensitiveColumns());
        if (sensitiveColumns.isEmpty()) {
            return;
        }
        if (plainSelect.getSelectItems() == null || plainSelect.getSelectItems().isEmpty()) {
            return;
        }
        List<SelectItem<?>> items = plainSelect.getSelectItems();
        for (int i = 0; i < items.size(); i++) {
            SelectItem<?> item = items.get(i);
            Expression expression = item.getExpression();
            if (expression == null) {
                continue;
            }
            if (expression instanceof AllColumns || expression instanceof AllTableColumns) {
                continue;
            }
            SensitiveColumnVisitor visitor = new SensitiveColumnVisitor(sensitiveColumns);
            expression.accept(visitor);
            if (visitor.isFound()) {
                SelectItem<Expression> maskedItem = new SelectItem<>();
                maskedItem.setExpression(new NullValue());
                if (item.getAlias() == null || !StringUtils.hasText(item.getAlias().getName())) {
                    String aliasName = visitor.getMatchedColumn();
                    if (!StringUtils.hasText(aliasName)) {
                        aliasName = "sensitive_field";
                    }
                    maskedItem.setAlias(new Alias(aliasName, false));
                } else {
                    maskedItem.setAlias(item.getAlias());
                }
                items.set(i, maskedItem);
            }
        }
    }

    /**
     * 解析逗号分隔字符串为集合。
     *
     * @param text 原始文本
     * @return 集合
     * @author 余炘洋
     * @date 2026-01-23
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
     * 敏感字段检测访问器。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private static class SensitiveColumnVisitor extends ExpressionDeParser {
        private final Set<String> sensitiveColumns;
        private boolean found = false;
        private String matchedColumn;

        /**
         * 构造函数。
         *
         * @param sensitiveColumns 敏感字段集合
         * @author 余炘洋
         * @date 2026-01-23
         */
        private SensitiveColumnVisitor(Set<String> sensitiveColumns) {
            this.sensitiveColumns = sensitiveColumns;
        }

        /**
         * 访问列节点。
         *
         * @param column 列
         * @author 余炘洋
         * @date 2026-01-23
         */
        @Override
        public void visit(Column column) {
            if (column.getColumnName() != null) {
                String columnName = column.getColumnName().toLowerCase();
                String tableName = column.getTable() != null ? column.getTable().getName() : null;
                String qualified = tableName == null ? null : (tableName + "." + column.getColumnName()).toLowerCase();
                if (sensitiveColumns.contains(columnName) || (qualified != null && sensitiveColumns.contains(qualified))) {
                    found = true;
                    matchedColumn = column.getColumnName();
                    return;
                }
            }
            super.visit(column);
        }

        /**
         * 是否命中敏感字段。
         *
         * @return 是否命中
         * @author 余炘洋
         * @date 2026-01-23
         */
        private boolean isFound() {
            return found;
        }

        /**
         * 命中的字段名。
         *
         * @return 字段名
         * @author 余炘洋
         * @date 2026-01-23
         */
        private String getMatchedColumn() {
            return matchedColumn;
        }
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
