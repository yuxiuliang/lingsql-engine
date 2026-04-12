package com.lingsql.security;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.metadata.service.MetadataExtService;
import com.lingsql.rbac.model.DataScopeRule;
import com.lingsql.rbac.model.RbacDataScopeType;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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
    private final MetadataExtService metadataExtService;

    /**
     * 校验 SQL 是否安全。
     *
     * @param sql      SQL 文本
     * @param config   租户配置
     * @param tenantId 当前租户ID
     * @author 余炘洋
     * @date 2026-01-22
     */
    public void check(String sql, TenantDbConfig config, String tenantId) {
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
            validateTenantCondition(config, plainSelect, tenantId);
            validateSelfDataScopeCondition(plainSelect.getWhere());
            validateOrgDataScopeCondition(plainSelect.getWhere());
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
     * @param plainSelect 查询体
     * @param tenantId 当前租户ID
     * @author 余炘洋
     * @date 2026-01-22
     */
    private void validateTenantCondition(TenantDbConfig config, PlainSelect plainSelect, String tenantId) {
        if (!config.isLogicalIsolation()) {
            return;
        }
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(ErrorCode.SECURITY_VIOLATION, "安全警告：租户上下文缺失");
        }
        Expression where = plainSelect == null ? null : plainSelect.getWhere();
        if (where == null) {
            throw new BizException(ErrorCode.SECURITY_VIOLATION, "安全警告：逻辑隔离模式缺少 tenant 条件");
        }
        List<TableRef> tableRefs = collectTableRefs(plainSelect);
        if (tableRefs.isEmpty()) {
            String columnName = config.resolveLogicColumn();
            if (!containsEqualsCondition(where, columnName, tenantId, null)) {
                throw new BizException(ErrorCode.SECURITY_VIOLATION, "安全警告：逻辑隔离模式 tenant 条件不合法");
            }
            return;
        }
        for (TableRef ref : tableRefs) {
            String columnName = metadataExtService.resolveIsolationField(tenantId, ref.tableName())
                    .orElse(config.resolveLogicColumn());
            if (!containsEqualsCondition(where, columnName, tenantId, null, ref.qualifier(), ref.tableName())) {
                throw new BizException(ErrorCode.SECURITY_VIOLATION,
                        "安全警告：逻辑隔离模式 tenant 条件不合法，表=" + ref.tableName());
            }
        }
    }

    /**
     * 校验 SELF 数据权限条件是否包含当前用户。
     *
     * @param where WHERE 条件
     * @author 余炘洋
     * @date 2026-02-26
     */
    private void validateSelfDataScopeCondition(Expression where) {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        if (session == null || session.getDataScope() == null) {
            return;
        }
        DataScopeRule rule = session.getDataScope();
        if (!RbacDataScopeType.SELF.equalsIgnoreCase(rule.getScopeType())) {
            return;
        }
        if (session.getUserId() == null || where == null) {
            throw new BizException(ErrorCode.SECURITY_VIOLATION, "安全警告：SELF 数据权限缺少用户条件");
        }
        String scopeColumn = StringUtils.hasText(rule.getScopeColumn()) ? rule.getScopeColumn() : "creator_id";
        if (!containsEqualsCondition(where, scopeColumn, null, session.getUserId())) {
            throw new BizException(ErrorCode.SECURITY_VIOLATION, "安全警告：SELF 数据权限条件不合法");
        }
    }

    /**
     * 校验组织数据权限条件是否存在。
     *
     * @param where WHERE 条件
     * @author 余炘洋
     * @date 2026-04-11
     */
    private void validateOrgDataScopeCondition(Expression where) {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        if (session == null || session.getDataScope() == null) {
            return;
        }
        DataScopeRule rule = session.getDataScope();
        String scopeType = rule.getScopeType();
        if (!RbacDataScopeType.ORG.equalsIgnoreCase(scopeType)
                && !RbacDataScopeType.ORG_SUBTREE.equalsIgnoreCase(scopeType)
                && !RbacDataScopeType.RELATION.equalsIgnoreCase(scopeType)
                && !RbacDataScopeType.RELATION_SUBTREE.equalsIgnoreCase(scopeType)) {
            return;
        }
        String scopeColumn = StringUtils.hasText(rule.getScopeColumn()) ? rule.getScopeColumn() : "org_code";
        List<String> allowedCodes;
        if (RbacDataScopeType.RELATION.equalsIgnoreCase(scopeType)
                || RbacDataScopeType.RELATION_SUBTREE.equalsIgnoreCase(scopeType)) {
            allowedCodes = resolveRelationScopedOrgCodes(session, scopeType, rule.getRelationTypes());
        } else {
            allowedCodes = RbacDataScopeType.ORG_SUBTREE.equalsIgnoreCase(scopeType)
                    ? resolveDirectOrgSubtreeCodes(session)
                    : resolveDirectOrgCodes(session);
        }
        if (where == null || allowedCodes == null || allowedCodes.isEmpty()) {
            throw new BizException(ErrorCode.SECURITY_VIOLATION, "安全警告：组织数据权限缺少组织条件");
        }
        if (!containsInCondition(where, scopeColumn, allowedCodes)) {
            throw new BizException(ErrorCode.SECURITY_VIOLATION, "安全警告：组织数据权限条件不合法");
        }
    }

    /**
     * 解析直属组织编码范围。
     *
     * @param session 当前会话
     * @return 直属组织编码
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> resolveDirectOrgCodes(RbacUserSession session) {
        if (session == null) {
            return List.of();
        }
        if (session.getMemberOrgUnitCodes() != null && !session.getMemberOrgUnitCodes().isEmpty()) {
            return session.getMemberOrgUnitCodes();
        }
        return session.getOrgUnitCodes();
    }

    /**
     * 解析直属组织及下级编码范围。
     *
     * @param session 当前会话
     * @return 直属组织及下级编码
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> resolveDirectOrgSubtreeCodes(RbacUserSession session) {
        if (session == null) {
            return List.of();
        }
        if (session.getMemberOrgSubtreeCodes() != null && !session.getMemberOrgSubtreeCodes().isEmpty()) {
            return session.getMemberOrgSubtreeCodes();
        }
        return session.getOrgSubtreeCodes();
    }

    /**
     * 解析关系型数据权限对应的组织范围。
     *
     * @param session       当前会话
     * @param scopeType     范围类型
     * @param relationTypes 关系类型列表
     * @return 组织编码范围
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> resolveRelationScopedOrgCodes(RbacUserSession session, String scopeType, List<String> relationTypes) {
        if (session == null || relationTypes == null || relationTypes.isEmpty()) {
            return List.of();
        }
        Map<String, List<String>> scopeMapping = RbacDataScopeType.RELATION_SUBTREE.equalsIgnoreCase(scopeType)
                ? session.getRelationScopes()
                : session.getRelationDirectScopes();
        if (scopeMapping == null || scopeMapping.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String relationType : relationTypes) {
            if (!StringUtils.hasText(relationType)) {
                continue;
            }
            List<String> matchedCodes = scopeMapping.get(relationType.trim().toUpperCase(Locale.ROOT));
            if (matchedCodes != null) {
                codes.addAll(matchedCodes);
            }
        }
        return codes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
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
     * 判断 WHERE 条件中是否包含指定字段与目标值的等值条件。
     *
     * @param where         WHERE 条件
     * @param columnName    字段名
     * @param expectedText  期望字符串值
     * @param expectedLong  期望数字值
     * @return 是否匹配
     * @author 余炘洋
     * @date 2026-02-26
     */
    private boolean containsEqualsCondition(Expression where, String columnName, String expectedText, Long expectedLong) {
        return containsEqualsCondition(where, columnName, expectedText, expectedLong, null, null);
    }

    /**
     * 判断 WHERE 条件中是否包含指定字段与目标值的等值条件（可选表限定名）。
     *
     * @param where             WHERE 条件
     * @param columnName        字段名
     * @param expectedText      期望字符串值
     * @param expectedLong      期望数字值
     * @param expectedQualifier 期望表别名
     * @param expectedTableName 期望表名
     * @return 是否匹配
     * @author 余炘洋
     * @date 2026-02-28
     */
    private boolean containsEqualsCondition(Expression where,
                                            String columnName,
                                            String expectedText,
                                            Long expectedLong,
                                            String expectedQualifier,
                                            String expectedTableName) {
        if (where == null || !StringUtils.hasText(columnName)) {
            return false;
        }
        EqualsConditionVisitor visitor = new EqualsConditionVisitor(
                columnName,
                expectedText,
                expectedLong,
                expectedQualifier,
                expectedTableName
        );
        where.accept(visitor);
        if (visitor.isFound()) {
            return true;
        }
        return containsEqualsByText(
                where.toString(),
                normalizeColumnName(columnName),
                expectedText,
                expectedLong,
                normalizeIdentifier(expectedQualifier),
                normalizeIdentifier(expectedTableName)
        );
    }

    /**
     * 文本兜底校验，处理部分表达式节点类型差异。
     *
     * @param whereText    WHERE 文本
     * @param columnName   字段名
     * @param expectedText 期望字符串值
     * @param expectedLong 期望数字值
     * @param expectedQualifier 期望表别名
     * @param expectedTableName 期望表名
     * @return 是否匹配
     * @author 余炘洋
     * @date 2026-02-26
     */
    private boolean containsEqualsByText(String whereText,
                                         String columnName,
                                         String expectedText,
                                         Long expectedLong,
                                         String expectedQualifier,
                                         String expectedTableName) {
        if (!StringUtils.hasText(whereText) || !StringUtils.hasText(columnName)) {
            return false;
        }
        String normalized = whereText.replace("`", "");
        String columnPattern;
        if (StringUtils.hasText(expectedQualifier) || StringUtils.hasText(expectedTableName)) {
            String q1 = StringUtils.hasText(expectedQualifier) ? Pattern.quote(expectedQualifier) : null;
            String q2 = StringUtils.hasText(expectedTableName) ? Pattern.quote(expectedTableName) : null;
            if (q1 != null && q2 != null && !q1.equals(q2)) {
                columnPattern = "(?:(?:" + q1 + ")|(?:" + q2 + "))\\." + Pattern.quote(columnName);
            } else if (q1 != null) {
                columnPattern = q1 + "\\." + Pattern.quote(columnName);
            } else {
                columnPattern = q2 + "\\." + Pattern.quote(columnName);
            }
        } else {
            columnPattern = "(?:\\b\\w+\\.)?" + Pattern.quote(columnName);
        }
        if (expectedText != null) {
            String valuePattern = Pattern.quote("'" + expectedText + "'");
            return Pattern.compile(columnPattern + "\\s*=\\s*" + valuePattern, Pattern.CASE_INSENSITIVE).matcher(normalized).find()
                    || Pattern.compile(valuePattern + "\\s*=\\s*" + columnPattern, Pattern.CASE_INSENSITIVE).matcher(normalized).find();
        }
        if (expectedLong != null) {
            String valuePattern = "(?:'?)" + Pattern.quote(String.valueOf(expectedLong)) + "(?:'?)";
            return Pattern.compile(columnPattern + "\\s*=\\s*" + valuePattern, Pattern.CASE_INSENSITIVE).matcher(normalized).find()
                    || Pattern.compile(valuePattern + "\\s*=\\s*" + columnPattern, Pattern.CASE_INSENSITIVE).matcher(normalized).find();
        }
        return false;
    }

    /**
     * 判断 WHERE 条件中是否包含指定字段的 IN 条件。
     *
     * @param where        WHERE 条件
     * @param columnName   字段名
     * @param expectedText 期望值列表
     * @return 是否匹配
     * @author 余炘洋
     * @date 2026-04-11
     */
    private boolean containsInCondition(Expression where, String columnName, List<String> expectedText) {
        if (where == null || !StringUtils.hasText(columnName) || expectedText == null || expectedText.isEmpty()) {
            return false;
        }
        String whereText = where.toString().replace("`", "");
        String normalizedColumn = normalizeColumnName(columnName);
        String columnPattern = "(?:\\b\\w+\\.)?" + Pattern.quote(normalizedColumn);
        if (!Pattern.compile(columnPattern + "\\s+IN\\s*\\(", Pattern.CASE_INSENSITIVE).matcher(whereText).find()) {
            return false;
        }
        String normalizedWhere = whereText.toUpperCase();
        for (String value : expectedText) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String expected = "'" + value.trim().toUpperCase() + "'";
            if (!normalizedWhere.contains(expected)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 标准化字段名，去除表前缀与反引号。
     *
     * @param columnName 原始字段名
     * @return 标准化字段名
     * @author 余炘洋
     * @date 2026-02-26
     */
    private String normalizeColumnName(String columnName) {
        if (!StringUtils.hasText(columnName)) {
            return "";
        }
        String normalized = columnName.trim().replace("`", "");
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex > -1 && dotIndex < normalized.length() - 1) {
            normalized = normalized.substring(dotIndex + 1);
        }
        return normalized.toLowerCase();
    }

    /**
     * 标准化标识符。
     *
     * @param identifier 原始标识符
     * @return 标准化结果
     * @author 余炘洋
     * @date 2026-02-28
     */
    private String normalizeIdentifier(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return null;
        }
        return identifier.trim().replace("`", "").toLowerCase();
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
        String qualifier = table.getAlias() == null ? tableName : table.getAlias().getName();
        refs.add(new TableRef(tableName, qualifier));
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
     * 等值条件检测访问器。
     *
     * @author 余炘洋
     * @date 2026-02-26
     */
    private class EqualsConditionVisitor extends ExpressionDeParser {
        private final String columnName;
        private final String expectedText;
        private final Long expectedLong;
        private final String expectedQualifier;
        private final String expectedTableName;
        private boolean found = false;

        /**
         * 构造函数。
         *
         * @param columnName   字段名
         * @param expectedText 期望字符串值
         * @param expectedLong 期望数字值
         * @param expectedQualifier 期望表别名
         * @param expectedTableName 期望表名
         * @author 余炘洋
         * @date 2026-02-26
         */
        private EqualsConditionVisitor(String columnName,
                                       String expectedText,
                                       Long expectedLong,
                                       String expectedQualifier,
                                       String expectedTableName) {
            this.columnName = normalizeColumnName(columnName);
            this.expectedText = expectedText;
            this.expectedLong = expectedLong;
            this.expectedQualifier = normalizeIdentifier(expectedQualifier);
            this.expectedTableName = normalizeIdentifier(expectedTableName);
        }

        /**
         * 访问等值表达式。
         *
         * @param equalsTo 等值条件
         * @author 余炘洋
         * @date 2026-02-26
         */
        @Override
        public void visit(EqualsTo equalsTo) {
            if (equalsTo == null || found) {
                super.visit(equalsTo);
                return;
            }
            if (matches(equalsTo.getLeftExpression(), equalsTo.getRightExpression())
                    || matches(equalsTo.getRightExpression(), equalsTo.getLeftExpression())) {
                found = true;
                return;
            }
            super.visit(equalsTo);
        }

        private boolean matches(Expression columnExpr, Expression valueExpr) {
            if (!(columnExpr instanceof Column column) || column.getColumnName() == null) {
                return false;
            }
            String actualColumn = normalizeColumnName(column.getColumnName());
            if (!actualColumn.equals(columnName)) {
                return false;
            }
            if (!matchesTable(column)) {
                return false;
            }
            if (expectedText != null) {
                return matchesTextValue(valueExpr, expectedText);
            }
            if (expectedLong != null) {
                return matchesLongValue(valueExpr, expectedLong);
            }
            return false;
        }

        private boolean matchesTextValue(Expression valueExpr, String expected) {
            if (valueExpr == null) {
                return false;
            }
            if (valueExpr instanceof StringValue stringValue) {
                return expected.equals(stringValue.getValue());
            }
            if (valueExpr instanceof LongValue longValue) {
                return expected.equals(String.valueOf(longValue.getValue()));
            }
            return expected.equals(stripValue(valueExpr.toString()));
        }

        private boolean matchesTable(Column column) {
            if (!StringUtils.hasText(expectedQualifier) && !StringUtils.hasText(expectedTableName)) {
                return true;
            }
            String actualTable = column.getTable() == null ? null : normalizeIdentifier(column.getTable().getName());
            if (!StringUtils.hasText(actualTable)) {
                return false;
            }
            if (StringUtils.hasText(expectedQualifier) && expectedQualifier.equals(actualTable)) {
                return true;
            }
            return StringUtils.hasText(expectedTableName) && expectedTableName.equals(actualTable);
        }

        private boolean matchesLongValue(Expression valueExpr, Long expected) {
            if (valueExpr == null || expected == null) {
                return false;
            }
            if (valueExpr instanceof LongValue longValue) {
                return longValue.getValue() == expected.longValue();
            }
            if (valueExpr instanceof StringValue stringValue) {
                try {
                    return Long.parseLong(stringValue.getValue()) == expected.longValue();
                } catch (NumberFormatException ex) {
                    return false;
                }
            }
            try {
                return Long.parseLong(stripValue(valueExpr.toString())) == expected.longValue();
            } catch (NumberFormatException ex) {
                return false;
            }
        }

        private String stripValue(String raw) {
            if (raw == null) {
                return "";
            }
            String value = raw.trim();
            if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }

        /**
         * 是否命中字段。
         *
         * @return 是否命中
         * @author 余炘洋
         * @date 2026-02-26
         */
        private boolean isFound() {
            return found;
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
