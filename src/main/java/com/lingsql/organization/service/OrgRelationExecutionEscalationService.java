package com.lingsql.organization.service;

import com.lingsql.config.LingSqlProperties;
import com.lingsql.ops.OpsAlertService;
import com.lingsql.organization.entity.OrgRelationExecution;
import com.lingsql.organization.entity.OrgRelationExecutionTimeline;
import com.lingsql.organization.repository.OrgRelationExecutionRepository;
import com.lingsql.organization.repository.OrgRelationExecutionTimelineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 组织关系执行项升级服务。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgRelationExecutionEscalationService {

    private static final String STATUS_TODO = "TODO";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_BLOCKED = "BLOCKED";
    private static final String STATUS_DONE = "DONE";
    private static final String ESCALATION_STATUS_NORMAL = "NORMAL";
    private static final String ESCALATION_STATUS_DUE_SOON = "DUE_SOON";
    private static final String ESCALATION_STATUS_ESCALATED = "ESCALATED";
    private static final String ESCALATION_STATUS_RESOLVED = "RESOLVED";
    private static final String EVENT_TYPE_ESCALATED = "ESCALATED";
    private static final String SYSTEM_ACTOR = "系统调度";

    private final OrgRelationExecutionRepository executionRepository;
    private final OrgRelationExecutionTimelineRepository timelineRepository;
    private final LingSqlProperties properties;
    private final OpsAlertService opsAlertService;

    /**
     * 刷新执行项升级状态。
     *
     * @return 本次升级数量
     * @author 余炘洋
     * @date 2026-04-12
     */
    @Transactional
    public int refreshEscalations() {
        LingSqlProperties.OrgExecution config = properties.getOrgExecution();
        if (config == null || !config.isEnabled() || !config.isOverdueEscalationEnabled()) {
            return 0;
        }
        List<OrgRelationExecution> executions = executionRepository.findByStatusInAndDueTimeIsNotNullOrderByDueTimeAscIdAsc(
                List.of(STATUS_TODO, STATUS_IN_PROGRESS, STATUS_BLOCKED)
        );
        if (executions == null || executions.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int dueSoonHours = Math.max(1, config.getDueSoonHours());
        Duration cooldown = resolveCooldown(config.getEscalationCooldown());
        int changedCount = 0;
        List<OrgRelationExecution> newlyEscalated = new ArrayList<>();

        for (OrgRelationExecution execution : executions) {
            EscalationDecision decision = decide(execution, now, dueSoonHours, cooldown);
            if (!decision.changed()) {
                continue;
            }
            String beforeStatus = normalizeEscalationStatus(execution.getEscalationStatus());
            execution.setEscalationStatus(decision.escalationStatus());
            if (decision.incrementLevel()) {
                execution.setEscalationLevel((execution.getEscalationLevel() == null ? 0 : execution.getEscalationLevel()) + 1);
                execution.setLastEscalatedTime(now);
                newlyEscalated.add(execution);
            }
            execution.setUpdateTime(now);
            executionRepository.save(execution);
            timelineRepository.save(buildTimeline(execution, beforeStatus, decision, now));
            changedCount++;
        }

        if (!newlyEscalated.isEmpty()) {
            sendAlert(newlyEscalated);
        }
        return changedCount;
    }

    private EscalationDecision decide(OrgRelationExecution execution,
                                      LocalDateTime now,
                                      int dueSoonHours,
                                      Duration cooldown) {
        if (execution == null || execution.getDueTime() == null || STATUS_DONE.equals(execution.getStatus())) {
            return EscalationDecision.noChange();
        }
        String currentStatus = normalizeEscalationStatus(execution.getEscalationStatus());
        LocalDateTime dueTime = execution.getDueTime();

        if (!now.isAfter(dueTime) && !now.plusHours(dueSoonHours).isBefore(dueTime)) {
            if (ESCALATION_STATUS_NORMAL.equals(currentStatus)) {
                return new EscalationDecision(true, ESCALATION_STATUS_DUE_SOON, false, "执行项即将逾期，进入预警状态。");
            }
            return EscalationDecision.noChange();
        }

        if (now.isAfter(dueTime)) {
            boolean cooldownExpired = execution.getLastEscalatedTime() == null
                    || execution.getLastEscalatedTime().plus(cooldown).isBefore(now);
            if (!ESCALATION_STATUS_ESCALATED.equals(currentStatus)) {
                return new EscalationDecision(true, ESCALATION_STATUS_ESCALATED, true, "执行项已逾期，系统自动升级。");
            }
            if (cooldownExpired) {
                return new EscalationDecision(true, ESCALATION_STATUS_ESCALATED, true, "执行项逾期未处理，系统执行重复升级提醒。");
            }
            return EscalationDecision.noChange();
        }

        if (!ESCALATION_STATUS_NORMAL.equals(currentStatus) && !ESCALATION_STATUS_RESOLVED.equals(currentStatus)) {
            return new EscalationDecision(true, ESCALATION_STATUS_NORMAL, false, "执行项已脱离预警/升级窗口，状态恢复正常。");
        }
        return EscalationDecision.noChange();
    }

    private OrgRelationExecutionTimeline buildTimeline(OrgRelationExecution execution,
                                                       String beforeEscalationStatus,
                                                       EscalationDecision decision,
                                                       LocalDateTime now) {
        OrgRelationExecutionTimeline timeline = new OrgRelationExecutionTimeline();
        timeline.setTenantId(execution.getTenantId());
        timeline.setExecutionId(execution.getId());
        timeline.setEventType(EVENT_TYPE_ESCALATED);
        timeline.setEventSummary(resolveEventSummary(decision.escalationStatus(), decision.incrementLevel()));
        timeline.setEventDetail(decision.detail());
        timeline.setActorUserId(null);
        timeline.setActorName(SYSTEM_ACTOR);
        timeline.setBeforeStatus(execution.getStatus());
        timeline.setAfterStatus(execution.getStatus());
        timeline.setBeforeAssigneeName(execution.getAssigneeName());
        timeline.setAfterAssigneeName(execution.getAssigneeName());
        timeline.setBeforePriority(execution.getPriority());
        timeline.setAfterPriority(execution.getPriority());
        timeline.setBeforeSlaHours(execution.getSlaHours());
        timeline.setAfterSlaHours(execution.getSlaHours());
        timeline.setBeforeDueTime(execution.getDueTime());
        timeline.setAfterDueTime(execution.getDueTime());
        timeline.setBeforeResultSummary(beforeEscalationStatus);
        timeline.setAfterResultSummary(decision.escalationStatus());
        timeline.setCreateTime(now);
        return timeline;
    }

    private void sendAlert(List<OrgRelationExecution> executions) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("escalatedCount", executions.size());
        List<Map<String, Object>> items = new ArrayList<>();
        for (OrgRelationExecution execution : executions.stream().limit(20).toList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tenantId", execution.getTenantId());
            item.put("executionId", execution.getId());
            item.put("executionTitle", execution.getExecutionTitle());
            item.put("relationType", execution.getRelationType());
            item.put("assigneeName", execution.getAssigneeName());
            item.put("dueTime", execution.getDueTime());
            item.put("escalationLevel", execution.getEscalationLevel());
            items.add(item);
        }
        details.put("items", items);
        opsAlertService.sendAlert(
                "组织执行项逾期升级提醒",
                "检测到 " + executions.size() + " 个组织执行项发生逾期升级，请尽快处理。",
                details
        );
    }

    private Duration resolveCooldown(Duration source) {
        if (source == null || source.isNegative() || source.isZero()) {
            return Duration.ofHours(4);
        }
        return source;
    }

    private String normalizeEscalationStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return ESCALATION_STATUS_NORMAL;
        }
        return value.trim().toUpperCase();
    }

    private String resolveEventSummary(String escalationStatus, boolean incrementLevel) {
        if (ESCALATION_STATUS_ESCALATED.equals(escalationStatus)) {
            return incrementLevel ? "执行项升级" : "执行项仍处升级状态";
        }
        if (ESCALATION_STATUS_DUE_SOON.equals(escalationStatus)) {
            return "执行项进入预警";
        }
        if (ESCALATION_STATUS_NORMAL.equals(escalationStatus)) {
            return "执行项恢复正常";
        }
        return "执行项升级状态变更";
    }

    private record EscalationDecision(boolean changed, String escalationStatus, boolean incrementLevel, String detail) {

        private static EscalationDecision noChange() {
            return new EscalationDecision(false, null, false, null);
        }
    }
}
