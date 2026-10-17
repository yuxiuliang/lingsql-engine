package com.lingsql.web;

import com.lingsql.agent.entity.AgentApprovalNode;
import com.lingsql.agent.model.AgentApprovalDecisionCommand;
import com.lingsql.agent.model.AgentApprovalResumeView;
import com.lingsql.agent.model.AgentApprovalView;
import com.lingsql.agent.orchestration.AgentResumeCommand;
import com.lingsql.agent.orchestration.AgentRunResult;
import com.lingsql.agent.orchestration.AgentRunStatus;
import com.lingsql.agent.orchestration.IControlledAgentOrchestrator;
import com.lingsql.agent.service.AgentApprovalService;
import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.security.RbacUserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 人工审批管理接口测试。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
class AgentApprovalAdminControllerTest {

    private final AgentApprovalService approvalService = Mockito.mock(AgentApprovalService.class);
    private final IControlledAgentOrchestrator agentOrchestrator = Mockito.mock(IControlledAgentOrchestrator.class);
    private final AuditOperationExecutor auditOperationExecutor = Mockito.mock(AuditOperationExecutor.class);
    private final AgentApprovalAdminController controller = new AgentApprovalAdminController(
            approvalService,
            agentOrchestrator,
            auditOperationExecutor
    );

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listShouldReturnApprovalNodesForSysAdmin() {
        bindSession("tenant-a", List.of("SYS_ADMIN"));
        when(approvalService.list("tenant-a", "PENDING")).thenReturn(List.of(node()));

        ApiResponse<List<AgentApprovalView>> response = controller.list("tenant-a", "PENDING");

        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        assertEquals(1, response.getData().size());
        assertEquals("exec-1", response.getData().getFirst().executionId());
        assertEquals("APPROVAL_REQUESTED", response.getData().getFirst().stage());
    }

    @Test
    void listShouldRejectNonSysAdmin() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));

        ApiResponse<List<AgentApprovalView>> response = controller.list("tenant-a", "PENDING");

        assertEquals(403, response.getCode());
        verify(approvalService, never()).list(anyString(), anyString());
    }

    @Test
    void decideShouldAuditAndApproveNode() {
        bindSession("tenant-a", List.of("SYS_ADMIN"));
        mockAuditExecute();
        AgentApprovalNode approved = node();
        approved.setStatus(AgentApprovalService.STATUS_APPROVED);
        approved.setApproverId("1");
        approved.setDecidedAt(LocalDateTime.now());
        when(approvalService.decide(eq(11L), eq(true), eq("1"), eq("同意执行"))).thenReturn(approved);

        AgentApprovalDecisionCommand request = new AgentApprovalDecisionCommand();
        request.setApproved(true);
        request.setComment("同意执行");

        ApiResponse<AgentApprovalView> response = controller.decide(11L, request, null);

        assertEquals(200, response.getCode());
        assertEquals(AgentApprovalService.STATUS_APPROVED, response.getData().status());
        verify(auditOperationExecutor).execute(eq("AGENT_APPROVAL_DECIDE"), eq("AGENT_APPROVAL"),
                eq(request), any(), any());
    }

    @Test
    void decideShouldRejectNonSysAdmin() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        AgentApprovalDecisionCommand request = new AgentApprovalDecisionCommand();
        request.setApproved(false);

        ApiResponse<AgentApprovalView> response = controller.decide(11L, request, null);

        assertEquals(403, response.getCode());
        verify(approvalService, never()).decide(any(), Mockito.anyBoolean(), anyString(), anyString());
    }

    @Test
    void resumeShouldRunApprovedExecution() {
        bindSession("tenant-a", List.of("SYS_ADMIN"));
        mockAuditExecute();
        AgentApprovalNode approved = node();
        approved.setStatus(AgentApprovalService.STATUS_APPROVED);
        when(approvalService.require(11L)).thenReturn(approved);
        when(agentOrchestrator.resume(any(AgentResumeCommand.class))).thenReturn(new AgentRunResult(
                "exec-1",
                "v1",
                AgentRunStatus.SUCCEEDED,
                "昨日销售额为 5 单",
                "SELECT COUNT(*) FROM orders LIMIT 100",
                List.of(),
                List.of(),
                null,
                12L
        ));

        ApiResponse<AgentApprovalResumeView> response = controller.resume(11L, null);

        assertEquals(200, response.getCode());
        assertEquals("SUCCEEDED", response.getData().status());
        assertEquals("昨日销售额为 5 单", response.getData().answer());
        ArgumentCaptor<AgentResumeCommand> captor = ArgumentCaptor.forClass(AgentResumeCommand.class);
        verify(agentOrchestrator).resume(captor.capture());
        assertEquals("tenant-a", captor.getValue().tenantId());
        assertEquals("exec-1", captor.getValue().executionId());
        assertEquals("1", captor.getValue().approverId());
        verify(auditOperationExecutor).execute(eq("AGENT_APPROVAL_RESUME"), eq("AGENT_APPROVAL"),
                eq("id=11"), any(), any());
    }

    @Test
    void resumeShouldRejectNonSysAdmin() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));

        ApiResponse<AgentApprovalResumeView> response = controller.resume(11L, null);

        assertEquals(403, response.getCode());
        verify(agentOrchestrator, never()).resume(any(AgentResumeCommand.class));
    }

    private AgentApprovalNode node() {
        AgentApprovalNode node = new AgentApprovalNode();
        node.setId(11L);
        node.setTenantId("tenant-a");
        node.setExecutionId("exec-1");
        node.setStage("APPROVAL_REQUESTED");
        node.setStatus(AgentApprovalService.STATUS_PENDING);
        node.setCreatedAt(LocalDateTime.now());
        return node;
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
        session.setRoles(roles);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(session, "token", List.of())
        );
    }
}
