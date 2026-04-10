package com.lingsql.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lingsql.chat.entity.ChatHistory;
import com.lingsql.chat.model.ChatHistoryDetailView;
import com.lingsql.chat.model.ChatHistoryView;
import com.lingsql.chat.model.ChatTraceStep;
import com.lingsql.chat.repository.ChatHistoryRepository;
import com.lingsql.common.pagination.PageParamResolver;
import com.lingsql.common.result.PageResult;
import com.lingsql.config.LingSqlProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ChatHistoryServiceTest {

    @Test
    void listHistoryShouldReturnConversationSummaries() {
        ChatHistoryRepository repository = Mockito.mock(ChatHistoryRepository.class);
        StringRedisTemplate stringRedisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        ChatHistory latest = buildHistory(
                22L,
                "conversation-001",
                "销售分析会话",
                "帮我看销售额",
                "本月销售额为 120 万",
                "SELECT * FROM t_order",
                LocalDateTime.of(2026, 4, 10, 10, 30)
        );
        when(repository.findConversationSummaries(eq("TENANT_A"), eq(1001L), eq(null), eq(PageRequest.of(0, 10))))
                .thenReturn(new PageImpl<>(List.of(latest), PageRequest.of(0, 10), 1));

        ChatHistoryService service = createService(repository, stringRedisTemplate);
        PageResult<ChatHistoryView> result = service.listHistory("TENANT_A", 1001L, null, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getList().size());
        assertEquals("conversation-001", result.getList().get(0).getConversationId());
        assertEquals("销售分析会话", result.getList().get(0).getQuestion());
    }

    @Test
    void getHistoryDetailShouldAggregateConversationMessages() {
        ChatHistoryRepository repository = Mockito.mock(ChatHistoryRepository.class);
        StringRedisTemplate stringRedisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        ChatHistory first = buildHistory(
                21L,
                "conversation-001",
                "销售分析会话",
                "帮我看销售额",
                "本月销售额为 120 万",
                "SELECT * FROM t_order",
                LocalDateTime.of(2026, 4, 10, 10, 0)
        );
        ChatHistory second = buildHistory(
                22L,
                "conversation-001",
                "销售分析会话",
                "再看看华东区域",
                "华东区域销售额为 45 万",
                "SELECT * FROM t_order WHERE region = '华东'",
                LocalDateTime.of(2026, 4, 10, 10, 5)
        );
        when(repository.findByIdAndTenantIdAndUserId(22L, "TENANT_A", 1001L)).thenReturn(Optional.of(second));
        when(repository.findByTenantIdAndUserIdAndConversationIdOrderByCreateTimeAscIdAsc("TENANT_A", 1001L, "conversation-001"))
                .thenReturn(List.of(first, second));

        ChatHistoryService service = createService(repository, stringRedisTemplate);
        ChatHistoryDetailView detail = service.getHistoryDetail("TENANT_A", 1001L, 22L);

        assertNotNull(detail);
        assertEquals("conversation-001", detail.getConversationId());
        assertEquals("销售分析会话", detail.getQuestion());
        assertEquals(4, detail.getMessages().size());
        assertEquals("user", detail.getMessages().get(0).getRole());
        assertEquals("assistant", detail.getMessages().get(1).getRole());
        assertEquals("user", detail.getMessages().get(2).getRole());
        assertEquals("assistant", detail.getMessages().get(3).getRole());
        assertEquals("再看看华东区域", detail.getMessages().get(2).getContent());
        assertEquals("华东区域销售额为 45 万", detail.getMessages().get(3).getContent());
    }

    private ChatHistoryService createService(ChatHistoryRepository repository, StringRedisTemplate stringRedisTemplate) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        LingSqlProperties properties = new LingSqlProperties();
        ChatHistoryPayloadCodec codec = new ChatHistoryPayloadCodec(objectMapper);
        PageParamResolver pageParamResolver = new PageParamResolver(properties);
        return new ChatHistoryService(repository, objectMapper, stringRedisTemplate, properties, pageParamResolver, codec);
    }

    private ChatHistory buildHistory(
            Long id,
            String conversationId,
            String conversationTitle,
            String question,
            String answer,
            String sql,
            LocalDateTime createTime
    ) {
        ChatHistory history = new ChatHistory();
        history.setId(id);
        history.setTenantId("TENANT_A");
        history.setUserId(1001L);
        history.setConversationId(conversationId);
        history.setConversationTitle(conversationTitle);
        history.setQuestion(question);
        history.setAnswer(answer);
        history.setSqlText(sql);
        history.setCreateTime(createTime);
        history.setDataJson(createPayload(answer));
        return history;
    }

    private String createPayload(String answer) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        ChatHistoryPayloadCodec codec = new ChatHistoryPayloadCodec(objectMapper);
        return codec.encode(
                List.of(Map.of("answer", answer)),
                List.of(new ChatTraceStep("FINAL", "完成", "已生成结果", "SUCCESS", 1))
        );
    }
}
