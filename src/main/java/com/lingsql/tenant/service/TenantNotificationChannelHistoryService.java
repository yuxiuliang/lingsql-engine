package com.lingsql.tenant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.audit.model.AuditLogQuery;
import com.lingsql.audit.model.AuditLogView;
import com.lingsql.audit.service.AuditLogService;
import com.lingsql.common.result.PageResult;
import com.lingsql.tenant.model.TenantNotificationChannelFailureChannelView;
import com.lingsql.tenant.model.TenantNotificationChannelFailureReasonView;
import com.lingsql.tenant.model.TenantNotificationChannelFailureStatsView;
import com.lingsql.tenant.model.TenantNotificationChannelRecentTestView;
import com.lingsql.tenant.model.TenantNotificationChannelTestChannelView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.LocalDateTime;

/**
 * 租户通知通道测试记录服务。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
@Service
@RequiredArgsConstructor
public class TenantNotificationChannelHistoryService {

    private static final String ACTION_NOTIFICATION_CHANNEL_TEST = "TENANT_NOTIFICATION_CHANNEL_TEST";
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_STATS_LIMIT = 100;
    private static final int MAX_STATS_LIMIT = 200;
    private static final int MAX_FILTER_SAMPLE = 200;

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /**
     * 查询最近的通知通道测试记录。
     *
     * @param tenantId 租户ID
     * @param limit    返回条数
     * @return 最近测试记录
     * @author 余炘洋
     * @date 2026-04-15
     */
    public List<TenantNotificationChannelRecentTestView> listRecentTests(String tenantId,
                                                                         Integer success,
                                                                         String channel,
                                                                         String operatorKeyword,
                                                                         LocalDateTime startTime,
                                                                         LocalDateTime endTime,
                                                                         Integer limit) {
        int resolvedLimit = resolveRecentLimit(limit);
        List<TenantNotificationChannelRecentTestView> tests = listRecentTestsInternal(
                tenantId,
                startTime,
                endTime,
                shouldUseExpandedSample(success, channel, operatorKeyword) ? MAX_FILTER_SAMPLE : resolvedLimit
        );
        return tests.stream()
                .filter(item -> matchSuccess(item, success))
                .filter(item -> matchChannel(item, channel))
                .filter(item -> matchOperator(item, operatorKeyword))
                .limit(resolvedLimit)
                .toList();
    }

    /**
     * 统计最近测试中的失败分布。
     *
     * @param tenantId 租户ID
     * @param limit    样本条数
     * @return 失败统计
     * @author 余炘洋
     * @date 2026-04-15
     */
    public TenantNotificationChannelFailureStatsView summarizeFailures(String tenantId,
                                                                       LocalDateTime startTime,
                                                                       LocalDateTime endTime,
                                                                       Integer limit) {
        List<TenantNotificationChannelRecentTestView> tests = listRecentTestsInternal(
                tenantId,
                startTime,
                endTime,
                resolveStatsLimit(limit)
        );
        int totalTests = tests.size();
        long failedTests = tests.stream().filter(item -> !Boolean.TRUE.equals(item.getSuccess())).count();
        Map<String, Integer> channelCounter = new LinkedHashMap<>();
        Map<String, Integer> reasonCounter = new LinkedHashMap<>();
        for (TenantNotificationChannelRecentTestView test : tests) {
            if (Boolean.TRUE.equals(test.getSuccess())) {
                continue;
            }
            boolean recorded = false;
            for (TenantNotificationChannelTestChannelView channelResult : test.getChannelResults() == null ? List.<TenantNotificationChannelTestChannelView>of() : test.getChannelResults()) {
                if (Boolean.TRUE.equals(channelResult.getSuccess())) {
                    continue;
                }
                recorded = true;
                accumulate(channelCounter, defaultText(channelResult.getChannel()));
                accumulate(reasonCounter, normalizeReason(channelResult.getDetail()));
            }
            if (!recorded) {
                accumulate(reasonCounter, normalizeReason(test.getSummary()));
            }
        }
        TenantNotificationChannelFailureStatsView view = new TenantNotificationChannelFailureStatsView();
        view.setTenantId(StringUtils.hasText(tenantId) ? tenantId.trim() : null);
        view.setTotalTests(totalTests);
        view.setFailedTests((int) failedTests);
        view.setSuccessRate(calculateSuccessRate(totalTests, (int) failedTests));
        view.setSummary(buildStatsSummary(totalTests, (int) failedTests, channelCounter, reasonCounter));
        view.setTopFailingChannels(toFailureChannelViews(channelCounter));
        view.setTopFailureReasons(toFailureReasonViews(reasonCounter));
        return view;
    }

    private List<TenantNotificationChannelRecentTestView> listRecentTestsInternal(String tenantId,
                                                                                  LocalDateTime startTime,
                                                                                  LocalDateTime endTime,
                                                                                  int limit) {
        AuditLogQuery query = new AuditLogQuery();
        query.setTenantId(StringUtils.hasText(tenantId) ? tenantId.trim() : null);
        query.setAction(ACTION_NOTIFICATION_CHANNEL_TEST);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        PageResult<AuditLogView> result = auditLogService.query(query, 1, limit);
        return (result.getList() == null ? List.<AuditLogView>of() : result.getList()).stream()
                .map(this::toRecentTestView)
                .toList();
    }

    private boolean shouldUseExpandedSample(Integer success, String channel, String operatorKeyword) {
        return success != null || StringUtils.hasText(channel) || StringUtils.hasText(operatorKeyword);
    }

    private boolean matchSuccess(TenantNotificationChannelRecentTestView item, Integer success) {
        if (success == null) {
            return true;
        }
        boolean expected = Integer.valueOf(1).equals(success);
        return Boolean.valueOf(expected).equals(item.getSuccess());
    }

    private boolean matchChannel(TenantNotificationChannelRecentTestView item, String channel) {
        if (!StringUtils.hasText(channel)) {
            return true;
        }
        String expected = channel.trim().toLowerCase(Locale.ROOT);
        boolean matchedRequested = (item.getRequestedChannels() == null ? List.<String>of() : item.getRequestedChannels()).stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(expected::equals);
        if (matchedRequested) {
            return true;
        }
        return (item.getChannelResults() == null ? List.<TenantNotificationChannelTestChannelView>of() : item.getChannelResults()).stream()
                .map(TenantNotificationChannelTestChannelView::getChannel)
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(expected::equals);
    }

    private boolean matchOperator(TenantNotificationChannelRecentTestView item, String operatorKeyword) {
        if (!StringUtils.hasText(operatorKeyword)) {
            return true;
        }
        String expected = operatorKeyword.trim().toLowerCase(Locale.ROOT);
        return StringUtils.hasText(item.getOperatorUsername())
                && item.getOperatorUsername().trim().toLowerCase(Locale.ROOT).contains(expected);
    }

    private TenantNotificationChannelRecentTestView toRecentTestView(AuditLogView view) {
        JsonNode detailNode = parseDetail(view == null ? null : view.getDetail());
        TenantNotificationChannelRecentTestView recentView = new TenantNotificationChannelRecentTestView();
        if (view == null) {
            recentView.setRequestedChannels(List.of());
            recentView.setChannelResults(List.of());
            return recentView;
        }
        recentView.setAuditLogId(view.getId());
        recentView.setTenantId(resolveText(detailNode, "tenantId", view.getTenantId()));
        recentView.setTenantName(resolveText(detailNode, "tenantName", null));
        recentView.setRequestedChannels(resolveRequestedChannels(detailNode));
        recentView.setSuccess(resolveSuccess(detailNode, view));
        recentView.setSummary(resolveSummary(detailNode, view));
        recentView.setOperatorUsername(view.getUsername());
        recentView.setCreateTime(view.getCreateTime());
        recentView.setChannelResults(resolveChannelResults(detailNode));
        return recentView;
    }

    private JsonNode parseDetail(String detail) {
        if (!StringUtils.hasText(detail)) {
            return null;
        }
        try {
            return objectMapper.readTree(detail);
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveText(JsonNode detailNode, String fieldName, String fallback) {
        if (detailNode != null && detailNode.has(fieldName) && !detailNode.get(fieldName).isNull()) {
            String value = detailNode.get(fieldName).asText(null);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return fallback;
    }

    private Boolean resolveSuccess(JsonNode detailNode, AuditLogView view) {
        if (detailNode != null && detailNode.has("success") && !detailNode.get("success").isNull()) {
            return detailNode.get("success").asBoolean();
        }
        return view != null && Integer.valueOf(1).equals(view.getSuccess());
    }

    private String resolveSummary(JsonNode detailNode, AuditLogView view) {
        String detailSummary = resolveText(detailNode, "summary", null);
        if (StringUtils.hasText(detailSummary)) {
            return detailSummary;
        }
        if (view != null && StringUtils.hasText(view.getSummary())) {
            return view.getSummary();
        }
        return view == null ? null : view.getErrorMessage();
    }

    private List<String> resolveRequestedChannels(JsonNode detailNode) {
        if (detailNode == null) {
            return List.of();
        }
        JsonNode channelsNode = detailNode.has("requestedChannels") ? detailNode.get("requestedChannels") : detailNode.get("channels");
        if (channelsNode == null || !channelsNode.isArray()) {
            return List.of();
        }
        List<String> channels = new ArrayList<>();
        for (JsonNode item : channelsNode) {
            if (item != null && !item.isNull() && StringUtils.hasText(item.asText())) {
                channels.add(item.asText().trim());
            }
        }
        return channels;
    }

    private List<TenantNotificationChannelTestChannelView> resolveChannelResults(JsonNode detailNode) {
        if (detailNode == null || !detailNode.has("channelResults") || !detailNode.get("channelResults").isArray()) {
            return Collections.emptyList();
        }
        List<TenantNotificationChannelTestChannelView> results = new ArrayList<>();
        for (JsonNode item : detailNode.get("channelResults")) {
            TenantNotificationChannelTestChannelView view = new TenantNotificationChannelTestChannelView();
            view.setChannel(item.has("channel") && !item.get("channel").isNull() ? item.get("channel").asText() : null);
            view.setSuccess(item.has("success") && !item.get("success").isNull() ? item.get("success").asBoolean() : null);
            view.setStatusCode(item.has("statusCode") && !item.get("statusCode").isNull() ? item.get("statusCode").asInt() : null);
            view.setDetail(item.has("detail") && !item.get("detail").isNull() ? item.get("detail").asText() : null);
            results.add(view);
        }
        return results;
    }

    private void accumulate(Map<String, Integer> counter, String key) {
        counter.merge(key, 1, Integer::sum);
    }

    private List<TenantNotificationChannelFailureChannelView> toFailureChannelViews(Map<String, Integer> counter) {
        return counter.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(5)
                .map(entry -> {
                    TenantNotificationChannelFailureChannelView view = new TenantNotificationChannelFailureChannelView();
                    view.setChannel(entry.getKey());
                    view.setFailureCount(entry.getValue());
                    return view;
                })
                .toList();
    }

    private List<TenantNotificationChannelFailureReasonView> toFailureReasonViews(Map<String, Integer> counter) {
        return counter.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(5)
                .map(entry -> {
                    TenantNotificationChannelFailureReasonView view = new TenantNotificationChannelFailureReasonView();
                    view.setReason(entry.getKey());
                    view.setCount(entry.getValue());
                    return view;
                })
                .toList();
    }

    private String normalizeReason(String detail) {
        if (!StringUtils.hasText(detail)) {
            return "未记录失败原因";
        }
        String normalized = detail.trim();
        if (normalized.length() > 80) {
            return normalized.substring(0, 80) + "...";
        }
        return normalized;
    }

    private String defaultText(String value) {
        if (!StringUtils.hasText(value)) {
            return "未知通道";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private double calculateSuccessRate(int totalTests, int failedTests) {
        if (totalTests <= 0) {
            return 0D;
        }
        return ((double) (totalTests - failedTests) * 100D) / (double) totalTests;
    }

    private String buildStatsSummary(int totalTests,
                                     int failedTests,
                                     Map<String, Integer> channelCounter,
                                     Map<String, Integer> reasonCounter) {
        if (totalTests <= 0) {
            return "最近暂无通知通道测试记录，暂时无法生成问题分布。";
        }
        if (failedTests <= 0) {
            return "最近测试全部成功，暂无失败问题分布。";
        }
        String topChannel = channelCounter.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("未知通道");
        String topReason = reasonCounter.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("未记录失败原因");
        return "最近 " + totalTests + " 次测试中有 " + failedTests + " 次失败；失败最多的通道是 "
                + topChannel + "，最常见原因是“" + topReason + "”。";
    }

    private int resolveRecentLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private int resolveStatsLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_STATS_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_STATS_LIMIT));
    }
}
