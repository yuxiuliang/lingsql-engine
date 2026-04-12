package com.lingsql.organization.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.organization.entity.OrgRelationExecution;
import com.lingsql.organization.entity.OrgRelationExecutionTimeline;
import com.lingsql.organization.model.OrgRelationExecutionCreateRequest;
import com.lingsql.organization.model.OrgRelationExecutionTimelineView;
import com.lingsql.organization.model.OrgRelationExecutionUpdateRequest;
import com.lingsql.organization.model.OrgRelationExecutionView;
import com.lingsql.organization.model.OrgRelationRouteCandidateView;
import com.lingsql.organization.model.OrgRelationRouteResolveView;
import com.lingsql.organization.repository.OrgRelationExecutionRepository;
import com.lingsql.organization.repository.OrgRelationExecutionTimelineRepository;
import com.lingsql.rbac.entity.RbacUser;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.rbac.security.RbacUserSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 组织关系执行台账服务。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Service
@RequiredArgsConstructor
public class OrgRelationExecutionService {

    private static final String STATUS_TODO = "TODO";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_BLOCKED = "BLOCKED";
    private static final String STATUS_DONE = "DONE";
    private static final String PRIORITY_P0 = "P0";
    private static final String PRIORITY_P1 = "P1";
    private static final String PRIORITY_P2 = "P2";
    private static final String SLA_STATUS_ON_TRACK = "ON_TRACK";
    private static final String SLA_STATUS_DUE_SOON = "DUE_SOON";
    private static final String SLA_STATUS_OVERDUE = "OVERDUE";
    private static final String SLA_STATUS_COMPLETED_ON_TIME = "COMPLETED_ON_TIME";
    private static final String SLA_STATUS_COMPLETED_LATE = "COMPLETED_LATE";
    private static final int SLA_MIN_HOURS = 1;
    private static final int SLA_MAX_HOURS = 720;
    private static final String ESCALATION_STATUS_NORMAL = "NORMAL";
    private static final String ESCALATION_STATUS_DUE_SOON = "DUE_SOON";
    private static final String ESCALATION_STATUS_ESCALATED = "ESCALATED";
    private static final String ESCALATION_STATUS_RESOLVED = "RESOLVED";
    private static final String EVENT_TYPE_CREATED = "CREATED";
    private static final String EVENT_TYPE_UPDATED = "UPDATED";

    private final OrgRelationExecutionRepository orgRelationExecutionRepository;
    private final OrgRelationExecutionTimelineRepository orgRelationExecutionTimelineRepository;
    private final OrganizationAdminService organizationAdminService;
    private final RbacUserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * 查询执行台账。
     *
     * @param tenantId     租户ID
     * @param status       状态
     * @param relationType 关系类型
     * @return 台账列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    public List<OrgRelationExecutionView> listExecutions(String tenantId, String status, String relationType) {
        String resolvedTenantId = requireTenantId(tenantId);
        String normalizedStatus = normalizeStatus(status, false);
        String normalizedRelationType = normalizeRelationType(relationType);
        List<OrgRelationExecution> entities;
        if (StringUtils.hasText(normalizedStatus) && StringUtils.hasText(normalizedRelationType)) {
            entities = orgRelationExecutionRepository.findByTenantIdAndStatusAndRelationTypeOrderByCreateTimeDescIdDesc(
                    resolvedTenantId,
                    normalizedStatus,
                    normalizedRelationType
            );
        } else if (StringUtils.hasText(normalizedStatus)) {
            entities = orgRelationExecutionRepository.findByTenantIdAndStatusOrderByCreateTimeDescIdDesc(resolvedTenantId, normalizedStatus);
        } else if (StringUtils.hasText(normalizedRelationType)) {
            entities = orgRelationExecutionRepository.findByTenantIdAndRelationTypeOrderByCreateTimeDescIdDesc(resolvedTenantId, normalizedRelationType);
        } else {
            entities = orgRelationExecutionRepository.findByTenantIdOrderByCreateTimeDescIdDesc(resolvedTenantId);
        }
        return entities.stream().map(this::toView).toList();
    }

