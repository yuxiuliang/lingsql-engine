package com.lingsql.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.config.LingSqlProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运维告警服务。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpsAlertService {

    private final LingSqlProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 发送告警。
     *
     * @param title   告警标题
     * @param message 告警内容
     * @param details 扩展字段
     * @return 是否发送成功
     * @author 余炘洋
     * @date 2026-02-27
     */
    public boolean sendAlert(String title, String message, Map<String, Object> details) {
        LingSqlProperties.Alert alert = properties.getAlert();
        if (alert == null || !alert.isEnabled() || !StringUtils.hasText(alert.getWebhookUrl())) {
            return false;
        }
        try {
            Duration connectTimeout = resolveDuration(alert.getConnectTimeout(), Duration.ofSeconds(3));
            Duration readTimeout = resolveDuration(alert.getReadTimeout(), Duration.ofSeconds(5));
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(connectTimeout)
                    .build();
            Map<String, Object> payload = buildPayload(title, message, details);
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(alert.getWebhookUrl().trim()))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .timeout(readTimeout)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                return true;
            }
            log.warn("告警发送失败: status={}, body={}", statusCode, response.body());
            return false;
        } catch (Exception ex) {
            log.warn("告警发送异常: {}", ex.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildPayload(String title, String message, Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("app", "lingsql-engine");
        payload.put("title", StringUtils.hasText(title) ? title : "LingSQL 告警");
        payload.put("message", StringUtils.hasText(message) ? message : "系统告警");
        payload.put("level", "WARN");
        payload.put("timestamp", Instant.now().toString());
        payload.put("details", details == null ? Map.of() : details);
        return payload;
    }

    private Duration resolveDuration(Duration source, Duration fallback) {
        if (source == null || source.isZero() || source.isNegative()) {
            return fallback;
        }
        return source;
    }
}

