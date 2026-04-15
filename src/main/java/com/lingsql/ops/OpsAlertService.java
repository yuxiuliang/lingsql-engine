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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        return sendAlertInternal(title, message, details, null);
    }

    /**
     * 按指定通道发送告警。
     *
     * @param title    告警标题
     * @param message  告警内容
     * @param details  扩展字段
     * @param channels 指定通道列表；为空时按全量已配置通道发送
     * @return 是否发送成功
     * @author 余炘洋
     * @date 2026-04-14
     */
    public boolean sendAlertToChannels(String title, String message, Map<String, Object> details, List<String> channels) {
        return sendAlertInternal(title, message, details, channels);
    }

    private boolean sendAlertInternal(String title,
                                      String message,
                                      Map<String, Object> details,
                                      List<String> channels) {
        LingSqlProperties.Alert alert = properties.getAlert();
        if (alert == null || !alert.isEnabled()) {
            return false;
        }
        List<AlertTarget> targets = resolveTargets(alert, channels);
        if (targets.isEmpty()) {
            return false;
        }
        try {
            Duration connectTimeout = resolveDuration(alert.getConnectTimeout(), Duration.ofSeconds(3));
            Duration readTimeout = resolveDuration(alert.getReadTimeout(), Duration.ofSeconds(5));
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(connectTimeout)
                    .build();
            boolean anySuccess = false;
            boolean allSuccess = true;
            for (AlertTarget target : targets) {
                boolean success = sendToTarget(client, readTimeout, target, title, message, details);
                anySuccess = anySuccess || success;
                allSuccess = allSuccess && success;
            }
            return alert.isRequireAllChannels() ? allSuccess : anySuccess;
        } catch (Exception ex) {
            log.warn("告警发送异常: {}", ex.getMessage());
            return false;
        }
    }

    private boolean sendToTarget(HttpClient client,
                                 Duration readTimeout,
                                 AlertTarget target,
                                 String title,
                                 String message,
                                 Map<String, Object> details) {
        try {
            Map<String, Object> payload = buildPayloadByChannel(target.channel(), title, message, details);
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(target.url()))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .timeout(readTimeout)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                return true;
            }
            log.warn("告警发送失败: channel={}, status={}, body={}", target.channel(), statusCode, response.body());
            return false;
        } catch (Exception ex) {
            log.warn("告警发送异常: channel={}, message={}", target.channel(), ex.getMessage());
            return false;
        }
    }

    private List<AlertTarget> resolveTargets(LingSqlProperties.Alert alert, List<String> channels) {
        List<AlertTarget> targets = new ArrayList<>();
        List<String> normalizedChannels = normalizeChannels(channels);
        addTargetIfPresent(targets, "webhook", alert.getWebhookUrl(), normalizedChannels);
        addTargetIfPresent(targets, "dingtalk", alert.getDingTalkWebhookUrl(), normalizedChannels);
        addTargetIfPresent(targets, "wecom", alert.getWeComWebhookUrl(), normalizedChannels);
        return targets;
    }

    private void addTargetIfPresent(List<AlertTarget> targets, String channel, String url, List<String> channels) {
        if (!StringUtils.hasText(url)) {
            return;
        }
        if (channels != null && !channels.isEmpty() && !channels.contains(channel)) {
            return;
        }
        targets.add(new AlertTarget(channel, url.trim()));
    }

    private List<String> normalizeChannels(List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            return Collections.emptyList();
        }
        return channels.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private Map<String, Object> buildPayloadByChannel(String channel,
                                                      String title,
                                                      String message,
                                                      Map<String, Object> details) {
        return switch (channel) {
            case "dingtalk" -> buildDingTalkPayload(title, message, details);
            case "wecom" -> buildWeComPayload(title, message, details);
            default -> buildGenericPayload(title, message, details);
        };
    }

    private Map<String, Object> buildGenericPayload(String title, String message, Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("app", "lingsql-engine");
        payload.put("title", StringUtils.hasText(title) ? title : "LingSQL 告警");
        payload.put("message", StringUtils.hasText(message) ? message : "系统告警");
        payload.put("level", "WARN");
        payload.put("timestamp", Instant.now().toString());
        payload.put("details", details == null ? Collections.emptyMap() : details);
        return payload;
    }

    private Map<String, Object> buildDingTalkPayload(String title, String message, Map<String, Object> details) {
        String resolvedTitle = StringUtils.hasText(title) ? title : "LingSQL 告警";
        String resolvedMessage = StringUtils.hasText(message) ? message : "系统告警";
        String timestamp = Instant.now().toString();
        String detailMarkdown = renderDetailMarkdown(details, "> - ");
        String text = """
                ### %s
                
                %s
                
                > 时间: %s
                > 详情:
                %s
                """.formatted(resolvedTitle, resolvedMessage, timestamp, detailMarkdown);

        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("title", resolvedTitle);
        markdown.put("text", text);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("msgtype", "markdown");
        payload.put("markdown", markdown);
        return payload;
    }

    private Map<String, Object> buildWeComPayload(String title, String message, Map<String, Object> details) {
        String resolvedTitle = StringUtils.hasText(title) ? title : "LingSQL 告警";
        String resolvedMessage = StringUtils.hasText(message) ? message : "系统告警";
        String timestamp = Instant.now().toString();
        String detailMarkdown = renderDetailMarkdown(details, "- ");
        String content = """
                **%s**
                %s
                
                > 时间: %s
                > 详情:
                %s
                """.formatted(resolvedTitle, resolvedMessage, timestamp, detailMarkdown);

        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("content", content);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("msgtype", "markdown");
        payload.put("markdown", markdown);
        return payload;
    }

    private String renderDetailMarkdown(Map<String, Object> details, String linePrefix) {
        if (details == null || details.isEmpty()) {
            return linePrefix + "无";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            builder.append(linePrefix)
                    .append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append(System.lineSeparator());
        }
        return builder.toString().trim();
    }

    private Duration resolveDuration(Duration source, Duration fallback) {
        if (source == null || source.isZero() || source.isNegative()) {
            return fallback;
        }
        return source;
    }

    private record AlertTarget(String channel, String url) {
    }
}
