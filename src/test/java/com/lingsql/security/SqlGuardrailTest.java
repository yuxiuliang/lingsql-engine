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
        SqlSecurityChecker checker = new SqlSecurityChecker(properties);
        TenantDbConfig config = createTenantConfig("LOGICAL", "tenant_id");

        BizException ex = assertThrows(
                BizException.class,
                () -> checker.check(
                        "SELECT order_id FROM orders WHERE tenant_id = 'OTHER' LIMIT 10",
                        config,
                        "TENANT_A"
                )
        );

        assertEquals(ErrorCode.SECURITY_VIOLATION, ex.getErrorCode());
    }

    @Test
    void checkerShouldRejectSelfScopeWithWrongUserCondition() {
        LingSqlProperties properties = createProperties(100);
        SqlSecurityChecker checker = new SqlSecurityChecker(properties);
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
        SqlSecurityChecker checker = new SqlSecurityChecker(properties);
        TenantDbConfig config = createTenantConfig("PHYSICAL", "tenant_id");
        bindSelfScopeSession(123L, "creator_id");

        try {
            checker.check("SELECT order_id FROM orders WHERE creator_id = 123 LIMIT 10", config, "TENANT_A");
        } catch (Exception ex) {
            fail("当前用户条件正确时不应抛出异常: " + ex.getMessage());
        }
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