    /**
     * 查询执行轨迹。
     *
     * @param executionId   执行项ID
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @return 轨迹列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    public List<OrgRelationExecutionTimelineView> listExecutionTimeline(Long executionId, String sessionTenant, boolean sysAdmin) {
        if (executionId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "执行台账ID不能为空");
        }
        OrgRelationExecution entity = orgRelationExecutionRepository.findById(executionId)
                .orElseThrow(() -> new BizException(ErrorCode.ORG_RELATION_EXECUTION_NOT_FOUND, "组织关系执行项不存在"));
        validateTenantAccess(entity.getTenantId(), sessionTenant, sysAdmin);
        return orgRelationExecutionTimelineRepository.findByTenantIdAndExecutionIdOrderByCreateTimeDescIdDesc(entity.getTenantId(), executionId)
                .stream()
                .map(this::toTimelineView)
                .toList();
    }

    /**
     * 创建执行台账。
     *
     * @param request       创建请求
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @param session       当前会话
     * @return 执行台账视图
     * @author 余炘洋
     * @date 2026-04-12
     */
    @Transactional
    public OrgRelationExecutionView createExecution(OrgRelationExecutionCreateRequest request,
                                                    String sessionTenant,
                                                    boolean sysAdmin,
                                                    RbacUserSession session) {
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "执行台账请求不能为空");
        }
        if (session == null || session.getUserId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        String tenantId = resolveTenantId(request.getTenantId(), sessionTenant, sysAdmin);
        Long sourceOrgUnitId = requireOrgUnitId(request.getSourceOrgUnitId(), "源组织节点不能为空");
        String relationType = requireRelationType(request.getRelationType());
        String executionTitle = requireText(request.getExecutionTitle(), "执行标题不能为空");
        String executionContent = normalizeText(request.getExecutionContent());
        OrgRelationRouteResolveView routeResolveView = organizationAdminService.resolveRelationRoute(tenantId, sourceOrgUnitId, relationType);
        OrgRelationRouteCandidateView candidate = resolveCandidate(routeResolveView, request.getRelationId());
        Long assigneeUserId = resolveAssigneeUserId(tenantId, request.getAssigneeUserId(), candidate.getTargetLeaderUserId());
        String assigneeName = assigneeUserId == null ? null : resolveUserDisplayName(userRepository.findById(assigneeUserId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, "处理人不存在")));
        String priority = normalizePriority(request.getPriority(), defaultPriority(relationType));
        Integer slaHours = resolveSlaHours(request.getSlaHours(), priority);
        String status = assigneeUserId == null ? STATUS_BLOCKED : STATUS_TODO;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dueTime = resolveDueTimeForCreate(request.getDueTime(), now, slaHours);

        OrgRelationExecution entity = new OrgRelationExecution();
        entity.setTenantId(tenantId);
        entity.setRelationId(candidate.getRelationId());
        entity.setRelationType(relationType);
        entity.setRelationName(candidate.getRelationName());
        entity.setSceneCode(resolveSceneCode(relationType));
        entity.setSceneName(resolveSceneName(relationType));
        entity.setExecutionTitle(executionTitle);
        entity.setExecutionContent(executionContent);
        entity.setPriority(priority);
        entity.setSlaHours(slaHours);
        entity.setDueTime(dueTime);
        entity.setEscalationStatus(ESCALATION_STATUS_NORMAL);
        entity.setEscalationLevel(0);
        entity.setLastEscalatedTime(null);
        entity.setStatus(status);
        entity.setSourceOrgUnitId(routeResolveView.getSourceOrgUnitId());
        entity.setSourceOrgUnitCode(routeResolveView.getSourceOrgUnitCode());
        entity.setSourceOrgUnitName(routeResolveView.getSourceOrgUnitName());
        entity.setTargetOrgUnitId(candidate.getTargetOrgUnitId());
        entity.setTargetOrgUnitCode(candidate.getTargetOrgUnitCode());
        entity.setTargetOrgUnitName(candidate.getTargetOrgUnitName());
        entity.setRouteScopeSummary(candidate.getRouteScopeSummary());
        entity.setScopeUnitCount(candidate.getScopeUnitCount());
        entity.setScopeUnitCodesJson(writeScopeCodes(candidate.getScopeUnitCodes()));
        entity.setRequestedByUserId(session.getUserId());
        entity.setRequestedByName(resolveSessionDisplayName(session));
        entity.setAssigneeUserId(assigneeUserId);
        entity.setAssigneeName(assigneeName);
        entity.setResultSummary(null);
        entity.setStartedTime(null);
        entity.setCompletedTime(null);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        OrgRelationExecution saved = orgRelationExecutionRepository.save(entity);
        recordTimeline(saved, EVENT_TYPE_CREATED, "创建执行项", buildCreateDetail(saved), null, null, session);
        return toView(saved);
    }

    /**
     * 更新执行台账。
     *
     * @param id            台账ID
     * @param request       更新请求
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @return 更新后的视图
     * @author 余炘洋
     * @date 2026-04-12
     */
    @Transactional
    public OrgRelationExecutionView updateExecution(Long id,
                                                    OrgRelationExecutionUpdateRequest request,
                                                    String sessionTenant,
                                                    boolean sysAdmin,
                                                    RbacUserSession session) {
        if (id == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "执行台账ID不能为空");
        }
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "执行台账请求不能为空");
        }
        OrgRelationExecution entity = orgRelationExecutionRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.ORG_RELATION_EXECUTION_NOT_FOUND, "组织关系执行项不存在"));
        validateTenantAccess(entity.getTenantId(), sessionTenant, sysAdmin);
        OrgRelationExecution before = copyExecution(entity);

        String currentStatus = entity.getStatus();
        String newStatus = normalizeStatus(request.getStatus(), true);
        if (!isAllowedStatusTransition(currentStatus, newStatus)) {
            throw new BizException(ErrorCode.CONFLICT, "当前状态不允许流转到目标状态");
        }
        Long assigneeUserId = entity.getAssigneeUserId();
        String assigneeName = entity.getAssigneeName();
        if (request.getAssigneeUserId() != null) {
            assigneeUserId = resolveAssigneeUserId(entity.getTenantId(), request.getAssigneeUserId(), null);
            RbacUser assignee = userRepository.findById(assigneeUserId)
                    .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, "处理人不存在"));
            assigneeName = resolveUserDisplayName(assignee);
        }
        LocalDateTime now = LocalDateTime.now();
        String resolvedPriority = normalizePriority(request.getPriority(), entity.getPriority());
        Integer resolvedSlaHours = resolveSlaHoursForUpdate(request.getSlaHours(), resolvedPriority, entity.getSlaHours());
        LocalDateTime resolvedDueTime = resolveDueTimeForUpdate(request.getDueTime(), request.getSlaHours(), entity.getDueTime(), now, resolvedSlaHours);
        String resolvedResultSummary = StringUtils.hasText(request.getResultSummary())
                ? request.getResultSummary().trim()
                : entity.getResultSummary();
        validateStatusBusinessRule(newStatus, assigneeUserId, resolvedResultSummary);

        entity.setStatus(newStatus);
        entity.setPriority(resolvedPriority);
        entity.setSlaHours(resolvedSlaHours);
        entity.setDueTime(resolvedDueTime);
        entity.setAssigneeUserId(assigneeUserId);
        entity.setAssigneeName(assigneeName);
        entity.setResultSummary(resolvedResultSummary);
        if (STATUS_IN_PROGRESS.equals(newStatus) && entity.getStartedTime() == null) {
            entity.setStartedTime(now);
        }
        if (STATUS_DONE.equals(newStatus)) {
            if (entity.getStartedTime() == null) {
                entity.setStartedTime(now);
            }
            entity.setCompletedTime(now);
            entity.setEscalationStatus(ESCALATION_STATUS_RESOLVED);
        } else if (STATUS_DONE.equals(currentStatus) && !STATUS_DONE.equals(newStatus)) {
            entity.setCompletedTime(null);
            if (ESCALATION_STATUS_RESOLVED.equals(entity.getEscalationStatus())) {
                entity.setEscalationStatus(ESCALATION_STATUS_NORMAL);
            }
        }
        entity.setUpdateTime(now);
        OrgRelationExecution saved = orgRelationExecutionRepository.save(entity);
        recordTimeline(saved, EVENT_TYPE_UPDATED, buildUpdateSummary(before, saved), buildUpdateDetail(before, saved), before, saved, session);
        return toView(saved);
    }

    private OrgRelationExecutionView toView(OrgRelationExecution entity) {
        OrgRelationExecutionView view = new OrgRelationExecutionView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setRelationId(entity.getRelationId());
        view.setRelationType(entity.getRelationType());
        view.setRelationName(entity.getRelationName());
        view.setSceneCode(entity.getSceneCode());
        view.setSceneName(entity.getSceneName());
        view.setExecutionTitle(entity.getExecutionTitle());
        view.setExecutionContent(entity.getExecutionContent());
        view.setPriority(entity.getPriority());
        Integer effectiveSlaHours = entity.getSlaHours() == null ? defaultSlaHours(entity.getPriority()) : entity.getSlaHours();
        LocalDateTime effectiveDueTime = resolveEffectiveDueTime(entity.getDueTime(), entity.getCreateTime(), effectiveSlaHours);
        view.setSlaHours(effectiveSlaHours);
        view.setDueTime(effectiveDueTime);
        view.setSlaStatus(resolveSlaStatus(entity.getStatus(), effectiveDueTime, entity.getCompletedTime(), effectiveSlaHours));
        view.setOverdue(isOverdue(entity.getStatus(), effectiveDueTime));
        view.setOverdueMinutes(resolveOverdueMinutes(entity.getStatus(), effectiveDueTime, entity.getCompletedTime()));
        view.setCompletedWithinSla(resolveCompletedWithinSla(entity.getCompletedTime(), effectiveDueTime));
        view.setEscalationStatus(normalizeEscalationStatus(entity.getEscalationStatus()));
        view.setEscalationLevel(entity.getEscalationLevel() == null ? 0 : entity.getEscalationLevel());
        view.setLastEscalatedTime(entity.getLastEscalatedTime());
        view.setStatus(entity.getStatus());
        view.setSourceOrgUnitId(entity.getSourceOrgUnitId());
        view.setSourceOrgUnitCode(entity.getSourceOrgUnitCode());
        view.setSourceOrgUnitName(entity.getSourceOrgUnitName());
        view.setTargetOrgUnitId(entity.getTargetOrgUnitId());
        view.setTargetOrgUnitCode(entity.getTargetOrgUnitCode());
        view.setTargetOrgUnitName(entity.getTargetOrgUnitName());
        view.setRouteScopeSummary(entity.getRouteScopeSummary());
        view.setScopeUnitCount(entity.getScopeUnitCount());
        view.setScopeUnitCodes(readScopeCodes(entity.getScopeUnitCodesJson()));
        view.setRequestedByUserId(entity.getRequestedByUserId());
        view.setRequestedByName(entity.getRequestedByName());
        view.setAssigneeUserId(entity.getAssigneeUserId());
        view.setAssigneeName(entity.getAssigneeName());
        view.setResultSummary(entity.getResultSummary());
        view.setStartedTime(entity.getStartedTime());
        view.setCompletedTime(entity.getCompletedTime());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }

    private OrgRelationExecutionTimelineView toTimelineView(OrgRelationExecutionTimeline entity) {
        OrgRelationExecutionTimelineView view = new OrgRelationExecutionTimelineView();
        view.setId(entity.getId());
        view.setExecutionId(entity.getExecutionId());
        view.setEventType(entity.getEventType());
        view.setEventSummary(entity.getEventSummary());
        view.setEventDetail(entity.getEventDetail());
        view.setActorUserId(entity.getActorUserId());
        view.setActorName(entity.getActorName());
        view.setBeforeStatus(entity.getBeforeStatus());
        view.setAfterStatus(entity.getAfterStatus());
        view.setBeforeAssigneeName(entity.getBeforeAssigneeName());
        view.setAfterAssigneeName(entity.getAfterAssigneeName());
        view.setBeforePriority(entity.getBeforePriority());
        view.setAfterPriority(entity.getAfterPriority());
        view.setBeforeSlaHours(entity.getBeforeSlaHours());
        view.setAfterSlaHours(entity.getAfterSlaHours());
        view.setBeforeDueTime(entity.getBeforeDueTime());
        view.setAfterDueTime(entity.getAfterDueTime());
        view.setBeforeResultSummary(entity.getBeforeResultSummary());
        view.setAfterResultSummary(entity.getAfterResultSummary());
        view.setCreateTime(entity.getCreateTime());
        return view;
    }

    private OrgRelationRouteCandidateView resolveCandidate(OrgRelationRouteResolveView routeResolveView, Long relationId) {
        if (routeResolveView == null || !routeResolveView.isMatched() || routeResolveView.getCandidates() == null || routeResolveView.getCandidates().isEmpty()) {
            throw new BizException(ErrorCode.CONFLICT, "当前源组织未匹配到可用路由，无法创建执行项");
        }
        if (relationId != null) {
            return routeResolveView.getCandidates().stream()
                    .filter(item -> Objects.equals(item.getRelationId(), relationId))
                    .findFirst()
                    .orElseThrow(() -> new BizException(ErrorCode.INVALID_PARAM, "指定关系不属于当前路由候选"));
        }
        if (routeResolveView.isAmbiguous()) {
            throw new BizException(ErrorCode.CONFLICT, "当前存在多个候选目标，请先明确选择关系后再创建执行项");
        }
        return routeResolveView.getCandidates().getFirst();
    }

    private Long resolveAssigneeUserId(String tenantId, Long preferredUserId, Long fallbackUserId) {
        Long userId = preferredUserId != null ? preferredUserId : fallbackUserId;
        if (userId == null) {
            return null;
        }
        RbacUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, "处理人不存在"));
        if (!Objects.equals(tenantId, user.getTenantId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "处理人不属于当前租户");
        }
        return userId;
    }

    private void validateStatusBusinessRule(String status, Long assigneeUserId, String resultSummary) {
        if ((STATUS_TODO.equals(status) || STATUS_IN_PROGRESS.equals(status) || STATUS_DONE.equals(status))
                && assigneeUserId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "当前状态必须指定处理人");
        }
        if (STATUS_DONE.equals(status) && !StringUtils.hasText(resultSummary)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "完成执行项时必须填写处理结果");
        }
    }

    private boolean isAllowedStatusTransition(String currentStatus, String newStatus) {
        if (!StringUtils.hasText(currentStatus)) {
            return true;
        }
        if (Objects.equals(currentStatus, newStatus)) {
            return true;
        }
        return switch (currentStatus) {
            case STATUS_TODO -> STATUS_IN_PROGRESS.equals(newStatus) || STATUS_BLOCKED.equals(newStatus) || STATUS_DONE.equals(newStatus);
            case STATUS_IN_PROGRESS -> STATUS_BLOCKED.equals(newStatus) || STATUS_DONE.equals(newStatus) || STATUS_TODO.equals(newStatus);
            case STATUS_BLOCKED -> STATUS_TODO.equals(newStatus) || STATUS_IN_PROGRESS.equals(newStatus) || STATUS_DONE.equals(newStatus);
            case STATUS_DONE -> STATUS_TODO.equals(newStatus) || STATUS_IN_PROGRESS.equals(newStatus) || STATUS_BLOCKED.equals(newStatus);
            default -> false;
        };
    }

    private String normalizePriority(String priority, String defaultValue) {
        if (!StringUtils.hasText(priority)) {
            return defaultValue;
        }
        String normalized = priority.trim().toUpperCase(Locale.ROOT);
        if (!PRIORITY_P0.equals(normalized) && !PRIORITY_P1.equals(normalized) && !PRIORITY_P2.equals(normalized)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "优先级仅支持 P0 / P1 / P2");
        }
        return normalized;
    }

    private Integer resolveSlaHours(Integer requestSlaHours, String priority) {
        if (requestSlaHours == null) {
            return defaultSlaHours(priority);
        }
        if (requestSlaHours < SLA_MIN_HOURS || requestSlaHours > SLA_MAX_HOURS) {
            throw new BizException(ErrorCode.INVALID_PARAM, "SLA 时长必须在 1 到 720 小时之间");
        }
        return requestSlaHours;
    }

    private Integer resolveSlaHoursForUpdate(Integer requestSlaHours, String priority, Integer existingSlaHours) {
        if (requestSlaHours != null) {
            return resolveSlaHours(requestSlaHours, priority);
        }
        if (existingSlaHours != null) {
            return existingSlaHours;
        }
        return defaultSlaHours(priority);
    }

    private String normalizeStatus(String status, boolean required) {
        if (!StringUtils.hasText(status)) {
            if (required) {
                throw new BizException(ErrorCode.INVALID_PARAM, "执行状态不能为空");
            }
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUS_TODO.equals(normalized)
                && !STATUS_IN_PROGRESS.equals(normalized)
                && !STATUS_BLOCKED.equals(normalized)
                && !STATUS_DONE.equals(normalized)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "执行状态仅支持 TODO / IN_PROGRESS / BLOCKED / DONE");
        }
        return normalized;
    }

    private LocalDateTime resolveDueTimeForCreate(String dueTimeText, LocalDateTime now, Integer slaHours) {
        LocalDateTime dueTime = parseDateTime(dueTimeText);
        if (dueTime == null) {
            return now.plusHours(slaHours == null ? defaultSlaHours(PRIORITY_P2) : slaHours);
        }
        if (dueTime.isBefore(now)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "截止时间不能早于当前时间");
        }
        return dueTime;
    }

    private LocalDateTime resolveDueTimeForUpdate(String dueTimeText,
                                                  Integer requestSlaHours,
                                                  LocalDateTime existingDueTime,
                                                  LocalDateTime now,
                                                  Integer resolvedSlaHours) {
        LocalDateTime dueTime = parseDateTime(dueTimeText);
        if (dueTime != null) {
            return dueTime;
        }
        if (requestSlaHours != null) {
            return now.plusHours(resolvedSlaHours);
        }
        return existingDueTime;
    }

    private String requireTenantId(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "请先选择要管理的租户");
        }
        return tenantId.trim();
    }

    private String resolveTenantId(String requestTenant, String sessionTenant, boolean sysAdmin) {
        if (sysAdmin) {
            if (!StringUtils.hasText(requestTenant)) {
                throw new BizException(ErrorCode.INVALID_PARAM, "请先选择要管理的租户");
            }
            return requestTenant.trim();
        }
        if (!StringUtils.hasText(sessionTenant)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户信息缺失");
        }
        return sessionTenant.trim();
    }

    private void validateTenantAccess(String targetTenant, String sessionTenant, boolean sysAdmin) {
        if (sysAdmin) {
            return;
        }
        if (!StringUtils.hasText(targetTenant) || !StringUtils.hasText(sessionTenant)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户信息缺失");
        }
        if (!targetTenant.trim().equals(sessionTenant.trim())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问");
        }
    }

    private Long requireOrgUnitId(Long orgUnitId, String errorMessage) {
        if (orgUnitId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, errorMessage);
        }
        return orgUnitId;
    }

    private String requireRelationType(String relationType) {
        if (!StringUtils.hasText(relationType)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "关系类型不能为空");
        }
        return relationType.trim().toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.INVALID_PARAM, errorMessage);
        }
        return value.trim();
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.SIMPLIFIED_CHINESE));
            } catch (DateTimeParseException ex) {
                throw new BizException(ErrorCode.INVALID_PARAM, "截止时间格式不正确");
            }
        }
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeRelationType(String relationType) {
        return StringUtils.hasText(relationType) ? relationType.trim().toUpperCase(Locale.ROOT) : null;
    }

    String normalizeEscalationStatus(String escalationStatus) {
        if (!StringUtils.hasText(escalationStatus)) {
            return ESCALATION_STATUS_NORMAL;
        }
        return escalationStatus.trim().toUpperCase(Locale.ROOT);
    }

    private int defaultSlaHours(String priority) {
        if (PRIORITY_P0.equals(priority)) {
            return 4;
        }
        if (PRIORITY_P1.equals(priority)) {
            return 24;
        }
        return 48;
    }

    private LocalDateTime resolveEffectiveDueTime(LocalDateTime dueTime, LocalDateTime createTime, Integer slaHours) {
        if (dueTime != null) {
            return dueTime;
        }
        if (createTime == null || slaHours == null) {
            return null;
        }
        return createTime.plusHours(slaHours);
    }

    private String resolveSlaStatus(String status, LocalDateTime dueTime, LocalDateTime completedTime, Integer slaHours) {
        if (dueTime == null) {
            return null;
        }
        if (STATUS_DONE.equals(status)) {
            return Boolean.TRUE.equals(resolveCompletedWithinSla(completedTime, dueTime))
                    ? SLA_STATUS_COMPLETED_ON_TIME
                    : SLA_STATUS_COMPLETED_LATE;
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(dueTime)) {
            return SLA_STATUS_OVERDUE;
        }
        long thresholdHours = Math.max(1, Math.min(4, (slaHours == null ? 4 : slaHours) / 4));
        if (!now.plusHours(thresholdHours).isBefore(dueTime)) {
            return SLA_STATUS_DUE_SOON;
        }
        return SLA_STATUS_ON_TRACK;
    }

    private Boolean isOverdue(String status, LocalDateTime dueTime) {
        if (dueTime == null || STATUS_DONE.equals(status)) {
            return false;
        }
        return LocalDateTime.now().isAfter(dueTime);
    }

    private Long resolveOverdueMinutes(String status, LocalDateTime dueTime, LocalDateTime completedTime) {
        if (dueTime == null) {
            return 0L;
        }
        if (STATUS_DONE.equals(status) && completedTime != null) {
            return completedTime.isAfter(dueTime) ? Duration.between(dueTime, completedTime).toMinutes() : 0L;
        }
        if (LocalDateTime.now().isAfter(dueTime)) {
            return Duration.between(dueTime, LocalDateTime.now()).toMinutes();
        }
        return 0L;
    }

    private Boolean resolveCompletedWithinSla(LocalDateTime completedTime, LocalDateTime dueTime) {
        if (completedTime == null || dueTime == null) {
            return null;
        }
        return !completedTime.isAfter(dueTime);
    }

    private String writeScopeCodes(List<String> scopeUnitCodes) {
        if (scopeUnitCodes == null || scopeUnitCodes.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(scopeUnitCodes);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "路由范围快照写入失败", ex);
        }
    }

    private List<String> readScopeCodes(String scopeUnitCodesJson) {
        if (!StringUtils.hasText(scopeUnitCodesJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(scopeUnitCodesJson, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String resolveSessionDisplayName(RbacUserSession session) {
        if (session == null) {
            return null;
        }
        if (StringUtils.hasText(session.getNickname()) && StringUtils.hasText(session.getUsername())) {
            return session.getNickname() + " (" + session.getUsername() + ")";
        }
        if (StringUtils.hasText(session.getNickname())) {
            return session.getNickname();
        }
        return session.getUsername();
    }

    private void recordTimeline(OrgRelationExecution target,
                                String eventType,
                                String eventSummary,
                                String eventDetail,
                                OrgRelationExecution before,
                                OrgRelationExecution after,
                                RbacUserSession session) {
        if (target == null || target.getId() == null) {
            return;
        }
        OrgRelationExecutionTimeline entity = new OrgRelationExecutionTimeline();
        entity.setTenantId(target.getTenantId());
        entity.setExecutionId(target.getId());
        entity.setEventType(eventType);
        entity.setEventSummary(eventSummary);
        entity.setEventDetail(eventDetail);
        entity.setActorUserId(session == null ? null : session.getUserId());
        entity.setActorName(resolveSessionDisplayName(session));
        entity.setBeforeStatus(before == null ? null : before.getStatus());
        entity.setAfterStatus(after == null ? null : after.getStatus());
        entity.setBeforeAssigneeName(before == null ? null : before.getAssigneeName());
        entity.setAfterAssigneeName(after == null ? null : after.getAssigneeName());
        entity.setBeforePriority(before == null ? null : before.getPriority());
        entity.setAfterPriority(after == null ? null : after.getPriority());
        entity.setBeforeSlaHours(before == null ? null : before.getSlaHours());
        entity.setAfterSlaHours(after == null ? null : after.getSlaHours());
        entity.setBeforeDueTime(before == null ? null : before.getDueTime());
        entity.setAfterDueTime(after == null ? null : after.getDueTime());
        entity.setBeforeResultSummary(before == null ? null : before.getResultSummary());
        entity.setAfterResultSummary(after == null ? null : after.getResultSummary());
        entity.setCreateTime(LocalDateTime.now());
        orgRelationExecutionTimelineRepository.save(entity);
    }

    private OrgRelationExecution copyExecution(OrgRelationExecution source) {
        OrgRelationExecution copied = new OrgRelationExecution();
        copied.setId(source.getId());
        copied.setTenantId(source.getTenantId());
        copied.setRelationId(source.getRelationId());
        copied.setRelationType(source.getRelationType());
        copied.setRelationName(source.getRelationName());
        copied.setSceneCode(source.getSceneCode());
        copied.setSceneName(source.getSceneName());
        copied.setExecutionTitle(source.getExecutionTitle());
        copied.setExecutionContent(source.getExecutionContent());
        copied.setPriority(source.getPriority());
        copied.setSlaHours(source.getSlaHours());
        copied.setDueTime(source.getDueTime());
        copied.setStatus(source.getStatus());
        copied.setSourceOrgUnitId(source.getSourceOrgUnitId());
        copied.setSourceOrgUnitCode(source.getSourceOrgUnitCode());
        copied.setSourceOrgUnitName(source.getSourceOrgUnitName());
        copied.setTargetOrgUnitId(source.getTargetOrgUnitId());
        copied.setTargetOrgUnitCode(source.getTargetOrgUnitCode());
        copied.setTargetOrgUnitName(source.getTargetOrgUnitName());
        copied.setRouteScopeSummary(source.getRouteScopeSummary());
        copied.setScopeUnitCount(source.getScopeUnitCount());
        copied.setScopeUnitCodesJson(source.getScopeUnitCodesJson());
        copied.setRequestedByUserId(source.getRequestedByUserId());
        copied.setRequestedByName(source.getRequestedByName());
        copied.setAssigneeUserId(source.getAssigneeUserId());
        copied.setAssigneeName(source.getAssigneeName());
        copied.setResultSummary(source.getResultSummary());
        copied.setStartedTime(source.getStartedTime());
        copied.setCompletedTime(source.getCompletedTime());
        copied.setCreateTime(source.getCreateTime());
        copied.setUpdateTime(source.getUpdateTime());
        return copied;
    }

    private String buildCreateDetail(OrgRelationExecution entity) {
        List<String> details = new ArrayList<>();
        details.add("状态初始化为“" + entity.getStatus() + "”");
        details.add("优先级为“" + entity.getPriority() + "”");
        if (entity.getSlaHours() != null) {
            details.add("SLA 为 " + entity.getSlaHours() + " 小时");
        }
        if (entity.getDueTime() != null) {
            details.add("截止时间为 " + entity.getDueTime());
        }
        if (StringUtils.hasText(entity.getAssigneeName())) {
            details.add("处理人指派给“" + entity.getAssigneeName() + "”");
        } else {
            details.add("当前未分配处理人");
        }
        return String.join("；", details);
    }

    private String buildUpdateSummary(OrgRelationExecution before, OrgRelationExecution after) {
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(before.getStatus(), after.getStatus())) {
            changes.add("状态更新");
        }
        if (!Objects.equals(before.getAssigneeUserId(), after.getAssigneeUserId())) {
            changes.add("处理人调整");
        }
        if (!Objects.equals(before.getPriority(), after.getPriority())) {
            changes.add("优先级调整");
        }
        if (!Objects.equals(before.getSlaHours(), after.getSlaHours()) || !Objects.equals(before.getDueTime(), after.getDueTime())) {
            changes.add("SLA 调整");
        }
        if (!Objects.equals(before.getResultSummary(), after.getResultSummary())) {
            changes.add("处理结果更新");
        }
        if (changes.isEmpty()) {
            return "更新执行项";
        }
        return String.join(" / ", changes);
    }

    private String buildUpdateDetail(OrgRelationExecution before, OrgRelationExecution after) {
        List<String> details = new ArrayList<>();
        if (!Objects.equals(before.getStatus(), after.getStatus())) {
            details.add("状态: " + before.getStatus() + " -> " + after.getStatus());
        }
        if (!Objects.equals(before.getAssigneeName(), after.getAssigneeName())) {
            details.add("处理人: " + defaultText(before.getAssigneeName()) + " -> " + defaultText(after.getAssigneeName()));
        }
        if (!Objects.equals(before.getPriority(), after.getPriority())) {
            details.add("优先级: " + before.getPriority() + " -> " + after.getPriority());
        }
        if (!Objects.equals(before.getSlaHours(), after.getSlaHours())) {
            details.add("SLA: " + defaultText(before.getSlaHours()) + " -> " + defaultText(after.getSlaHours()) + " 小时");
        }
        if (!Objects.equals(before.getDueTime(), after.getDueTime())) {
            details.add("截止时间: " + defaultText(before.getDueTime()) + " -> " + defaultText(after.getDueTime()));
        }
        if (!Objects.equals(before.getResultSummary(), after.getResultSummary()) && StringUtils.hasText(after.getResultSummary())) {
            details.add("处理结果已更新");
        }
        return details.isEmpty() ? "未发生结构化字段变化" : String.join("；", details);
    }

    private String defaultText(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String resolveUserDisplayName(RbacUser user) {
        if (user == null) {
            return null;
        }
        if (StringUtils.hasText(user.getNickname()) && StringUtils.hasText(user.getUsername())) {
            return user.getNickname() + " (" + user.getUsername() + ")";
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        return user.getUsername();
    }

    private String defaultPriority(String relationType) {
        return switch (relationType) {
            case "SERVICE", "SUPERVISE", "MATRIX" -> PRIORITY_P1;
            case "COOPERATE", "OWNERSHIP" -> PRIORITY_P2;
            default -> PRIORITY_P2;
        };
    }

    private String resolveSceneCode(String relationType) {
        return switch (relationType) {
            case "SUPERVISE" -> "SUPERVISE_BOARD";
            case "COOPERATE" -> "COOPERATE_TASK";
            case "SERVICE" -> "SERVICE_DESK";
            case "OWNERSHIP" -> "OWNERSHIP_GOVERNANCE";
            case "MATRIX" -> "MATRIX_REPORT";
            default -> "ORG_RELATION";
        };
    }

    private String resolveSceneName(String relationType) {
        return switch (relationType) {
            case "SUPERVISE" -> "主管督办";
            case "COOPERATE" -> "协同任务";
            case "SERVICE" -> "服务归口";
            case "OWNERSHIP" -> "归口治理";
            case "MATRIX" -> "矩阵汇报";
            default -> "关系执行";
        };
    }
}
