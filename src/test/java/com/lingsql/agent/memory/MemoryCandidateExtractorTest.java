package com.lingsql.agent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.billing.invocation.PhysicalBillingException;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.llm.IModelGateway;
import com.lingsql.llm.ModelCallCommand;
import com.lingsql.llm.ModelCallResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 记忆候选提取器测试：模型调用身份、输出解析容错与失败收敛。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
class MemoryCandidateExtractorTest {

    private IModelGateway modelGateway;

    private MemoryCandidateExtractor extractor;

    @BeforeEach
    void setUp() {
        modelGateway = Mockito.mock(IModelGateway.class);
        extractor = new MemoryCandidateExtractor(modelGateway, new ObjectMapper());
    }

    @Test
    void extractShouldCallModelWithDedicatedIdentity() {
        when(modelGateway.chat(any(ModelCallCommand.class))).thenReturn(result("""
                [{"fact": "销售额口径为不含税金额", "confidence": 80}]
                """));

        List<MemoryCandidateExtractor.ExtractedCandidate> candidates =
                extractor.extract(context(), "用户: 口径是不含税的", 5);

        assertEquals(1, candidates.size());
        assertEquals("销售额口径为不含税金额", candidates.get(0).factText());
        assertEquals(80, candidates.get(0).confidence());
        ArgumentCaptor<ModelCallCommand> captor = ArgumentCaptor.forClass(ModelCallCommand.class);
        verify(modelGateway).chat(captor.capture());
        ModelCallCommand command = captor.getValue();
        assertEquals("t1", command.tenantId());
        assertEquals("req-1", command.workflowRequestId());
        assertEquals("memory-curator", command.callerType());
        assertEquals("MEMORY_CANDIDATE", command.stage());
        assertEquals(1, command.logicalCallNo());
    }

    @Test
    void extractShouldParseJsonInsideMarkdownFence() {
        when(modelGateway.chat(any(ModelCallCommand.class))).thenReturn(result("""
                ```json
                [{"fact": "口径A"}, {"fact": "口径B", "confidence": 120}]
                ```
                """));

        List<MemoryCandidateExtractor.ExtractedCandidate> candidates =
                extractor.extract(context(), "用户: 口径A 与口径B", 5);

        assertEquals(2, candidates.size());
        assertEquals(MemoryCandidateExtractor.DEFAULT_CONFIDENCE, candidates.get(0).confidence());
        assertEquals(100, candidates.get(1).confidence());
    }

    @Test
    void extractShouldDropInvalidAndOversizedEntries() {
        String oversized = "口".repeat(MemoryContentGuard.MAX_FACT_LENGTH + 1);
        when(modelGateway.chat(any(ModelCallCommand.class))).thenReturn(result(
                "[{\"fact\": \"   \"}, {\"fact\": \"" + oversized + "\"}, {\"fact\": \"有效事实\"}]"));

        List<MemoryCandidateExtractor.ExtractedCandidate> candidates =
                extractor.extract(context(), "用户: 有效事实", 5);

        assertEquals(1, candidates.size());
        assertEquals("有效事实", candidates.get(0).factText());
    }

    @Test
    void extractShouldReturnEmptyWhenModelOutputIsNotJson() {
        when(modelGateway.chat(any(ModelCallCommand.class))).thenReturn(result("抱歉，我无法提炼。"));

        assertTrue(extractor.extract(context(), "用户: 你好", 5).isEmpty());
    }

    @Test
    void extractShouldSkipModelCallWhenHistoryIsBlank() {
        assertTrue(extractor.extract(context(), "   ", 5).isEmpty());

        verify(modelGateway, never()).chat(any(ModelCallCommand.class));
    }

    @Test
    void extractShouldPropagateBillingException() {
        when(modelGateway.chat(any(ModelCallCommand.class)))
                .thenThrow(new PhysicalBillingException("资金状态机拒绝调用"));

        assertThrows(PhysicalBillingException.class,
                () -> extractor.extract(context(), "用户: 你好", 5));
    }

    @Test
    void extractShouldWrapUnexpectedFailureAsBusinessError() {
        when(modelGateway.chat(any(ModelCallCommand.class))).thenThrow(new IllegalStateException("模型不可用"));

        BizException ex = assertThrows(BizException.class,
                () -> extractor.extract(context(), "用户: 你好", 5));

        assertEquals(ErrorCode.BUSINESS_ERROR, ex.getErrorCode());
    }

    private AgentExecutionContext context() {
        return new AgentExecutionContext("exec-1", "req-1", "t1", 7L, "conv-1");
    }

    private ModelCallResult result(String content) {
        return new ModelCallResult(content, "provider", "model", "v1", 10, 1L, "inv-1", "b1", 1);
    }
}
