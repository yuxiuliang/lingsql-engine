package com.lingsql.web;

import com.lingsql.audit.model.TokenAuditOverviewView;
import com.lingsql.audit.model.TokenAuditQuery;
import com.lingsql.audit.service.AuditLogService;
import com.lingsql.audit.service.SqlExecuteLogService;
import com.lingsql.common.pagination.PageParamResolver;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.security.RbacUserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogControllerTokenOverviewTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nonSysAdminShouldForceTenantScope() {
        bindSession("TENANT_A", List.of("TENANT_ADMIN"));
        SqlExecuteLogService sqlExecuteLogService = Mockito.mock(SqlExecuteLogService.class);
        when(sqlExecuteLogService.tokenOverview(any(TokenAuditQuery.class), eq(false)))
                .thenReturn(new TokenAuditOverviewView());
        AuditLogController controller = new AuditLogController(
                Mockito.mock(AuditLogService.class),
                sqlExecuteLogService,
                Mockito.mock(PageParamResolver.class)
        );

        ApiResponse<TokenAuditOverviewView> response = controller.tokenOverview(
                "TENANT_B",
                1001L,
                "销售额",
                "WEB",
                null,
                null,
                20
        );

        ArgumentCaptor<TokenAuditQuery> captor = ArgumentCaptor.forClass(TokenAuditQuery.class);
        verify(sqlExecuteLogService).tokenOverview(captor.capture(), eq(false));
        assertEquals(200, response.getCode());
        assertEquals("TENANT_A", captor.getValue().getTenantId());
        assertEquals(1001L, captor.getValue().getUserId());
        assertEquals("销售额", captor.getValue().getQuestion());
        assertEquals("WEB", captor.getValue().getRequestSource());
        assertEquals(20, captor.getValue().getTopN());
    }

    @Test
    void sysAdminShouldKeepRequestedTenantScope() {
        bindSession("SYS", List.of("SYS_ADMIN"));
        SqlExecuteLogService sqlExecuteLogService = Mockito.mock(SqlExecuteLogService.class);
        when(sqlExecuteLogService.tokenOverview(any(TokenAuditQuery.class), eq(true)))
                .thenReturn(new TokenAuditOverviewView());
        AuditLogController controller = new AuditLogController(
                Mockito.mock(AuditLogService.class),
                sqlExecuteLogService,
                Mockito.mock(PageParamResolver.class)
        );

        ApiResponse<TokenAuditOverviewView> response = controller.tokenOverview(
                "TENANT_B",
                null,
                null,
                null,
                null,
                null,
                10
        );

        ArgumentCaptor<TokenAuditQuery> captor = ArgumentCaptor.forClass(TokenAuditQuery.class);
        verify(sqlExecuteLogService).tokenOverview(captor.capture(), eq(true));
        assertEquals(200, response.getCode());
        assertEquals("TENANT_B", captor.getValue().getTenantId());
    }

    private void bindSession(String tenantId, List<String> roles) {
        RbacUserSession session = new RbacUserSession();
        session.setUserId(1L);
        session.setTenantId(tenantId);
        session.setRoles(roles);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(session, "token", List.of())
        );
    }
}
