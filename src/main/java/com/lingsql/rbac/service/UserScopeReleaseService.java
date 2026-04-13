package com.lingsql.rbac.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.rbac.entity.RbacUser;
import com.lingsql.rbac.entity.UserScopeReleaseRecord;
import com.lingsql.rbac.model.DataScopePreviewDiffView;
import com.lingsql.rbac.model.DataScopePreviewView;
import com.lingsql.rbac.model.DataScopeRule;
import com.lingsql.rbac.model.UserDataScopePreviewRequest;
import com.lingsql.rbac.model.UserScopeReleaseCreateRequest;
import com.lingsql.rbac.model.UserScopeReleaseReviewRequest;
import com.lingsql.rbac.model.UserScopeReleaseView;
import com.lingsql.rbac.model.UserUpdateRequest;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.rbac.repository.UserScopeReleaseRecordRepository;
import com.lingsql.rbac.security.RbacUserSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * 用户数据权限发布单服务。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Service
@RequiredArgsConstructor
public class UserScopeReleaseService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_APPLIED = "APPLIED";
    private static final DateTimeFormatter TITLE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserScopeReleaseRecordRepository userScopeReleaseRecordRepository;
    private final RbacUserRepository userRepository;
    private final UserAdminService userAdminService;
    private final DataScopePreviewService dataScopePreviewService;
    private final ObjectMapper objectMapper;

    /**
     * 查询发布单列表。
     *
     * @param tenantId      租户ID
     * @param releaseStatus 发布状态
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @return 发布单列表
     * @author 余炘洋
     * @date 2026-04-13
     */
    public List<UserScopeReleaseView> listRecords(String tenantId, String releaseStatus, String sessionTenant, boolean sysAdmin) {
        String resolvedTenantId = resolveTenantId(tenantId, sessionTenant, sysAdmin);
        String normalizedStatus = normalizeStatus(releaseStatus, false);
        List<UserScopeReleaseRecord> records = StringUtils.hasText(normalizedStatus)
                ? userScopeReleaseRecordRepository.findByTenantIdAndReleaseStatusOrderByCreateTimeDescIdDesc(resolvedTenantId, normalizedStatus)
                : userScopeReleaseRecordRepository.findByTenantIdOrderByCreateTimeDescIdDesc(resolvedTenantId);
        return records.stream().map(this::toView).toList();
    }

    /**
     * 创建发布单。
     *
     * @param request       创建请求
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @param session       当前会话
     * @return 发布单视图
     * @author 余炘洋
     * @date 2026-04-13
     */
    @Transactional
    public UserScopeReleaseView createRecord(UserScopeReleaseCreateRequest request,
                                             String sessionTenant,
                                             boolean sysAdmin,
                                             RbacUserSession session) {
        if (request == null || request.getPreviewRequest() == null || request.getPreviewRequest().getUserId() == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "用户数据权限发布单请求不能为空");
        }
        if (session == null || session.getUserId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        String resolvedTenantId = resolveTenantId(
                StringUtils.hasText(request.getTenantId()) ? request.getTenantId() : request.getPreviewRequest().getTenantId(),
                sessionTenant,
                sysAdmin
        );
        UserDataScopePreviewRequest previewRequest = request.getPreviewRequest();
        previewRequest.setTenantId(resolvedTenantId);
        DataScopePreviewView preview = dataScopePreviewService.previewUserScope(previewRequest, sessionTenant, sysAdmin);
        RbacUser user = userRepository.findById(previewRequest.getUserId())
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, "用户不存在"));
        validateTenantAccess(user.getTenantId(), resolvedTenantId, sysAdmin);
        LocalDateTime now = LocalDateTime.now();

        UserScopeReleaseRecord entity = new UserScopeReleaseRecord();
        entity.setTenantId(resolvedTenantId);
        entity.setReleaseTitle(resolveReleaseTitle(request.getReleaseTitle(), user.getUsername(), user.getNickname()));
        entity.setUserId(user.getId());
        entity.setUsername(user.getUsername());
        entity.setNickname(user.getNickname());
        DataScopePreviewDiffView diff = preview.getDiff();
        entity.setBaselineSummary(diff == null ? null : diff.getBaselineSummary());
        entity.setTargetSummary(diff == null ? preview.getSummary() : diff.getTargetSummary());
        entity.setImpactSummary(preview.getImpactSummary());
        entity.setMatchedOrgCount(preview.getMatchedOrgCount());
        entity.setAffectedRoleCount(preview.getAffectedRoleCount());
        entity.setAffectedUserCount(preview.getAffectedUserCount());
        entity.setAddedOrgCount(diff == null ? 0 : diff.getAddedOrgCount());
        entity.setRemovedOrgCount(diff == null ? 0 : diff.getRemovedOrgCount());
        entity.setPreviewRequestJson(writeJson(previewRequest));
        entity.setPreviewSnapshotJson(writeJson(preview));
        entity.setChangeRemark(normalizeText(request.getChangeRemark()));
        entity.setReleaseStatus(STATUS_DRAFT);
        entity.setRequestedByUserId(session.getUserId());
        entity.setRequestedByName(resolveDisplayName(session));
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return toView(userScopeReleaseRecordRepository.save(entity));
    }

    /**
     * 审核发布单。
     *
     * @param id            发布单ID
     * @param request       审核请求
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @param session       当前会话
     * @return 发布单视图
     * @author 余炘洋
     * @date 2026-04-13
     */
    @Transactional
    public UserScopeReleaseView reviewRecord(Long id,
                                             UserScopeReleaseReviewRequest request,
                                             String sessionTenant,
                                             boolean sysAdmin,
                                             RbacUserSession session) {
        if (id == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "发布单ID不能为空");
        }
        UserScopeReleaseRecord entity = userScopeReleaseRecordRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.INVALID_PARAM, "用户数据权限发布单不存在"));
        validateTenantAccess(entity.getTenantId(), sessionTenant, sysAdmin);
        if (!STATUS_DRAFT.equals(entity.getReleaseStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "当前发布单状态不允许审核");
        }
        validateSegregation(entity, session, "审核");
        String decision = normalizeStatus(request == null ? null : request.getDecision(), true);
        if (!STATUS_APPROVED.equals(decision) && !STATUS_REJECTED.equals(decision)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "审核结论仅支持 APPROVED / REJECTED");
        }
        entity.setReleaseStatus(decision);
        entity.setReviewComment(normalizeText(request == null ? null : request.getReviewComment()));
        entity.setReviewedByUserId(session == null ? null : session.getUserId());
        entity.setReviewedByName(resolveDisplayName(session));
        entity.setReviewedTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return toView(userScopeReleaseRecordRepository.save(entity));
    }

    /**
     * 应用发布单。
     *
     * @param id            发布单ID
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @param session       当前会话
     * @return 发布单视图
     * @author 余炘洋
     * @date 2026-04-13
     */
    @Transactional
    public UserScopeReleaseView applyRecord(Long id,
                                            String sessionTenant,
                                            boolean sysAdmin,
                                            RbacUserSession session) {
        if (id == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "发布单ID不能为空");
        }
        UserScopeReleaseRecord entity = userScopeReleaseRecordRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.INVALID_PARAM, "用户数据权限发布单不存在"));
        validateTenantAccess(entity.getTenantId(), sessionTenant, sysAdmin);
        if (!STATUS_APPROVED.equals(entity.getReleaseStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "仅已审批的发布单允许应用");
        }
        validateSegregation(entity, session, "应用");
        UserDataScopePreviewRequest previewRequest = readPreviewRequest(entity.getPreviewRequestJson());
        validateBaselineDrift(previewRequest.getUserId(), previewRequest.getBaselineDataScope(),
                previewRequest.getBaselineRoleIds(), previewRequest.getBaselineOrgUnitIds());
        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setDataScope(previewRequest.getDataScope());
        userAdminService.updateUser(previewRequest.getUserId(), updateRequest, sessionTenant, sysAdmin);
        entity.setReleaseStatus(STATUS_APPLIED);
        entity.setAppliedByUserId(session == null ? null : session.getUserId());
        entity.setAppliedByName(resolveDisplayName(session));
        entity.setAppliedTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return toView(userScopeReleaseRecordRepository.save(entity));
    }

    private void validateBaselineDrift(Long userId,
                                       DataScopeRule baselineRule,
                                       List<Long> baselineRoleIds,
                                       List<Long> baselineOrgUnitIds) {
        if (userId == null) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "发布单缺少用户信息");
        }
        if (!java.util.Objects.equals(
                normalizeRule(userAdminService.resolveCurrentUserDataScope(userId)),
                normalizeRule(baselineRule)
        )) {
            throw new BizException(ErrorCode.CONFLICT, "当前用户数据权限基线已变化，请重新生成发布单并重新审核");
        }
        if (!normalizeIds(userAdminService.resolveCurrentUserRoleIds(userId)).equals(normalizeIds(baselineRoleIds))) {
            throw new BizException(ErrorCode.CONFLICT, "当前用户角色绑定已变化，请重新生成发布单并重新审核");
        }
        if (!normalizeIds(userAdminService.resolveCurrentUserOrgUnitIds(userId)).equals(normalizeIds(baselineOrgUnitIds))) {
            throw new BizException(ErrorCode.CONFLICT, "当前用户组织归属已变化，请重新生成发布单并重新审核");
        }
    }

    private DataScopeRule normalizeRule(DataScopeRule rule) {
        DataScopeRule normalized = new DataScopeRule();
        if (rule == null) {
            normalized.setScopeType("INHERIT");
            normalized.setScopeColumn("");
            normalized.setScopeRule("");
            normalized.setRelationTypes(List.of());
            return normalized;
        }
        normalized.setScopeType(StringUtils.hasText(rule.getScopeType()) ? rule.getScopeType().trim().toUpperCase(Locale.ROOT) : "INHERIT");
        normalized.setScopeColumn(StringUtils.hasText(rule.getScopeColumn()) ? rule.getScopeColumn().trim() : "");
        normalized.setScopeRule(StringUtils.hasText(rule.getScopeRule()) ? rule.getScopeRule().trim() : "");
        normalized.setRelationTypes(rule.getRelationTypes() == null
                ? List.of()
                : rule.getRelationTypes().stream()
                .filter(StringUtils::hasText)
                .map(item -> item.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList());
        return normalized;
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return new java.util.ArrayList<>(new LinkedHashSet<>(ids.stream().filter(java.util.Objects::nonNull).toList()));
    }

    private UserDataScopePreviewRequest readPreviewRequest(String json) {
        if (!StringUtils.hasText(json)) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "发布单缺少预览请求快照");
        }
        try {
            return objectMapper.readValue(json, UserDataScopePreviewRequest.class);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "发布单预览请求解析失败", ex);
        }
    }

    private UserScopeReleaseView toView(UserScopeReleaseRecord entity) {
        UserScopeReleaseView view = new UserScopeReleaseView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setUserId(entity.getUserId());
        view.setUsername(entity.getUsername());
        view.setNickname(entity.getNickname());
        view.setReleaseTitle(entity.getReleaseTitle());
        view.setBaselineSummary(entity.getBaselineSummary());
        view.setTargetSummary(entity.getTargetSummary());
        view.setImpactSummary(entity.getImpactSummary());
        view.setMatchedOrgCount(entity.getMatchedOrgCount());
        view.setAffectedRoleCount(entity.getAffectedRoleCount());
        view.setAffectedUserCount(entity.getAffectedUserCount());
        view.setAddedOrgCount(entity.getAddedOrgCount());
        view.setRemovedOrgCount(entity.getRemovedOrgCount());
        view.setChangeRemark(entity.getChangeRemark());
        view.setReleaseStatus(entity.getReleaseStatus());
        view.setRequestedByUserId(entity.getRequestedByUserId());
        view.setRequestedByName(entity.getRequestedByName());
        view.setReviewedByUserId(entity.getReviewedByUserId());
        view.setReviewedByName(entity.getReviewedByName());
        view.setReviewComment(entity.getReviewComment());
        view.setReviewedTime(entity.getReviewedTime());
        view.setAppliedByUserId(entity.getAppliedByUserId());
        view.setAppliedByName(entity.getAppliedByName());
        view.setAppliedTime(entity.getAppliedTime());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "用户数据权限发布单快照写入失败", ex);
        }
    }

    private String resolveReleaseTitle(String releaseTitle, String username, String nickname) {
        if (StringUtils.hasText(releaseTitle)) {
            return releaseTitle.trim();
        }
        String displayName = StringUtils.hasText(nickname) ? nickname + " (" + username + ")" : username;
        return "用户数据权限发布单 - " + displayName + " - " + TITLE_TIME_FORMATTER.format(LocalDateTime.now());
    }

    private String normalizeStatus(String status, boolean required) {
        if (!StringUtils.hasText(status)) {
            if (required) {
                throw new BizException(ErrorCode.INVALID_PARAM, "发布单状态不能为空");
            }
            return null;
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveTenantId(String requestTenant, String sessionTenant, boolean sysAdmin) {
        if (sysAdmin) {
            if (!StringUtils.hasText(requestTenant)) {
                throw new BizException(ErrorCode.INVALID_PARAM, "请先选择要治理的租户");
            }
            return requestTenant.trim();
        }
        if (!StringUtils.hasText(sessionTenant)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户信息缺失");
        }
        if (StringUtils.hasText(requestTenant) && !sessionTenant.trim().equals(requestTenant.trim())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问");
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

    private void validateSegregation(UserScopeReleaseRecord entity, RbacUserSession session, String phase) {
        if (entity == null || entity.getRequestedByUserId() == null || session == null || session.getUserId() == null) {
            return;
        }
        if (entity.getRequestedByUserId().equals(session.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "发布单创建人不能" + phase + "自己的发布单");
        }
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String resolveDisplayName(RbacUserSession session) {
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
}
