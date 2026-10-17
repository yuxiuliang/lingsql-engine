package com.lingsql.agent.orchestration;

import com.lingsql.agent.entity.AgentApprovalNode;
import com.lingsql.agent.entity.AgentCheckpoint;
import com.lingsql.agent.plugin.AgentPluginExecutor;
import com.lingsql.agent.service.AgentApprovalService;
import com.lingsql.agent.service.AgentCheckpointService;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.executor.ResultRefinement;
import com.lingsql.executor.ResultRefiner;
import com.lingsql.executor.SmartSqlExecutor;
import com.lingsql.executor.SqlExecutionResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 受控编排的人工审批与续跑规则测试。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
class ControlledAgentOrchestratorImplTest {

    private final AgentPluginExecutor pluginExecutor = Mockito.mock(AgentPluginExecutor.class);
    private final ResultRefiner resultRefiner = Mockito.mock(ResultRefiner.class);
    private final AgentCheckpointService checkpointService = Mockito.mock(AgentCheckpointService.class);
    private final AgentApprovalService approvalService = Mockito.mock(AgentApprovalService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ControlledAgentOrchestratorImpl orchestrator = new ControlledAgentOrchestratorImpl(
            pluginExecutor,
            resultRefiner,
            checkpointService,
            approvalService,
            objectMapper
    );

    @Test
    void runShouldPauseForApprovalWithoutTouchingSecureQuery() {
        Mockito.when(approvalService.requestApproval(eq("tenant-a"), anyString(), eq(AgentRunStage.APPROVAL_REQUESTED)))
                .thenAnswer(invocation -> approval("PENDING"));

        AgentRunResult result = orchestrator.run(command(true));

        assertEquals(AgentRunStatus.PENDING_APPROVAL, result.status());
        assertNotNull(result.executionId());
        assertNull(result.failureCode());
        assertFalse(result.querySucceeded());
        assertTrue(result.data().isEmpty());
        verifyNoInteractions(pluginExecutor, resultRefiner);
        verify(approvalService).requestApproval(eq("tenant-a"), eq(result.executionId()),
                eq(AgentRunStage.APPROVAL_REQUESTED));

        ArgumentCaptor<String> stageCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(checkpointService).save(eq("tenant-a"), eq(result.executionId()), eq("conv-1"),
                stageCaptor.capture(), statusCaptor.capture(), payloadCaptor.capture());
        assertEquals(AgentRunStage.APPROVAL_REQUESTED.name(), stageCaptor.getValue());
        assertEquals(AgentApprovalService.STATUS_PENDING, statusCaptor.getValue());
        assertTrue(payloadCaptor.getValue().contains("统计昨日销售额"));
    }

    @Test
    void runShouldRejectIncompleteCommand() {
        BizException exception = assertThrows(BizException.class, () -> orchestrator.run(
                new AgentRunCommand("tenant-a", 9L, "conv-1", "  ", "WEB", "req-1", true)));

        assertEquals(ErrorCode.INVALID_PARAM, exception.getErrorCode());
        verifyNoInteractions(pluginExecutor, approvalService);
    }

    @Test
    void resumeShouldRequireCheckpoint() {
        when(checkpointService.latest("tenant-a", "exec-1")).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> orchestrator.resume(new AgentResumeCommand("tenant-a", "exec-1", "7")));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(pluginExecutor, approvalService);
    }

    @Test
    void resumeShouldRejectStagesAfterSecureQuery() {
        for (AgentRunStage stage : new AgentRunStage[]{
                AgentRunStage.SQL_ARCHITECT, AgentRunStage.SECURE_QUERY,
                AgentRunStage.ANALYST, AgentRunStage.RESUME_COMPLETED}) {
            when(checkpointService.latest("tenant-a", "exec-1"))
                    .thenReturn(Optional.of(checkpoint(stage, payload())));
            Mockito.reset(approvalService);

            BizException exception = assertThrows(BizException.class,
                    () -> orchestrator.resume(new AgentResumeCommand("tenant-a", "exec-1", "7")));

            assertEquals(ErrorCode.CONFLICT, exception.getErrorCode(), "阶段应拒绝恢复: " + stage);
            verifyNoInteractions(pluginExecutor);
        }
    }

