package com.lingsql.security;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
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

import java.util.ArrayList;
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
                throw new BizException(ErrorCode.SQL_INVALID, "仅支持单条 SELECT 查询");
            }

            injectLimit(plainSelect);

            if (config.isLogicalIsolation()) {
                injectTenantConditions(plainSelect, config, tenantId);
            }
            maskSensitiveColumns(plainSelect, config);
            injectDataScope(plainSelect);

            return select.toString();
        } catch (BizException ex) {
            throw ex;
        } catch (Exception e) {
            log.warn("SQL 改写失败: {}", e.getMessage(), e);
            throw new BizException(ErrorCode.SQL_INVALID, "SQL 改写失败", e);
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
     * 注入租户隔离条件（主表与 JOIN 表均强制追加）。
     *
     * @param plainSelect 查询体
     * @param config      租户配置
     * @param tenantId    租户标识
     * @author 余炘洋
     * @date 2026-02-28
     */
    private void injectTenantConditions(PlainSelect plainSelect, TenantDbConfig config, String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return;
        }
        List<TableRef> tableRefs = collectTableRefs(plainSelect);
        if (tableRefs.isEmpty()) {
            String defaultColumn = config.resolveLogicColumn();
            if (!StringUtils.hasText(defaultColumn)) {
                return;
            }
            injectEqualsCondition(plainSelect, buildColumn(plainSelect, defaultColumn), new StringValue(tenantId));
            return;
        }
        for (TableRef tableRef : tableRefs) {
            String isolationColumn = metadataExtService
                    .resolveIsolationField(tenantId, tableRef.tableName())
                    .orElse(config.resolveLogicColumn());
            if (!StringUtils.hasText(isolationColumn)) {
                continue;
            }
            Column column = new Column(isolationColumn);
            if (StringUtils.hasText(tableRef.qualifier())) {
                column.setTable(new net.sf.jsqlparser.schema.Table(tableRef.qualifier()));
            }
            injectEqualsCondition(plainSelect, column, new StringValue(tenantId));
        }
    }

    /**
     * 收集主表与 JOIN 表引用。
     *
     * @param plainSelect 查询体
     * @return 表引用列表
     * @author 余炘洋
     * @date 2026-02-28
     */
    private List<TableRef> collectTableRefs(PlainSelect plainSelect) {
        List<TableRef> refs = new ArrayList<>();
        collectTableRef(plainSelect == null ? null : plainSelect.getFromItem(), refs);
        if (plainSelect != null && plainSelect.getJoins() != null) {
            plainSelect.getJoins().forEach(join -> collectTableRef(join.getRightItem(), refs));
        }
        return refs;
    }

    /**
     * 收集单个表引用。
     *
     * @param fromItem FROM/JOIN 项
     * @param refs     引用列表
     * @author 余炘洋
     * @date 2026-02-28
     */
    private void collectTableRef(net.sf.jsqlparser.statement.select.FromItem fromItem, List<TableRef> refs) {
        if (!(fromItem instanceof net.sf.jsqlparser.schema.Table table)) {
            return;
        }
        String tableName = table.getName();
        if (!StringUtils.hasText(tableName)) {
            return;
        }
        String qualifier = table.getAlias() != null ? table.getAlias().getName() : tableName;
        refs.add(new TableRef(tableName, qualifier));
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
            if (session.getUserId() == null) {
                throw new BizException(ErrorCode.SECURITY_VIOLATION, "数据权限缺少用户信息");
            }
            injectEqualsCondition(plainSelect, buildColumn(plainSelect, columnName), new LongValue(String.valueOf(session.getUserId())));
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
                log.warn("数据权限条件解析失败: {}", ex.getMessage(), ex);
                throw new BizException(ErrorCode.DATA_SCOPE_INVALID, "数据权限规则配置错误", ex);
            }
        }
    }

    /**
     * 追加等值条件，避免原条件被覆盖。
     *
     * @param plainSelect 查询体
     * @param left        左表达式
     * @param right       右表达式
     * @author 余炘洋
     * @date 2026-02-26
     */
    private void injectEqualsCondition(PlainSelect plainSelect, Expression left, Expression right) {
        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(left);
        equalsTo.setRightExpression(right);
        Expression where = plainSelect.getWhere();
        if (where == null) {
            plainSelect.setWhere(equalsTo);
            return;
        }
        plainSelect.setWhere(new AndExpression(where, equalsTo));
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
     * 表引用。
     *
     * @param tableName 表名
     * @param qualifier SQL 中可见限定名（别名优先）
     * @author 余炘洋
     * @date 2026-02-28
     */
    private record TableRef(String tableName, String qualifier) {
    }

}
