package com.lingsql.audit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 审计详情敏感信息清洗器。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Component
@RequiredArgsConstructor
public class AuditDetailSanitizer {

    private static final String REDACTED_VALUE = "[REDACTED]";
    private static final String UNSTRUCTURED_DETAIL = "{\"redacted\":true,\"reason\":\"UNSTRUCTURED_DETAIL\"}";
    private static final Set<String> SENSITIVE_FIELD_PARTS = Set.of(
            "password", "passwd", "secret", "apikey", "authorization",
            "credential", "privatekey", "accesskey", "cookie", "webhook"
    );
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile(
            "(?i)(password|passwd|pwd|token|secret|api[_-]?key|authorization)\\s*[:=]\\s*[^,;\\s]+"
    );

    private final ObjectMapper objectMapper;

    /**
     * 清洗审计详情。非结构化内容无法可靠判断字段边界，因此按失败关闭原则不保存原文。
     *
     * @param detail 原始详情
     * @return 可安全持久化的详情
     */
    public String sanitize(String detail) {
        if (!StringUtils.hasText(detail)) {
            return detail;
        }
        try {
            JsonNode root = objectMapper.readTree(detail);
            if (root == null || (!root.isObject() && !root.isArray())) {
                return UNSTRUCTURED_DETAIL;
            }
            maskSensitiveFields(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            return UNSTRUCTURED_DETAIL;
        }
    }

    /**
     * 清洗可能进入审计错误字段的非结构化文本。
     *
     * @param value 原始文本
     * @return 脱敏文本
     */
    public String sanitizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String sanitized = BEARER_PATTERN.matcher(value).replaceAll("Bearer " + REDACTED_VALUE);
        return KEY_VALUE_PATTERN.matcher(sanitized).replaceAll("$1=" + REDACTED_VALUE);
    }

    private void maskSensitiveFields(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject() && node instanceof ObjectNode objectNode) {
            List<String> maskedFields = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (isSensitiveField(entry.getKey())) {
                    maskedFields.add(entry.getKey());
                } else {
                    maskSensitiveFields(entry.getValue());
                }
            }
            maskedFields.forEach(field -> objectNode.put(field, REDACTED_VALUE));
            return;
        }
        if (node.isArray()) {
            node.forEach(this::maskSensitiveFields);
        }
    }

    private boolean isSensitiveField(String fieldName) {
        if (!StringUtils.hasText(fieldName)) {
            return false;
        }
        String normalized = fieldName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (SENSITIVE_FIELD_PARTS.stream().anyMatch(normalized::contains)) {
            return true;
        }
        if ("pwd".equals(normalized) || normalized.endsWith("pwd")) {
            return true;
        }
        return "token".equals(normalized)
                || normalized.endsWith("token")
                || normalized.contains("accesstoken")
                || normalized.contains("refreshtoken")
                || normalized.contains("idtoken");
    }
}