    @Test
    void resumeShouldRequireApprovedNode() {
        when(checkpointService.latest("tenant-a", "exec-1"))
                .thenReturn(Optional.of(checkpoint(AgentRunStage.APPROVAL_REQUESTED, payload())));
        when(approvalService.latest("tenant-a", "exec-1"))
                .thenReturn(Optional.of(approval(AgentApprovalService.STATUS_PENDING)));

        BizException exception = assertThrows(BizException.class,
                () -> orchestrator.resume(new AgentResumeCommand("tenant-a", "exec-1", "7")));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        verifyNoInteractions(pluginExecutor);
    }

    @Test
    void resumeShouldRequireApprovalNode() {
        when(checkpointService.latest("tenant-a", "exec-1"))
                .thenReturn(Optional.of(checkpoint(AgentRunStage.APPROVAL_REQUESTED, payload())));
        when(approvalService.latest("tenant-a", "exec-1")).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> orchestrator.resume(new AgentResumeCommand("tenant-a", "exec-1", "7")));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        verifyNoInteractions(pluginExecutor);
    }

    @Test
    void resumeShouldRejectMissingPayload() {
        when(checkpointService.latest("tenant-a", "exec-1"))
                .thenReturn(Optional.of(checkpoint(AgentRunStage.APPROVAL_REQUESTED, null)));
        when(approvalService.latest("tenant-a", "exec-1"))
                .thenReturn(Optional.of(approval(AgentApprovalService.STATUS_APPROVED)));

        BizException exception = assertThrows(BizException.class,
                () -> orchestrator.resume(new AgentResumeCommand("tenant-a", "exec-1", "7")));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        verifyNoInteractions(pluginExecutor);
    }

    @Test
    void resumeShouldRejectUnparsablePayload() {
        when(checkpointService.latest("tenant-a", "exec-1"))
                .thenReturn(Optional.of(checkpoint(AgentRunStage.APPROVAL_REQUESTED, "not-a-json")));
        when(approvalService.latest("tenant-a", "exec-1"))
                .thenReturn(Optional.of(approval(AgentApprovalService.STATUS_APPROVED)));

        BizException exception = assertThrows(BizException.class,
                () -> orchestrator.resume(new AgentResumeCommand("tenant-a", "exec-1", "7")));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        verifyNoInteractions(pluginExecutor);
    }

    @Test
    void resumeShouldReEnterSecureQueryAndRecordCompletion() {
        when(checkpointService.latest("tenant-a", "exec-1"))
                .thenReturn(Optional.of(checkpoint(AgentRunStage.APPROVAL_REQUESTED, payload())));
        when(approvalService.latest("tenant-a", "exec-1"))
                .thenReturn(Optional.of(approval(AgentApprovalService.STATUS_APPROVED)));
        when(pluginExecutor.execute(
                eq(SmartSqlExecutor.PLUGIN_ID),
                any(AgentExecutionContext.class),
                any(AgentRunControl.class),
                any(SqlQueryTask.class),
                eq(SqlExecutionResult.class)
        )).thenReturn(SqlExecutionResult.success(
                "SELECT COUNT(*) FROM orders LIMIT 100",
                List.of(Map.of("total", 5))
        ));
        when(resultRefiner.refineWithMetadata(any(AgentExecutionContext.class), anyString(), any(SqlExecutionResult.class)))
                .thenReturn(new ResultRefinement("昨日销售额为 5 单", true, 8L));

        AgentRunResult result = orchestrator.resume(new AgentResumeCommand("tenant-a", "exec-1", "7"));

        assertEquals("exec-1", result.executionId());
        assertEquals(AgentRunStatus.SUCCEEDED, result.status());
        assertEquals("昨日销售额为 5 单", result.answer());
        assertEquals("SELECT COUNT(*) FROM orders LIMIT 100", result.sql());

        // 续跑必须重新完整经过安全查询模块（生成、审计、执行不可拆分）
        ArgumentCaptor<SqlQueryTask> taskCaptor = ArgumentCaptor.forClass(SqlQueryTask.class);
        verify(pluginExecutor).execute(
                eq(SmartSqlExecutor.PLUGIN_ID),
                any(AgentExecutionContext.class),
                any(AgentRunControl.class),
                taskCaptor.capture(),
                eq(SqlExecutionResult.class)
        );
        assertEquals("统计昨日销售额", taskCaptor.getValue().question());
        assertEquals("WEB", taskCaptor.getValue().requestSource());

        // 续跑结束后写入完成检查点，防止重复续跑
        verify(checkpointService).save(eq("tenant-a"), eq("exec-1"), eq("conv-1"),
                eq(AgentRunStage.RESUME_COMPLETED.name()), eq(AgentRunStatus.SUCCEEDED.name()), anyString());
    }

    @Test
    void resumeShouldRecordCompletionOnFailedPipeline() {
        when(checkpointService.latest("tenant-a", "exec-1"))
                .thenReturn(Optional.of(checkpoint(AgentRunStage.APPROVAL_REQUESTED, payload())));
        when(approvalService.latest("tenant-a", "exec-1"))
                .thenReturn(Optional.of(approval(AgentApprovalService.STATUS_APPROVED)));
        when(pluginExecutor.execute(
                eq(SmartSqlExecutor.PLUGIN_ID),
                any(AgentExecutionContext.class),
                any(AgentRunControl.class),
                any(SqlQueryTask.class),
                eq(SqlExecutionResult.class)
        )).thenThrow(new IllegalStateException("数据库连接中断"));

        AgentRunResult result = orchestrator.resume(new AgentResumeCommand("tenant-a", "exec-1", "7"));

        assertEquals(AgentRunStatus.FAILED, result.status());
        verify(checkpointService).save(eq("tenant-a"), eq("exec-1"), eq("conv-1"),
                eq(AgentRunStage.RESUME_COMPLETED.name()), eq(AgentRunStatus.FAILED.name()), anyString());
        verify(resultRefiner, never()).refineWithMetadata(any(AgentExecutionContext.class), anyString(), any());
    }

    private AgentRunCommand command(boolean requireApproval) {
        return new AgentRunCommand("tenant-a", 9L, "conv-1", "统计昨日销售额", "WEB", "req-1", requireApproval);
    }

    private AgentCheckpoint checkpoint(AgentRunStage stage, String payload) {
        AgentCheckpoint checkpoint = new AgentCheckpoint();
        checkpoint.setId(21L);
        checkpoint.setTenantId("tenant-a");
        checkpoint.setExecutionId("exec-1");
        checkpoint.setConversationId("conv-1");
        checkpoint.setStage(stage.name());
        checkpoint.setStatus("PENDING");
        checkpoint.setPayloadJson(payload);
        checkpoint.setCreatedAt(LocalDateTime.now());
        return checkpoint;
    }

    private AgentApprovalNode approval(String status) {
        AgentApprovalNode node = new AgentApprovalNode();
        node.setId(11L);
        node.setTenantId("tenant-a");
        node.setExecutionId("exec-1");
        node.setStage(AgentRunStage.APPROVAL_REQUESTED.name());
        node.setStatus(status);
        node.setCreatedAt(LocalDateTime.now());
        return node;
    }

    private String payload() {
        return "{\"question\":\"统计昨日销售额\",\"requestSource\":\"WEB\",\"requestId\":\"req-1\","
                + "\"userId\":9,\"conversationId\":\"conv-1\"}";
    }
}
