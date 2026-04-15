package com.lingsql.tenant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.audit.model.AuditLogQuery;
import com.lingsql.audit.model.AuditLogView;
import com.lingsql.audit.service.AuditLogService;
import com.lingsql.common.result.PageResult;
import com.lingsql.tenant.model.TenantNotificationChannelRecentTestView;
import com.lingsql.tenant.model.TenantNotificationChannelTestChannelView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    public List<TenantNotificationChannelRecentTestView> listRecentTests(String tenantId, Integer limit) {
        AuditLogQuery query = new AuditLogQuery();
        query.setTenantId(StringUtils.hasText(tenantId) ? tenantId.trim() : null);
        query.setAction(ACTION_NOTIFICATION_CHANNEL_TEST);
        int resolvedLimit = resolveLimit(limit);
        PageResult<AuditLogView> result = auditLogService.query(query, 1, resolvedLimit);
        return (result.getList() == null ? List.<AuditLogView>of() : result.getList()).stream()
                .map(this::toRecentTestView)
                .toList();
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

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}
