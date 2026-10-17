package com.lingsql.agent.service;

import com.lingsql.agent.entity.AgentApprovalNode;
import com.lingsql.agent.orchestration.AgentRunStage;
import com.lingsql.agent.repository.AgentApprovalNodeRepository;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 人工审批服务规则测试。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
class AgentApprovalServiceTest {

    private final AgentApprovalNodeRepository repository = Mockito.mock(AgentApprovalNodeRepository.class);
    private final AgentApprovalService service = new AgentApprovalService(repository);

    @Test
    void requestApprovalShouldPersistPendingNode() {
        when(repository.save(any(AgentApprovalNode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentApprovalNode node = service.requestApproval("tenant-a", "exec-1", AgentRunStage.APPROVAL_REQUESTED);

        assertEquals("tenant-a", node.getTenantId());
        assertEquals("exec-1", node.getExecutionId());
        assertEquals("APPROVAL_REQUESTED", node.getStage());
        assertEquals(AgentApprovalService.STATUS_PENDING, node.getStatus());
        assertEquals(null, node.getApproverId());
    }

    @Test
    void requestApprovalShouldRejectNonApprovalStage() {
        BizException exception = assertThrows(BizException.class,
                () -> service.requestApproval("tenant-a", "exec-1", AgentRunStage.SECURE_QUERY));

        assertEquals(ErrorCode.INVALID_PARAM, exception.getErrorCode());
        verify(repository, never()).save(any(AgentApprovalNode.class));
    }

    @Test
    void requestApprovalShouldRejectBlankExecutionId() {
        BizException exception = assertThrows(BizException.class,
                () -> service.requestApproval("tenant-a", "  ", AgentRunStage.APPROVAL_REQUESTED));

        assertEquals(ErrorCode.INVALID_PARAM, exception.getErrorCode());
    }

    @Test
    void decideShouldApprovePendingNode() {
        AgentApprovalNode node = node(AgentApprovalService.STATUS_PENDING);
        when(repository.findById(1L)).thenReturn(Optional.of(node));
        when(repository.save(any(AgentApprovalNode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentApprovalNode decided = service.decide(1L, true, " 99 ", "同意执行");

        assertEquals(AgentApprovalService.STATUS_APPROVED, decided.getStatus());
        assertEquals("99", decided.getApproverId());
        assertEquals("同意执行", decided.getComment());
        assertEquals(true, decided.getDecidedAt() != null);
    }

    @Test
    void decideShouldRejectAlreadyDecidedNode() {
        AgentApprovalNode node = node(AgentApprovalService.STATUS_APPROVED);
        when(repository.findById(1L)).thenReturn(Optional.of(node));

        BizException exception = assertThrows(BizException.class,
                () -> service.decide(1L, false, "99", "驳回"));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        verify(repository, never()).save(any(AgentApprovalNode.class));
    }

    @Test
    void decideShouldRejectMissingNode() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> service.decide(404L, true, "99", null));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void decideShouldRejectBlankApprover() {
        BizException exception = assertThrows(BizException.class,
                () -> service.decide(1L, true, "   ", null));

        assertEquals(ErrorCode.INVALID_PARAM, exception.getErrorCode());
        verify(repository, never()).findById(any());
    }

    @Test
    void decideShouldRejectOverlongComment() {
        BizException exception = assertThrows(BizException.class,
                () -> service.decide(1L, true, "99", "x".repeat(513)));

        assertEquals(ErrorCode.INVALID_PARAM, exception.getErrorCode());
    }

    @Test
    void listShouldRejectUnknownStatus() {
        BizException exception = assertThrows(BizException.class, () -> service.list("tenant-a", "DONE"));

        assertEquals(ErrorCode.INVALID_PARAM, exception.getErrorCode());
    }

    @Test
    void listShouldQueryByTenantAndStatus() {
        AgentApprovalNode node = node(AgentApprovalService.STATUS_PENDING);
        when(repository.findTop100ByTenantIdAndStatusOrderByCreatedAtDesc("tenant-a", AgentApprovalService.STATUS_PENDING))
                .thenReturn(List.of(node));

        List<AgentApprovalNode> nodes = service.list("tenant-a", "pending");

        assertEquals(1, nodes.size());
        verify(repository).findTop100ByTenantIdAndStatusOrderByCreatedAtDesc("tenant-a", AgentApprovalService.STATUS_PENDING);
    }

    @Test
    void listShouldQueryAllWhenNoCondition() {
        when(repository.findTop100ByOrderByCreatedAtDesc()).thenReturn(List.of(node(AgentApprovalService.STATUS_REJECTED)));

        List<AgentApprovalNode> nodes = service.list(null, null);

        assertEquals(1, nodes.size());
        verify(repository).findTop100ByOrderByCreatedAtDesc();
    }

    @Test
    void latestShouldDelegateToRepository() {
        when(repository.findTopByTenantIdAndExecutionIdOrderByCreatedAtDesc(eq("tenant-a"), eq("exec-1")))
                .thenReturn(Optional.of(node(AgentApprovalService.STATUS_PENDING)));

        assertEquals(true, service.latest("tenant-a", "exec-1").isPresent());
    }

    private AgentApprovalNode node(String status) {
        AgentApprovalNode node = new AgentApprovalNode();
        node.setId(1L);
        node.setTenantId("tenant-a");
        node.setExecutionId("exec-1");
        node.setStage(AgentRunStage.APPROVAL_REQUESTED.name());
        node.setStatus(status);
        node.setCreatedAt(LocalDateTime.now());
        return node;
    }
}
