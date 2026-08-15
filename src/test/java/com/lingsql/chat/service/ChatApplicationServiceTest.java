package com.lingsql.chat.service;

import com.lingsql.chat.entity.ChatHistory;
import com.lingsql.chat.model.ChatRequest;
import com.lingsql.chat.model.ChatResult;
import com.lingsql.chat.model.ChatTraceStep;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.context.SessionInfo;
import com.lingsql.context.TenantContext;
import com.lingsql.executor.ResultRefiner;
import com.lingsql.executor.SmartSqlExecutor;
import com.lingsql.executor.SqlExecutionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatApplicationServiceTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void askShouldSanitizeExecutionFailureForUser() {
        SmartSqlExecutor sqlExecutor = Mockito.mock(SmartSqlExecutor.class);
        ResultRefiner resultRefiner = Mockito.mock(ResultRefiner.class);
        ChatHistoryService chatHistoryService = Mockito.mock(ChatHistoryService.class);
        ChatMemoryService chatMemoryService = Mockito.mock(ChatMemoryService.class);

        when(sqlExecutor.execute("TENANT_A", "查销售额", "WEB"))
                .thenReturn(SqlExecutionResult.failure(
                        "SELECT * FROM t_order",
                        "Communications link failure: connect timed out",
                        List.of(new ChatTraceStep("OBSERVATION", "首轮执行失败", "系统已触发安全失败提示。", "FAILURE", 1))
                ));
        when(resultRefiner.refine(anyString(), any(SqlExecutionResult.class))).thenReturn("请稍后重试");

        ChatHistory history = new ChatHistory();
        history.setId(99L);
        when(chatHistoryService.saveHistory(anyString(), anyLong(), anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(history);

        ChatApplicationService service = new ChatApplicationService(
                sqlExecutor,
                resultRefiner,
                chatHistoryService,
                chatMemoryService
        );
        TenantContext.setSessionInfo(new SessionInfo("TENANT_A", 1001L));

        ChatRequest request = new ChatRequest();
        request.setQuestion("查销售额");
        request.setConversationId("conversation-001");
        ChatResult result = service.ask(request);

        assertNotNull(result);
        assertEquals("ERROR:SQL 执行失败，请检查查询条件", result.getSql());
        assertEquals(99L, result.getHistoryId());
        assertEquals(1, result.getTrace().size());
        assertEquals("conversation-001", result.getConversationId());
        verify(chatMemoryService, never()).saveSuccessExchange(anyString(), anyString(), anyString(), anyString(), any());

        verify(resultRefiner, never()).refine(anyString(), any(SqlExecutionResult.class));
        verify(resultRefiner, never()).refineWithMetadata(anyString(), any(SqlExecutionResult.class));
    }

    @Test
    void askShouldExposeTenantConfigGuidanceWhenTenantDbConfigMissing() {
        SmartSqlExecutor sqlExecutor = Mockito.mock(SmartSqlExecutor.class);
        ResultRefiner resultRefiner = Mockito.mock(ResultRefiner.class);
        ChatHistoryService chatHistoryService = Mockito.mock(ChatHistoryService.class);
        ChatMemoryService chatMemoryService = Mockito.mock(ChatMemoryService.class);

        when(sqlExecutor.execute("TENANT_A", "查销售额", "WEB"))
                .thenThrow(new BizException(ErrorCode.TENANT_DB_CONFIG_NOT_FOUND, "租户数据源配置不存在，请先完成租户数据库连接配置"));

        ChatHistory history = new ChatHistory();
        history.setId(100L);
        when(chatHistoryService.saveHistory(anyString(), anyLong(), anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(history);

        ChatApplicationService service = new ChatApplicationService(
                sqlExecutor,
                resultRefiner,
                chatHistoryService,
                chatMemoryService
        );
        TenantContext.setSessionInfo(new SessionInfo("TENANT_A", 1001L));

        ChatRequest request = new ChatRequest();
        request.setQuestion("查销售额");
        request.setConversationId("conversation-002");
        ChatResult result = service.ask(request);

        assertNotNull(result);
        assertEquals("ERROR:当前租户未完成数据源配置，请联系管理员补充数据库连接信息", result.getSql());
        assertEquals("当前租户未完成数据源配置，请联系管理员补充数据库连接信息", result.getAnswer());
        assertEquals(100L, result.getHistoryId());
        assertEquals(3, result.getTrace().size());
        assertEquals("租户数据源未配置", result.getTrace().get(1).getTitle());
        verify(resultRefiner, never()).refine(anyString(), any(SqlExecutionResult.class));
        verify(chatMemoryService, never()).saveSuccessExchange(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void askShouldRejectBlankQuestionBeforeExecution() {
        SmartSqlExecutor sqlExecutor = Mockito.mock(SmartSqlExecutor.class);
        ResultRefiner resultRefiner = Mockito.mock(ResultRefiner.class);
        ChatHistoryService chatHistoryService = Mockito.mock(ChatHistoryService.class);
        ChatMemoryService chatMemoryService = Mockito.mock(ChatMemoryService.class);

        ChatApplicationService service = new ChatApplicationService(
                sqlExecutor,
                resultRefiner,
                chatHistoryService,
                chatMemoryService
        );
        TenantContext.setSessionInfo(new SessionInfo("TENANT_A", 1001L));

        ChatRequest request = new ChatRequest();
        request.setQuestion("   ");

        BizException ex = assertThrows(BizException.class, () -> service.ask(request));

        assertEquals(ErrorCode.INVALID_PARAM, ex.getErrorCode());
        assertEquals("问题不能为空", ex.getMessage());
        verify(sqlExecutor, never()).execute(anyString(), anyString(), anyString());
        verify(chatHistoryService, never()).saveHistory(anyString(), anyLong(), anyString(), anyString(), anyString(), anyString(), any(), any());
    }
}
