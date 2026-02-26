package com.lingsql.chat.service;

import com.lingsql.chat.entity.ChatHistory;
import com.lingsql.chat.model.ChatRequest;
import com.lingsql.chat.model.ChatResult;
import com.lingsql.context.SessionInfo;
import com.lingsql.context.TenantContext;
import com.lingsql.executor.ResultRefiner;
import com.lingsql.executor.SmartSqlExecutor;
import com.lingsql.executor.SqlExecutionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
                        "Communications link failure: connect timed out"
                ));
        when(resultRefiner.refine(anyString(), any(SqlExecutionResult.class))).thenReturn("请稍后重试");

        ChatHistory history = new ChatHistory();
        history.setId(99L);
        when(chatHistoryService.saveHistory(anyString(), anyLong(), anyString(), anyString(), anyString(), any()))
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
        ChatResult result = service.ask(request);

        assertNotNull(result);
        assertEquals("ERROR:SQL 执行失败，请检查查询条件", result.getSql());
        assertEquals(99L, result.getHistoryId());
        verify(chatMemoryService, never()).saveSuccessExchange(anyString(), anyString(), anyString(), anyString(), any());

        ArgumentCaptor<SqlExecutionResult> resultCaptor = ArgumentCaptor.forClass(SqlExecutionResult.class);
        verify(resultRefiner).refine(Mockito.eq("查销售额"), resultCaptor.capture());
        assertEquals("SQL 执行失败，请检查查询条件", resultCaptor.getValue().getErrorMessage());
    }
}

