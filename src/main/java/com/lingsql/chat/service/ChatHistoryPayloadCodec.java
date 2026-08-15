package com.lingsql.chat.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.lingsql.chat.model.ChatHistoryPayload;
import com.lingsql.chat.model.ChatTraceStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 聊天历史扩展载荷编解码器。
 *
 * @author 余炘洋
 * @date 2026-04-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatHistoryPayloadCodec {

    private static final TypeReference<List<Map<String, Object>>> DATA_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ChatTraceStep>> TRACE_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    /**
     * 编码历史结果与执行轨迹。
     *
     * @param data  查询结果
     * @param trace 执行轨迹
     * @return JSON 文本
     * @author 余炘洋
     * @date 2026-04-07
     */
    public String encode(List<Map<String, Object>> data, List<ChatTraceStep> trace) {
        ChatHistoryPayload payload = new ChatHistoryPayload(normalizeData(data), normalizeTrace(trace));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            log.warn("历史记录扩展载荷序列化失败: {}", ex.getMessage());
            return "[]";
        }
    }

    /**
     * 解码查询结果数据，兼容旧版纯数组结构。
     *
     * @param json JSON 文本
     * @return 结果数据
     * @author 余炘洋
     * @date 2026-04-07
     */
    public List<Map<String, Object>> decodeData(String json) {
        JsonNode root = readTree(json);
        if (root == null || root.isNull()) {
            return Collections.emptyList();
        }
        if (root.isArray()) {
            return convertData(root);
        }
        return convertData(root.get("data"));
    }

    /**
     * 解码执行轨迹，旧版记录默认返回空列表。
     *
     * @param json JSON 文本
     * @return 执行轨迹
     * @author 余炘洋
     * @date 2026-04-07
     */
    public List<ChatTraceStep> decodeTrace(String json) {
        JsonNode root = readTree(json);
        if (root == null || root.isNull() || root.isArray()) {
            return Collections.emptyList();
        }
        return convertTrace(root.get("trace"));
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            log.warn("历史记录扩展载荷解析失败: {}", ex.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> convertData(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.convertValue(node, DATA_TYPE);
        } catch (IllegalArgumentException ex) {
            log.warn("历史记录结果数据转换失败: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private List<ChatTraceStep> convertTrace(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.convertValue(node, TRACE_TYPE);
        } catch (IllegalArgumentException ex) {
            log.warn("历史记录执行轨迹转换失败: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> normalizeData(List<Map<String, Object>> data) {
        return data == null ? Collections.emptyList() : List.copyOf(data);
    }

    private List<ChatTraceStep> normalizeTrace(List<ChatTraceStep> trace) {
        return trace == null ? Collections.emptyList() : List.copyOf(trace);
    }
}
