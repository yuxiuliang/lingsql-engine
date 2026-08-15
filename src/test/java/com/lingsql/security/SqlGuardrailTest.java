package com.lingsql.security;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.metadata.service.MetadataExtService;
import com.lingsql.rbac.model.DataScopeRule;
import com.lingsql.rbac.model.RbacDataScopeType;
import com.lingsql.rbac.security.RbacUserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;

class SqlGuardrailTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rewriteShouldForceTenantAndSelfConditionWhenUserProvidesConflictingFilters() {
        LingSqlProperties properties = createProperties(100);
        MetadataExtService metadataExtService = Mockito.mock(MetadataExtService.class);
        Mockito.when(metadataExtService.resolveIsolationField(anyString(), anyString()))
                .thenReturn(Optional.empty());
        SqlRewriter rewriter = new SqlRewriter(properties, metadataExtService);

        bindSelfScopeSession(123L, "creator_id");
        TenantDbConfig config = createTenantConfig("LOGICAL", "tenant_id");

        String rewritten = rewriter.rewrite(
                "SELECT order_id FROM orders WHERE tenant_id = 'OTHER' AND creator_id = 999 LIMIT 500",
                config,
                "TENANT_A"
        );

        assertTrue(rewritten.contains("tenant_id = 'TENANT_A'"), "应强制注入当前租户条件");
        assertTrue(rewritten.contains("creator_id = 123"), "应强制注入当前用户条件");
        assertTrue(rewritten.toLowerCase().contains("limit 100"), "应强制限制最大分页");
    }

    @Test
    void checkerShouldRejectTenantConditionWithWrongValue() {
        LingSqlProperties properties = createProperties(100);
        MetadataExtService metadataExtService = Mockito.mock(MetadataExtService.class);
        Mockito.when(metadataExtService.resolveIsolationField(anyString(), anyString()))
                .thenReturn(Optional.empty());
        SqlSecurityChecker checker = new SqlSecurityChecker(properties, metadataExtService);
        TenantDbConfig config = createTenantConfig("LOGICAL", "tenant_id");

        BizException ex = assertThrows(
                BizException.class,
                () -> checker.check(
                        "SELECT order_id FROM orders WHERE tenant_id = 'OTHER' LIMIT 10",
                        config,
                        "TENANT_A"
                )
        );

        assertEquals(ErrorCode.SECURITY_TENANT_SCOPE_BLOCKED, ex.getErrorCode());
    }

    @Test
    void candidateCheckerShouldRejectWriteBeforeRewrite() {
        LingSqlProperties properties = createProperties(100);
        MetadataExtService metadataExtService = Mockito.mock(MetadataExtService.class);
        SqlSecurityChecker checker = new SqlSecurityChecker(properties, metadataExtService);

        BizException ex = assertThrows(
                BizException.class,
                () -> checker.checkCandidate(
                        "DELETE FROM orders WHERE tenant_id = 'TENANT_A'",
                        createTenantConfig("LOGICAL", "tenant_id"),
                        "TENANT_A"
                )
        );

        assertEquals(ErrorCode.SECURITY_WRITE_BLOCKED, ex.getErrorCode());
    }

    @Test
    void candidateCheckerShouldRejectExplicitCrossTenantCondition() {
        LingSqlProperties properties = createProperties(100);
        MetadataExtService metadataExtService = Mockito.mock(MetadataExtService.class);
        Mockito.when(metadataExtService.resolveIsolationField(anyString(), anyString()))
                .thenReturn(Optional.empty());
        SqlSecurityChecker checker = new SqlSecurityChecker(properties, metadataExtService);

        BizException ex = assertThrows(
                BizException.class,
                () -> checker.checkCandidate(
                        "SELECT order_id FROM orders WHERE tenant_id = 'OTHER'",
                        createTenantConfig("LOGICAL", "tenant_id"),
                        "TENANT_A"
                )
        );

        assertEquals(ErrorCode.SECURITY_TENANT_SCOPE_BLOCKED, ex.getErrorCode());
    }

    @Test
    void candidateCheckerShouldAllowMissingTenantConditionForControlledRewrite() {
        LingSqlProperties properties = createProperties(100);
        MetadataExtService metadataExtService = Mockito.mock(MetadataExtService.class);
        Mockito.when(metadataExtService.resolveIsolationField(anyString(), anyString()))
                .thenReturn(Optional.empty());
        SqlSecurityChecker checker = new SqlSecurityChecker(properties, metadataExtService);

        try {
            checker.checkCandidate(
                    "SELECT order_id FROM orders WHERE amount > 0",
                    createTenantConfig("LOGICAL", "tenant_id"),
                    "TENANT_A"
            );
        } catch (Exception ex) {
            fail("候选 SQL 缺少租户条件时应交由受控改写补齐: " + ex.getMessage());
        }
    }

    @Test
    void checkerShouldRejectSelfScopeWithWrongUserCondition() {
        LingSqlProperties properties = createProperties(100);
        MetadataExtService metadataExtService = Mockito.mock(MetadataExtService.class);
        Mockito.when(metadataExtService.resolveIsolationField(anyString(), anyString()))
                .thenReturn(Optional.empty());
        SqlSecurityChecker checker = new SqlSecurityChecker(properties, metadataExtService);
        TenantDbConfig config = createTenantConfig("PHYSICAL", "tenant_id");
        bindSelfScopeSession(123L, "creator_id");

        BizException ex = assertThrows(
                BizException.class,
                () -> checker.check(
                        "SELECT order_id FROM orders WHERE creator_id = 999 LIMIT 10",
                        config,
                        "TENANT_A"
                )
        );

        assertEquals(ErrorCode.SECURITY_VIOLATION, ex.getErrorCode());
    }

    @Test
    void checkerShouldAllowSelfScopeWithCurrentUserCondition() {
        LingSqlProperties properties = createProperties(100);
        MetadataExtService metadataExtService = Mockito.mock(MetadataExtService.class);
        Mockito.when(metadataExtService.resolveIsolationField(anyString(), anyString()))
                .thenReturn(Optional.empty());
        SqlSecurityChecker checker = new SqlSecurityChecker(properties, metadataExtService);
        TenantDbConfig config = createTenantConfig("PHYSICAL", "tenant_id");
        bindSelfScopeSession(123L, "creator_id");

        try {
            checker.check("SELECT order_id FROM orders WHERE creator_id = 123 LIMIT 10", config, "TENANT_A");
        } catch (Exception ex) {
            fail("当前用户条件正确时不应抛出异常: " + ex.getMessage());
        }
    }

    @Test
    void rewriteShouldInjectTenantConditionForAllJoinedTables() {
        LingSqlProperties properties = createProperties(100);
        MetadataExtService metadataExtService = Mockito.mock(MetadataExtService.class);
        Mockito.when(metadataExtService.resolveIsolationField(anyString(), anyString()))
                .thenReturn(Optional.empty());
        SqlRewriter rewriter = new SqlRewriter(properties, metadataExtService);
        TenantDbConfig config = createTenantConfig("LOGICAL", "tenant_id");

        String rewritten = rewriter.rewrite(
                "SELECT o.order_id FROM orders o JOIN customers c ON o.customer_id = c.id WHERE o.amount > 0 LIMIT 50",
                config,
                "TENANT_A"
        );

        assertTrue(rewritten.contains("o.tenant_id = 'TENANT_A'"), "主表应注入租户条件");
        assertTrue(rewritten.contains("c.tenant_id = 'TENANT_A'"), "JOIN 表应注入租户条件");
    }

    @Test
    void checkerShouldRejectJoinMissingTenantConditionOnJoinedTable() {
        LingSqlProperties properties = createProperties(100);
        MetadataExtService metadataExtService = Mockito.mock(MetadataExtService.class);
        Mockito.when(metadataExtService.resolveIsolationField(anyString(), anyString()))
                .thenReturn(Optional.empty());
        SqlSecurityChecker checker = new SqlSecurityChecker(properties, metadataExtService);
        TenantDbConfig config = createTenantConfig("LOGICAL", "tenant_id");

        BizException ex = assertThrows(
                BizException.class,
                () -> checker.check(
                        "SELECT o.order_id FROM orders o JOIN customers c ON o.customer_id = c.id " +
                                "WHERE o.tenant_id = 'TENANT_A' LIMIT 10",
                        config,
                        "TENANT_A"
                )
        );

        assertEquals(ErrorCode.SECURITY_TENANT_SCOPE_BLOCKED, ex.getErrorCode());
    }

    @Test
    void checkerShouldAllowJoinWithTenantConditionOnAllTables() {
        LingSqlProperties properties = createProperties(100);
        MetadataExtService metadataExtService = Mockito.mock(MetadataExtService.class);
        Mockito.when(metadataExtService.resolveIsolationField(anyString(), anyString()))
                .thenReturn(Optional.empty());
        SqlSecurityChecker checker = new SqlSecurityChecker(properties, metadataExtService);
        TenantDbConfig config = createTenantConfig("LOGICAL", "tenant_id");

        try {
            checker.check(
                    "SELECT o.order_id FROM orders o JOIN customers c ON o.customer_id = c.id " +
                            "WHERE o.tenant_id = 'TENANT_A' AND c.tenant_id = 'TENANT_A' LIMIT 10",
                    config,
                    "TENANT_A"
            );
        } catch (Exception ex) {
            fail("JOIN 多表租户条件完整时不应抛出异常: " + ex.getMessage());
        }
    }

    @Test
    void rewriteShouldInjectOrgScopeCondition() {
        LingSqlProperties properties = createProperties(100);
        MetadataExtService metadataExtService = Mockito.mock(MetadataExtService.class);
        Mockito.when(metadataExtService.resolveIsolationField(anyString(), anyString()))
                .thenReturn(Optional.empty());
        SqlRewriter rewriter = new SqlRewriter(properties, metadataExtService);

        DataScopeRule rule = new DataScopeRule();
        rule.setScopeType(RbacDataScopeType.ORG);
        rule.setScopeColumn("org_code");

        RbacUserSession session = new RbacUserSession();
        session.setDataScope(rule);
        session.setMemberOrgUnitCodes(List.of("CS001", "CS002"));
        session.setOrgUnitCodes(List.of("CS001", "CS002"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(session, "token", List.of())
        );

        String rewritten = rewriter.rewrite(
                "SELECT order_id FROM orders WHERE amount > 0 LIMIT 50",
                createTenantConfig("PHYSICAL", "tenant_id"),
                "TENANT_A"
        );

        assertTrue(rewritten.contains("org_code"), "应包含组织字段条件");
        assertTrue(rewritten.contains("CS001"), "应包含直属组织编码");
        assertTrue(rewritten.contains("CS002"), "应包含多组织编码");
    }

    @Test
    void checkerShouldAllowOrgSubtreeScopeWhenAllCodesPresent() {
        LingSqlProperties properties = createProperties(100);
        MetadataExtService metadataExtService = Mockito.mock(MetadataExtService.class);
        Mockito.when(metadataExtService.resolveIsolationField(anyString(), anyString()))
                .thenReturn(Optional.empty());
        SqlSecurityChecker checker = new SqlSecurityChecker(properties, metadataExtService);

        DataScopeRule rule = new DataScopeRule();
        rule.setScopeType(RbacDataScopeType.ORG_SUBTREE);
        rule.setScopeColumn("org_code");

        RbacUserSession session = new RbacUserSession();
        session.setDataScope(rule);
        session.setMemberOrgSubtreeCodes(List.of("HN", "HN_CS", "HN_CS_YL"));
        session.setOrgSubtreeCodes(List.of("HN", "HN_CS", "HN_CS_YL"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(session, "token", List.of())
        );

        try {
            checker.check(
                    "SELECT order_id FROM orders WHERE org_code IN ('HN', 'HN_CS', 'HN_CS_YL') LIMIT 10",
                    createTenantConfig("PHYSICAL", "tenant_id"),
                    "TENANT_A"
            );
        } catch (Exception ex) {
            fail("组织子树条件完整时不应抛出异常: " + ex.getMessage());
        }
    }

    @Test
    void rewriteShouldUseDirectOrgCodesInsteadOfRelationExpandedCodesForOrgScope() {
        LingSqlProperties properties = createProperties(100);
        MetadataExtService metadataExtService = Mockito.mock(MetadataExtService.class);
        Mockito.when(metadataExtService.resolveIsolationField(anyString(), anyString()))
                .thenReturn(Optional.empty());
        SqlRewriter rewriter = new SqlRewriter(properties, metadataExtService);

        DataScopeRule rule = new DataScopeRule();
        rule.setScopeType(RbacDataScopeType.ORG);
        rule.setScopeColumn("org_code");

        RbacUserSession session = new RbacUserSession();
        session.setDataScope(rule);
        session.setMemberOrgUnitCodes(List.of("SRC"));
        session.setOrgUnitCodes(List.of("SRC", "OWN", "OWN_CHILD"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(session, "token", List.of())
        );

        String rewritten = rewriter.rewrite(
                "SELECT order_id FROM orders WHERE amount > 0 LIMIT 50",
                createTenantConfig("PHYSICAL", "tenant_id"),
                "TENANT_A"
        );

        assertTrue(rewritten.contains("SRC"), "直属组织编码应参与改写");
        assertTrue(!rewritten.contains("OWN"), "关系扩展编码不应被 ORG 范围直接注入");
    }

    @Test
    void rewriteShouldInjectRelationScopedCodesWhenRelationScopeConfigured() {
        LingSqlProperties properties = createProperties(100);
        MetadataExtService metadataExtService = Mockito.mock(MetadataExtService.class);
        Mockito.when(metadataExtService.resolveIsolationField(anyString(), anyString()))
                .thenReturn(Optional.empty());
        SqlRewriter rewriter = new SqlRewriter(properties, metadataExtService);

        DataScopeRule rule = new DataScopeRule();
        rule.setScopeType(RbacDataScopeType.RELATION_SUBTREE);
        rule.setScopeColumn("org_code");
        rule.setRelationTypes(List.of("SERVICE"));

        RbacUserSession session = new RbacUserSession();
        session.setDataScope(rule);
        session.setRelationDirectScopes(java.util.Map.of("SERVICE", List.of("SRV")));
        session.setRelationScopes(java.util.Map.of("SERVICE", List.of("SRV", "SRV_CHILD")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(session, "token", List.of())
        );

        String rewritten = rewriter.rewrite(
                "SELECT order_id FROM orders WHERE amount > 0 LIMIT 50",
                createTenantConfig("PHYSICAL", "tenant_id"),
                "TENANT_A"
        );

        assertTrue(rewritten.contains("SRV"), "关系型数据权限应注入关系目标组织编码");
        assertTrue(rewritten.contains("SRV_CHILD"), "关系型子树权限应注入关系目标下级编码");
    }

    private LingSqlProperties createProperties(int limit) {
        LingSqlProperties properties = new LingSqlProperties();
        LingSqlProperties.Security security = new LingSqlProperties.Security();
        security.setDefaultLimit(limit);
        properties.setSecurity(security);
        return properties;
    }

    private TenantDbConfig createTenantConfig(String mode, String logicColumn) {
        TenantDbConfig config = new TenantDbConfig();
        config.setIsolationMode(mode);
        config.setLogicColumn(logicColumn);
        return config;
    }

    private void bindSelfScopeSession(Long userId, String scopeColumn) {
        DataScopeRule rule = new DataScopeRule();
        rule.setScopeType(RbacDataScopeType.SELF);
        rule.setScopeColumn(scopeColumn);

        RbacUserSession session = new RbacUserSession();
        session.setUserId(userId);
        session.setDataScope(rule);

        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(session, "token", List.of());
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
    }
}
