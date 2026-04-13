package com.lingsql.rbac.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.rbac.entity.RbacRole;
import com.lingsql.rbac.entity.RoleScopeReleaseRecord;
import com.lingsql.rbac.model.DataScopePreviewView;
import com.lingsql.rbac.model.DataScopeRule;
import com.lingsql.rbac.model.RoleDataScopePreviewRequest;
import com.lingsql.rbac.model.RoleScopeReleaseCreateRequest;
import com.lingsql.rbac.model.RoleScopeReleaseRollbackRequest;
import com.lingsql.rbac.model.RoleScopeReleaseReviewRequest;
import com.lingsql.rbac.model.RoleScopeReleaseView;
import com.lingsql.rbac.model.RoleUpdateRequest;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RoleScopeReleaseRecordRepository;
import com.lingsql.rbac.security.RbacUserSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 角色数据权限发布单服务。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Service
@RequiredArgsConstructor
public class RoleScopeReleaseService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_APPLIED = "APPLIED";
    private static final DateTimeFormatter TITLE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final RoleScopeReleaseRecordRepository roleScopeReleaseRecordRepository;
    private final RbacRoleRepository roleRepository;
    private final RoleAdminService roleAdminService;
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
    public List<RoleScopeReleaseView> listRecords(String tenantId, String releaseStatus, String sessionTenant, boolean sysAdmin) {
        String resolvedTenantId = resolveTenantId(tenantId, sessionTenant, sysAdmin);
        String normalizedStatus = normalizeStatus(releaseStatus, false);
        List<RoleScopeReleaseRecord> records = StringUtils.hasText(normalizedStatus)
                ? roleScopeReleaseRecordRepository.findByTenantIdAndReleaseStatusOrderByCreateTimeDescIdDesc(resolvedTenantId, normalizedStatus)
                : roleScopeReleaseRecordRepository.findByTenantIdOrderByCreateTimeDescIdDesc(resolvedTenantId);
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
    public RoleScopeReleaseView createRecord(RoleScopeReleaseCreateRequest request,
                                             String sessionTenant,
                                             boolean sysAdmin,
                                             RbacUserSession session) {
        if (request == null || request.getPreviewRequest() == null || request.getPreviewRequest().getRoleId() == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "角色数据权限发布单请求不能为空");
        }
        if (session == null || session.getUserId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        String resolvedTenantId = resolveTenantId(
                StringUtils.hasText(request.getTenantId()) ? request.getTenantId() : request.getPreviewRequest().getTenantId(),
                sessionTenant,
                sysAdmin
        );
        RoleDataScopePreviewRequest previewRequest = request.getPreviewRequest();
        previewRequest.setTenantId(resolvedTenantId);
        DataScopePreviewView preview = dataScopePreviewService.previewRoleScope(previewRequest, sessionTenant, sysAdmin);
        RbacRole role = roleRepository.findById(previewRequest.getRoleId())
                .orElseThrow(() -> new BizException(ErrorCode.ROLE_NOT_FOUND, "角色不存在"));
        validateTenantAccess(role.getTenantId(), resolvedTenantId, sysAdmin);
        LocalDateTime now = LocalDateTime.now();

        RoleScopeReleaseRecord entity = new RoleScopeReleaseRecord();
        entity.setTenantId(resolvedTenantId);
        entity.setReleaseTitle(resolveReleaseTitle(request.getReleaseTitle(), role.getRoleName()));
        entity.setRoleId(role.getId());
        entity.setRoleCode(role.getRoleCode());
        entity.setRoleName(role.getRoleName());
        entity.setBaselineSummary(preview.getDiff() == null ? null : preview.getDiff().getBaselineSummary());
        entity.setTargetSummary(preview.getDiff() == null ? preview.getSummary() : preview.getDiff().getTargetSummary());
        entity.setImpactSummary(preview.getImpactSummary());
        entity.setMatchedOrgCount(preview.getMatchedOrgCount());
        entity.setAffectedUserCount(preview.getAffectedUserCount());
        entity.setAddedOrgCount(preview.getDiff() == null ? 0 : preview.getDiff().getAddedOrgCount());
        entity.setRemovedOrgCount(preview.getDiff() == null ? 0 : preview.getDiff().getRemovedOrgCount());
        entity.setPreviewRequestJson(writeJson(previewRequest));
        entity.setPreviewSnapshotJson(writeJson(preview));
        entity.setChangeRemark(normalizeText(request.getChangeRemark()));
        entity.setReleaseStatus(STATUS_DRAFT);
        entity.setRequestedByUserId(session.getUserId());
        entity.setRequestedByName(resolveDisplayName(session));
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return toView(roleScopeReleaseRecordRepository.save(entity));
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
    public RoleScopeReleaseView reviewRecord(Long id,
                                             RoleScopeReleaseReviewRequest request,
                                             String sessionTenant,
                                             boolean sysAdmin,
                                             RbacUserSession session) {
        if (id == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "发布单ID不能为空");
        }
        RoleScopeReleaseRecord entity = roleScopeReleaseRecordRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.INVALID_PARAM, "角色数据权限发布单不存在"));
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
        return toView(roleScopeReleaseRecordRepository.save(entity));
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
    public RoleScopeReleaseView applyRecord(Long id,
                                            String sessionTenant,
                                            boolean sysAdmin,
                                            RbacUserSession session) {
        if (id == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "发布单ID不能为空");
        }
        RoleScopeReleaseRecord entity = roleScopeReleaseRecordRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.INVALID_PARAM, "角色数据权限发布单不存在"));
        validateTenantAccess(entity.getTenantId(), sessionTenant, sysAdmin);
        if (!STATUS_APPROVED.equals(entity.getReleaseStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "仅已审批的发布单允许应用");
        }
        validateSegregation(entity, session, "应用");
        RoleDataScopePreviewRequest previewRequest = readPreviewRequest(entity.getPreviewRequestJson());
        validateBaselineDrift(previewRequest.getRoleId(), previewRequest.getBaselineDataScope());
        RoleUpdateRequest updateRequest = new RoleUpdateRequest();
        updateRequest.setDataScope(previewRequest.getDataScope());
        roleAdminService.updateRole(previewRequest.getRoleId(), updateRequest, sessionTenant, sysAdmin);
        entity.setReleaseStatus(STATUS_APPLIED);
        entity.setAppliedByUserId(session == null ? null : session.getUserId());
        entity.setAppliedByName(resolveDisplayName(session));
        entity.setAppliedTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return toView(roleScopeReleaseRecordRepository.save(entity));
    }

    /**
     * 基于已应用发布单生成回滚单。
     *
     * @param id            源发布单ID
     * @param request       回滚请求
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @param session       当前会话
     * @return 新发布单
     * @author 余炘洋
     * @date 2026-04-13
     */
    @Transactional
    public RoleScopeReleaseView createRollbackRecord(Long id,
                                                     RoleScopeReleaseRollbackRequest request,
                                                     String sessionTenant,
                                                     boolean sysAdmin,
                                                     RbacUserSession session) {
        if (id == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "源发布单ID不能为空");
        }
        RoleScopeReleaseRecord source = roleScopeReleaseRecordRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.INVALID_PARAM, "角色数据权限发布单不存在"));
        validateTenantAccess(source.getTenantId(), sessionTenant, sysAdmin);
        if (!STATUS_APPLIED.equals(source.getReleaseStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "仅已应用的发布单允许生成回滚单");
        }
        RoleDataScopePreviewRequest sourcePreviewRequest = readPreviewRequest(source.getPreviewRequestJson());
        validateTargetDrift(sourcePreviewRequest.getRoleId(), sourcePreviewRequest.getDataScope());

        RoleScopeReleaseCreateRequest rollbackRequest = new RoleScopeReleaseCreateRequest();
        rollbackRequest.setTenantId(source.getTenantId());
        rollbackRequest.setReleaseTitle(resolveRollbackTitle(request == null ? null : request.getReleaseTitle(), source));
        rollbackRequest.setChangeRemark(resolveRollbackRemark(request == null ? null : request.getChangeRemark(), source));
        rollbackRequest.setPreviewRequest(buildRollbackPreviewRequest(source.getTenantId(), sourcePreviewRequest));
        return createRecord(rollbackRequest, sessionTenant, sysAdmin, session);
    }

    private void validateBaselineDrift(Long roleId, DataScopeRule baselineRule) {
        if (roleId == null) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "发布单缺少角色信息");
        }
        DataScopeRule currentRule = roleAdminService.resolveCurrentRoleDataScope(roleId);
        if (!java.util.Objects.equals(normalizeRule(currentRule), normalizeRule(baselineRule))) {
            throw new BizException(ErrorCode.CONFLICT, "当前角色数据权限基线已变化，请重新生成发布单并重新审核");
        }
    }

    private void validateTargetDrift(Long roleId, DataScopeRule targetRule) {
        if (roleId == null) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "发布单缺少角色信息");
        }
        DataScopeRule currentRule = roleAdminService.resolveCurrentRoleDataScope(roleId);
        if (!java.util.Objects.equals(normalizeRule(currentRule), normalizeRule(targetRule))) {
            throw new BizException(ErrorCode.CONFLICT, "当前角色数据权限已偏离已应用发布单状态，请先确认最新状态后再生成回滚单");
        }
    }

    private RoleDataScopePreviewRequest buildRollbackPreviewRequest(String tenantId, RoleDataScopePreviewRequest source) {
        RoleDataScopePreviewRequest request = new RoleDataScopePreviewRequest();
        request.setTenantId(tenantId);
        request.setRoleId(source.getRoleId());
        request.setDataScope(source.getBaselineDataScope());
        request.setBaselineDataScope(source.getDataScope());
        request.setOrgUnitIds(source.getOrgUnitIds() == null ? List.of() : new java.util.ArrayList<>(source.getOrgUnitIds()));
        request.setSampleSql(source.getSampleSql());
        return request;
    }

    private DataScopeRule normalizeRule(DataScopeRule rule) {
        DataScopeRule normalized = new DataScopeRule();
        if (rule == null) {
            normalized.setScopeType("TENANT");
            normalized.setScopeColumn("");
            normalized.setScopeRule("");
            normalized.setRelationTypes(List.of());
            return normalized;
        }
        normalized.setScopeType(StringUtils.hasText(rule.getScopeType()) ? rule.getScopeType().trim().toUpperCase(Locale.ROOT) : "TENANT");
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

    private RoleDataScopePreviewRequest readPreviewRequest(String json) {
        if (!StringUtils.hasText(json)) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "发布单缺少预览请求快照");
        }
        try {
            return objectMapper.readValue(json, RoleDataScopePreviewRequest.class);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "发布单预览请求解析失败", ex);
        }
    }

    private RoleScopeReleaseView toView(RoleScopeReleaseRecord entity) {
        RoleScopeReleaseView view = new RoleScopeReleaseView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setRoleId(entity.getRoleId());
        view.setRoleCode(entity.getRoleCode());
        view.setRoleName(entity.getRoleName());
        view.setReleaseTitle(entity.getReleaseTitle());
        view.setBaselineSummary(entity.getBaselineSummary());
        view.setTargetSummary(entity.getTargetSummary());
        view.setImpactSummary(entity.getImpactSummary());
        view.setMatchedOrgCount(entity.getMatchedOrgCount());
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
            throw new BizException(ErrorCode.SYSTEM_ERROR, "角色数据权限发布单快照写入失败", ex);
        }
    }

    private String resolveReleaseTitle(String releaseTitle, String roleName) {
        if (StringUtils.hasText(releaseTitle)) {
            return releaseTitle.trim();
        }
        return "角色数据权限发布单 - " + roleName + " - " + TITLE_TIME_FORMATTER.format(LocalDateTime.now());
    }

    private String resolveRollbackTitle(String releaseTitle, RoleScopeReleaseRecord source) {
        if (StringUtils.hasText(releaseTitle)) {
            return releaseTitle.trim();
        }
        String baseTitle = source == null || !StringUtils.hasText(source.getReleaseTitle()) ? "角色数据权限发布单" : source.getReleaseTitle().trim();
        return "回滚-" + baseTitle + "-" + TITLE_TIME_FORMATTER.format(LocalDateTime.now());
    }

    private String resolveRollbackRemark(String changeRemark, RoleScopeReleaseRecord source) {
        String prefix = "回滚来源发布单#" + (source == null ? "-" : source.getId());
        if (!StringUtils.hasText(changeRemark)) {
            return prefix;
        }
        return prefix + "；" + changeRemark.trim();
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

    private void validateSegregation(RoleScopeReleaseRecord entity, RbacUserSession session, String phase) {
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
