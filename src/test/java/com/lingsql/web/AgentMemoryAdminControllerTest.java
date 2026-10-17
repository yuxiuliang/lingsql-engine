package com.lingsql.web;

import com.lingsql.agent.entity.AgentMemoryFact;
import com.lingsql.agent.memory.AgentMemoryService;
import com.lingsql.agent.memory.MemorySourceType;
import com.lingsql.agent.memory.MemoryStatus;
import com.lingsql.agent.memory.MemoryTier;
import com.lingsql.agent.memory.model.AgentMemoryAdminSaveRequest;
import com.lingsql.agent.memory.model.AgentMemoryCorrectRequest;
import com.lingsql.agent.memory.model.AgentMemoryView;
import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.security.RbacUserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 长期记忆治理接口测试：租户隔离、层级校验与审计委托。
 *
 * @author 余炘洋
 * @date 2026-10-18
 */
class AgentMemoryAdminControllerTest {

    private AgentMemoryService agentMemoryService;

    private AuditOperationExecutor auditOperationExecutor;

    private AgentMemoryAdminController controller;

    @BeforeEach
    void setUp() {
        agentMemoryService = Mockito.mock(AgentMemoryService.class);
        auditOperationExecutor = Mockito.mock(AuditOperationExecutor.class);
        controller = new AgentMemoryAdminController(agentMemoryService, auditOperationExecutor);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listShouldUseSessionTenantForTenantAdmin() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        when(agentMemoryService.listForAdmin("tenant-a", null, null, null))
                .thenReturn(List.of(fact(1L, "PROCEDURAL", null, "销售额口径为不含税金额")));

        ApiResponse<List<AgentMemoryView>> response = controller.list("tenant-a", null, null, null);

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        verify(agentMemoryService).listForAdmin("tenant-a", null, null, null);
    }

    @Test
    void listShouldRejectCrossTenantAccessForTenantAdmin() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));

        assertThrows(AccessDeniedException.class, () -> controller.list("tenant-b", null, null, null));
    }

    @Test
    void listShouldAllowSysAdminCrossTenantAccess() {
        bindSession("tenant-a", List.of("SYS_ADMIN"));
        when(agentMemoryService.listForAdmin("tenant-b", 9L, MemoryTier.PERMANENT, MemoryStatus.ACTIVE))
                .thenReturn(List.of());

        ApiResponse<List<AgentMemoryView>> response =
                controller.list("tenant-b", 9L, "PERMANENT", "ACTIVE");

        assertEquals(200, response.getCode());
        verify(agentMemoryService).listForAdmin("tenant-b", 9L, MemoryTier.PERMANENT, MemoryStatus.ACTIVE);
    }

    @Test
    void listShouldRejectUnknownTier() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));

        BizException ex = assertThrows(BizException.class,
                () -> controller.list("tenant-a", null, "UNKNOWN", null));

        assertEquals(ErrorCode.INVALID_PARAM, ex.getErrorCode());
    }

    @Test
    void createShouldDelegateWithAdminApprovedSource() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        AgentMemoryAdminSaveRequest request = new AgentMemoryAdminSaveRequest();
        request.setTier("PROCEDURAL");
        request.setFactText("销售额口径为不含税金额");
        when(agentMemoryService.remember("tenant-a", null, MemoryTier.PROCEDURAL, MemorySourceType.ADMIN_APPROVED,
                "销售额口径为不含税金额", null, "tenant-admin"))
                .thenReturn(fact(2L, "PROCEDURAL", null, "销售额口径为不含税金额"));
        mockAuditExecute();

        ApiResponse<AgentMemoryView> response = controller.create(request, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals("销售额口径为不含税金额", response.getData().getFactText());
        verify(agentMemoryService).remember("tenant-a", null, MemoryTier.PROCEDURAL, MemorySourceType.ADMIN_APPROVED,
                "销售额口径为不含税金额", null, "tenant-admin");
    }

    @Test
    void createShouldRejectUserScopedPermanentWithoutUser() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        AgentMemoryAdminSaveRequest request = new AgentMemoryAdminSaveRequest();
        request.setTier("PERMANENT");
        request.setFactText("口径A");
        when(agentMemoryService.remember(anyString(), any(), any(), any(), anyString(), any(), anyString()))
                .thenThrow(new BizException(ErrorCode.INVALID_PARAM, "用户级永久记忆必须绑定用户"));
        mockAuditExecute();

        BizException ex = assertThrows(BizException.class,
                () -> controller.create(request, new MockHttpServletRequest()));

        assertEquals(ErrorCode.INVALID_PARAM, ex.getErrorCode());
    }

    @Test
    void correctShouldDelegateByAuditExecutor() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        AgentMemoryCorrectRequest request = new AgentMemoryCorrectRequest();
        request.setFactText("新口径");
        when(agentMemoryService.correctByAdmin("tenant-a", 5L, "新口径", null, "tenant-admin"))
                .thenReturn(fact(6L, "PROCEDURAL", null, "新口径"));
        mockAuditExecute();

        ApiResponse<AgentMemoryView> response =
                controller.correct(5L, "tenant-a", request, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals("新口径", response.getData().getFactText());
    }

    @Test
    void revokeShouldDelegateByAuditExecutor() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        AgentMemoryFact revoked = fact(7L, "PROCEDURAL", null, "旧口径");
        revoked.setStatus("REVOKED");
        when(agentMemoryService.revokeByAdmin("tenant-a", 7L)).thenReturn(revoked);
        mockAuditExecute();

        ApiResponse<AgentMemoryView> response = controller.revoke(7L, null, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals("已撤销", response.getData().getStatusLabel());
        verify(agentMemoryService).revokeByAdmin("tenant-a", 7L);
    }

    private void mockAuditExecute() {
        when(auditOperationExecutor.execute(anyString(), anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return ApiResponse.success(supplier.get());
                });
    }

    private void bindSession(String tenantId, List<String> roles) {
        RbacUserSession session = new RbacUserSession();
        session.setUserId(1L);
        session.setTenantId(tenantId);
        session.setUsername("tenant-admin");
        session.setRoles(roles);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(session, "token", List.of())
        );
    }

    private AgentMemoryFact fact(Long id, String tier, Long userId, String factText) {
        LocalDateTime now = LocalDateTime.now();
        AgentMemoryFact fact = new AgentMemoryFact();
        fact.setId(id);
        fact.setTenantId("tenant-a");
        fact.setUserId(userId);
        fact.setTier(tier);
        fact.setFactText(factText);
        fact.setSourceType("ADMIN_APPROVED");
        fact.setStatus("ACTIVE");
        fact.setConfidence(100);
        fact.setVersion(1);
        fact.setCreatedBy("tenant-admin");
        fact.setCreatedAt(now);
        fact.setUpdatedAt(now);
        fact.setExpiresAt(now.plusDays(30));
        return fact;
    }
}
