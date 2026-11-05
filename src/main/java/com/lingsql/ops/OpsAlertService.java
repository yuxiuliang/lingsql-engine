package com.lingsql.ops;

import tools.jackson.databind.ObjectMapper;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.ops.model.AlertDispatchChannelResult;
import com.lingsql.ops.model.AlertDispatchResult;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
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

    /**
     * 默认消息级别。告警类消息沿用 WARN；日报等「正常产出」可显式传 INFO。
     */
    private static final String DEFAULT_ALERT_LEVEL = "WARN";

    /**
     * 客户可见级别（正常产出，如日报）。此级别在 markdown 通道只渲染业务预览。
     */
    private static final String CUSTOMER_FACING_LEVEL = "INFO";

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
        return sendAlertWithResult(title, message, details).success();
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
        return sendAlertToChannelsWithResult(title, message, details, channels).success();
    }

    /**
     * 发送告警并返回结构化投递结果。
     *
     * @param title   告警标题
     * @param message 告警内容
     * @param details 扩展字段
     * @return 投递结果
     * @author 余炘洋
     * @date 2026-04-15
     */
    public AlertDispatchResult sendAlertWithResult(String title, String message, Map<String, Object> details) {
        return sendAlertWithResult(title, message, details, DEFAULT_ALERT_LEVEL);
    }

    /**
     * 发送告警并返回结构化投递结果，可指定消息级别。
     *
     * <p>日报等「正常产出」的推送应传 {@code INFO}，否则客户侧按级别着色时会把正常日报显示成告警。</p>
     *
     * @param title   告警标题
     * @param message 告警内容
     * @param details 扩展字段
     * @param level   消息级别；为空时按默认级别处理
     * @return 投递结果
     * @author 余炘洋
     * @date 2026-10-26
     */
    public AlertDispatchResult sendAlertWithResult(String title, String message, Map<String, Object> details, String level) {
        return sendAlertInternal(title, message, details, null, level);
    }

    /**
     * 按指定通道发送告警并返回结构化投递结果。
     *
     * @param title    告警标题
     * @param message  告警内容
     * @param details  扩展字段
     * @param channels 指定通道
     * @return 投递结果
     * @author 余炘洋
     * @date 2026-04-15
     */
    public AlertDispatchResult sendAlertToChannelsWithResult(String title,
                                                             String message,
                                                             Map<String, Object> details,
                                                             List<String> channels) {
        return sendAlertToChannelsWithResult(title, message, details, channels, DEFAULT_ALERT_LEVEL);
    }

    /**
     * 按指定通道发送告警并返回结构化投递结果，可指定消息级别。
     *
     * @param title    告警标题
     * @param message  告警内容
     * @param details  扩展字段
     * @param channels 指定通道
     * @param level    消息级别；为空时按默认级别处理
     * @return 投递结果
     * @author 余炘洋
     * @date 2026-10-26
     */
    public AlertDispatchResult sendAlertToChannelsWithResult(String title,
                                                             String message,
                                                             Map<String, Object> details,
                                                             List<String> channels,
                                                             String level) {
        return sendAlertInternal(title, message, details, channels, level);
    }

    /**
     * 当前系统是否启用了告警总开关。
     *
     * @return 是否启用
     * @author 余炘洋
     * @date 2026-04-15
     */
    public boolean isAlertEnabled() {
        LingSqlProperties.Alert alert = properties.getAlert();
        return alert != null && alert.isEnabled();
    }

    /**
     * 查询当前已配置的通知通道。
     *
     * @return 已配置通道
     * @author 余炘洋
     * @date 2026-04-15
     */
    public List<String> getConfiguredChannels() {
        LingSqlProperties.Alert alert = properties.getAlert();
        if (alert == null) {
            return Collections.emptyList();
        }
        List<String> configuredChannels = new ArrayList<>();
        if (StringUtils.hasText(alert.getWebhookUrl())) {
            configuredChannels.add("webhook");
        }
        if (StringUtils.hasText(alert.getDingTalkWebhookUrl())) {
            configuredChannels.add("dingtalk");
        }
        if (StringUtils.hasText(alert.getWeComWebhookUrl())) {
            configuredChannels.add("wecom");
        }
        return configuredChannels;
    }

    /**
     * 查询当前未配置的通知通道。
     *
     * @return 未配置通道
     * @author 余炘洋
     * @date 2026-04-15
     */
    public List<String> getUnconfiguredChannels() {
        List<String> configuredChannels = getConfiguredChannels();
        return List.of("webhook", "dingtalk", "wecom").stream()
                .filter(channel -> !configuredChannels.contains(channel))
                .toList();
    }

    private AlertDispatchResult sendAlertInternal(String title,
                                                  String message,
                                                  Map<String, Object> details,
                                                  List<String> channels,
                                                  String level) {
        LingSqlProperties.Alert alert = properties.getAlert();
        if (alert == null || !alert.isEnabled()) {
            return AlertDispatchResult.skipped("系统告警总开关未启用");
        }
        List<String> normalizedChannels = normalizeChannels(channels);
        List<AlertTarget> targets = resolveTargets(alert, normalizedChannels);
        if (targets.isEmpty()) {
            return AlertDispatchResult.skipped(buildNoTargetSummary(normalizedChannels));
        }
        try {
            Duration connectTimeout = resolveDuration(alert.getConnectTimeout(), Duration.ofSeconds(3));
            Duration readTimeout = resolveDuration(alert.getReadTimeout(), Duration.ofSeconds(5));
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(connectTimeout)
                    .build();
            List<AlertDispatchChannelResult> channelResults = new ArrayList<>();
            boolean anySuccess = false;
            boolean allSuccess = true;
            for (AlertTarget target : targets) {
                AlertDispatchChannelResult channelResult = sendToTarget(client, readTimeout, target, title, message, details, level);
                channelResults.add(channelResult);
                anySuccess = anySuccess || channelResult.success();
                allSuccess = allSuccess && channelResult.success();
            }
            return AlertDispatchResult.completed(alert.isRequireAllChannels() ? allSuccess : anySuccess, channelResults);
        } catch (Exception ex) {
            log.warn("告警发送异常: {}", ex.getMessage());
            return AlertDispatchResult.skipped("告警客户端初始化失败: " + safeMessage(ex.getMessage()));
        }
    }

    private AlertDispatchChannelResult sendToTarget(HttpClient client,
                                                    Duration readTimeout,
                                                    AlertTarget target,
                                                    String title,
                                                    String message,
                                                    Map<String, Object> details,
                                                    String level) {
        try {
            Map<String, Object> payload = buildPayloadByChannel(target.channel(), title, message, details, level);
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
                return AlertDispatchChannelResult.success(target.channel(), statusCode);
            }
            log.warn("告警发送失败: channel={}, status={}, body={}", target.channel(), statusCode, response.body());
            return AlertDispatchChannelResult.failure(target.channel(), statusCode, abbreviate(response.body(), 160));
        } catch (Exception ex) {
            log.warn("告警发送异常: channel={}, message={}", target.channel(), ex.getMessage());
            return AlertDispatchChannelResult.failure(target.channel(), null, safeMessage(ex.getMessage()));
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

    private String buildNoTargetSummary(List<String> normalizedChannels) {
        if (normalizedChannels == null || normalizedChannels.isEmpty()) {
            return "未配置可用通知通道";
        }
        return "指定通道未配置或不可用: " + String.join(", ", normalizedChannels);
    }

    private Map<String, Object> buildPayloadByChannel(String channel,
                                                      String title,
                                                      String message,
                                                      Map<String, Object> details,
                                                      String level) {
        return switch (channel) {
            case "dingtalk" -> buildDingTalkPayload(title, message, details, level);
            case "wecom" -> buildWeComPayload(title, message, details, level);
            default -> buildGenericPayload(title, message, details, level);
        };
    }

    private Map<String, Object> buildGenericPayload(String title, String message, Map<String, Object> details, String level) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("app", "lingsql-engine");
        payload.put("title", StringUtils.hasText(title) ? title : "LingSQL 告警");
        payload.put("message", StringUtils.hasText(message) ? message : "系统告警");
        payload.put("level", StringUtils.hasText(level) ? level : DEFAULT_ALERT_LEVEL);
        // 用带偏移的本地时间（如 2026-10-26T20:52:02+08:00），不要退回 UTC：
        // 同一 payload 里的 details.executeTime 是本地时间，混用会让客户看到差 8 小时的两个时间。
        payload.put("timestamp", OffsetDateTime.now().toString());
        payload.put("details", details == null ? Collections.emptyMap() : details);
        return payload;
    }

    private Map<String, Object> buildDingTalkPayload(String title, String message, Map<String, Object> details, String level) {
        String resolvedTitle = StringUtils.hasText(title) ? title : "LingSQL 告警";
        String resolvedMessage = StringUtils.hasText(message) ? message : "系统告警";
        String timestamp = OffsetDateTime.now().toString();
        String detailMarkdown = renderMarkdownDetails(details, "> - ", level);
        String detailLabel = isCustomerFacingLevel(level) ? "结果预览" : "详情";
        String text = """
                ### %s
                
                %s
                
                > 时间: %s
                > %s:
                %s
                """.formatted(resolvedTitle, resolvedMessage, timestamp, detailLabel, detailMarkdown);

        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("title", resolvedTitle);
        markdown.put("text", text);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("msgtype", "markdown");
        payload.put("markdown", markdown);
        return payload;
    }

    private Map<String, Object> buildWeComPayload(String title, String message, Map<String, Object> details, String level) {
        String resolvedTitle = StringUtils.hasText(title) ? title : "LingSQL 告警";
        String resolvedMessage = StringUtils.hasText(message) ? message : "系统告警";
        String timestamp = OffsetDateTime.now().toString();
        String detailMarkdown = renderMarkdownDetails(details, "- ", level);
        String detailLabel = isCustomerFacingLevel(level) ? "结果预览" : "详情";
        String content = """
                **%s**
                %s
                
                > 时间: %s
                > %s:
                %s
                """.formatted(resolvedTitle, resolvedMessage, timestamp, detailLabel, detailMarkdown);

        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("content", content);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("msgtype", "markdown");
        payload.put("markdown", markdown);
        return payload;
    }

    /**
     * markdown 通道的详情块。
     *
     * <p>客户可见级别（日报，{@code INFO}）只渲染业务预览 {@code previewRows}，不渲染
     * {@code executeTime}／{@code resultRows}——这两个字段是给机器读 JSON 用的，
     * 渲染进群消息就变成工程噪音。告警级别（{@code WARN}）保留完整明细，运维排障需要。</p>
     *
     * @param details    扩展字段
     * @param linePrefix 每行前缀
     * @param level      消息级别
     * @return markdown 片段
     * @author 余炘洋
     * @date 2026-10-26
     */
    private String renderMarkdownDetails(Map<String, Object> details, String linePrefix, String level) {
        if (!isCustomerFacingLevel(level)) {
            return renderDetailMarkdown(details, linePrefix);
        }
        Object preview = details == null ? null : details.get("previewRows");
        if (!(preview instanceof Collection<?> rows) || rows.isEmpty()) {
            return linePrefix + "无";
        }
        // 标签由外层模板输出（> 结果预览:），这里只渲染内容行，避免出现两行「结果预览:」
        StringBuilder builder = new StringBuilder();
        for (Object row : rows) {
            builder.append(linePrefix).append(row).append('\n');
        }
        return builder.toString().trim();
    }

    private boolean isCustomerFacingLevel(String level) {
        return CUSTOMER_FACING_LEVEL.equalsIgnoreCase(level == null ? "" : level.trim());
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

    private String safeMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "未知异常";
        }
        return abbreviate(message.trim(), 160);
    }

    private String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private record AlertTarget(String channel, String url) {
    }
}
