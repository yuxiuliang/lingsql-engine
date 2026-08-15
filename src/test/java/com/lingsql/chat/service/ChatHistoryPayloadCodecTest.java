package com.lingsql.chat.service;

import tools.jackson.databind.ObjectMapper;
import com.lingsql.chat.model.ChatTraceStep;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatHistoryPayloadCodecTest {

    private final ChatHistoryPayloadCodec codec = new ChatHistoryPayloadCodec(new ObjectMapper());

    @Test
    void decodeLegacyArrayShouldRemainCompatible() {
        String legacyJson = "[{\"cnt\":1}]";

        List<Map<String, Object>> data = codec.decodeData(legacyJson);
        List<ChatTraceStep> trace = codec.decodeTrace(legacyJson);

        assertEquals(1, data.size());
        assertEquals(1, data.get(0).get("cnt"));
        assertTrue(trace.isEmpty());
    }

    @Test
    void encodeShouldPersistTraceAndData() {
        List<Map<String, Object>> data = List.of(Map.of("cnt", 2));
        List<ChatTraceStep> trace = List.of(
                new ChatTraceStep("THOUGHT", "解析问题", "已识别用户需要查看汇总结果。", "SUCCESS", 1)
        );

        String json = codec.encode(data, trace);

        assertEquals(2, codec.decodeData(json).get(0).get("cnt"));
        assertEquals(1, codec.decodeTrace(json).size());
        assertEquals("解析问题", codec.decodeTrace(json).get(0).getTitle());
    }
}
