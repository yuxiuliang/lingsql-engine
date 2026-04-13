package com.lingsql.rbac.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.rbac.entity.MenuReleaseRecord;
import com.lingsql.rbac.model.MenuImpactPreviewRequest;
import com.lingsql.rbac.model.MenuImpactPreviewView;
import com.lingsql.rbac.model.MenuReleaseCreateRequest;
import com.lingsql.rbac.model.MenuReleaseReviewRequest;
import com.lingsql.rbac.model.MenuReleaseView;
import com.lingsql.rbac.model.TenantMenuRuleUpdateRequest;
import com.lingsql.rbac.repository.MenuReleaseRecordRepository;
import com.lingsql.rbac.security.RbacUserSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 菜单治理发布单服务。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Service
@RequiredArgsConstructor
public class MenuReleaseService {

    private static final String RELEASE_TYPE_GLOBAL = "GLOBAL_RULE";
    private static final String RELEASE_TYPE_TENANT = "TENANT_RULE";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_APPLIED = "APPLIED";
    private static final DateTimeFormatter TITLE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MenuReleaseRecordRepository menuReleaseRecordRepository;
    private final MenuAdminService menuAdminService;
    private final ObjectMapper objectMapper;

    /**
     * 查询菜单发布单列表。
     *
     * @param tenantId      租户ID
     * @param releaseStatus 发布状态
     * @param sessionTenant 会话租户
     * @param sysAdmin      是否系统管理员
     * @return 发布单列表
     * @author 余炘洋
     * @date 2026-04-13
     */
    public List<MenuReleaseView> listRecords(String tenantId, String releaseStatus, String sessionTenant, boolean sysAdmin) {
        String resolvedTenantId = resolveTenantId(tenantId, sessionTenant, sysAdmin);
        String normalizedStatus = normalizeStatus(releaseStatus, false);
        List<MenuReleaseRecord> records = StringUtils.hasText(normalizedStatus)
                ? menuReleaseRecordRepository.findByTenantIdAndReleaseStatusOrderByCreateTimeDescIdDesc(resolvedTenantId, normalizedStatus)
                : menuReleaseRecordRepository.findByTenantIdOrderByCreateTimeDescIdDesc(resolvedTenantId);
        return records.stream().map(this::toView).toList();
    }

    /**
     * 创建菜单发布单。
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
    public MenuReleaseView createRecord(MenuReleaseCreateRequest request,
                                        String sessionTenant,
                                        boolean sysAdmin,
                                        RbacUserSession session) {
        if (request == null || request.getImpactRequest() == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "发布单请求不能为空");
        }
        if (session == null || session.getUserId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        String resolvedTenantId = resolveTenantId(
                StringUtils.hasText(request.getTenantId()) ? request.getTenantId() : request.getImpactRequest().getTenantId(),
                sessionTenant,
                sysAdmin
        );
        MenuImpactPreviewRequest impactRequest = request.getImpactRequest();
        impactRequest.setTenantId(resolvedTenantId);
        MenuImpactPreviewView impactSnapshot = menuAdminService.previewMenuImpact(impactRequest, sessionTenant, sysAdmin);
        LocalDateTime now = LocalDateTime.now();

        MenuReleaseRecord entity = new MenuReleaseRecord();
        entity.setTenantId(resolvedTenantId);
        entity.setReleaseType(resolveReleaseType(impactRequest));
        entity.setReleaseTitle(resolveReleaseTitle(request.getReleaseTitle(), impactSnapshot));
        entity.setMenuId(impactRequest.getMenuId());
        entity.setMenuCode(impactSnapshot.getMenuCode());
        entity.setMenuName(impactSnapshot.getMenuName());
        entity.setBaselineRuleSummary(impactSnapshot.getBaselineRuleSummary());
        entity.setTargetRuleSummary(impactSnapshot.getTargetRuleSummary());
        entity.setImpactSummary(impactSnapshot.getImpactSummary());
        entity.setChangedDimensionsJson(writeJson(impactSnapshot.getChangedDimensions()));
        entity.setAddedMenuCount(impactSnapshot.getAddedMenuCount());
        entity.setRemovedMenuCount(impactSnapshot.getRemovedMenuCount());
        entity.setImpactRequestJson(writeJson(impactRequest));
        entity.setImpactSnapshotJson(writeJson(impactSnapshot));
        entity.setChangeRemark(normalizeText(request.getChangeRemark()));
        entity.setReleaseStatus(STATUS_DRAFT);
        entity.setRequestedByUserId(session.getUserId());
        entity.setRequestedByName(resolveDisplayName(session));
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return toView(menuReleaseRecordRepository.save(entity));
    }

    /**
     * 审核菜单发布单。
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
    public MenuReleaseView reviewRecord(Long id,
                                        MenuReleaseReviewRequest request,
                                        String sessionTenant,
                                        boolean sysAdmin,
                                        RbacUserSession session) {
        if (id == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "发布单ID不能为空");
        }
        MenuReleaseRecord entity = menuReleaseRecordRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.INVALID_PARAM, "菜单发布单不存在"));
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
        return toView(menuReleaseRecordRepository.save(entity));
    }

    /**
     * 应用已审批的菜单发布单。
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
    public MenuReleaseView applyRecord(Long id,
                                       String sessionTenant,
                                       boolean sysAdmin,
                                       RbacUserSession session) {
        if (id == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "发布单ID不能为空");
        }
        MenuReleaseRecord entity = menuReleaseRecordRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.INVALID_PARAM, "菜单发布单不存在"));
        validateTenantAccess(entity.getTenantId(), sessionTenant, sysAdmin);
        if (!STATUS_APPROVED.equals(entity.getReleaseStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "仅已审批的发布单允许应用");
        }
        validateSegregation(entity, session, "应用");
        MenuImpactPreviewRequest impactRequest = readImpactRequest(entity.getImpactRequestJson());
        if (RELEASE_TYPE_GLOBAL.equals(entity.getReleaseType())) {
            menuAdminService.updateGlobalMenuRule(impactRequest.getMenuId(), impactRequest.getGlobalRule());
        } else if (RELEASE_TYPE_TENANT.equals(entity.getReleaseType())) {
            TenantMenuRuleUpdateRequest tenantRule = impactRequest.getTenantRule() == null
                    ? new TenantMenuRuleUpdateRequest()
                    : impactRequest.getTenantRule();
            if (!StringUtils.hasText(tenantRule.getTenantId())) {
                tenantRule.setTenantId(entity.getTenantId());
            }
            menuAdminService.upsertTenantMenuRule(
                    impactRequest.getMenuCode(),
                    tenantRule,
                    sysAdmin ? entity.getTenantId() : sessionTenant,
                    sysAdmin
            );
        } else {
            throw new BizException(ErrorCode.CONFIG_INVALID, "发布单类型不支持应用");
        }
        entity.setReleaseStatus(STATUS_APPLIED);
        entity.setAppliedByUserId(session == null ? null : session.getUserId());
        entity.setAppliedByName(resolveDisplayName(session));
        entity.setAppliedTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return toView(menuReleaseRecordRepository.save(entity));
    }

    private MenuImpactPreviewRequest readImpactRequest(String json) {
        if (!StringUtils.hasText(json)) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "发布单缺少影响分析请求快照");
        }
        try {
            return objectMapper.readValue(json, MenuImpactPreviewRequest.class);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "发布单影响分析请求解析失败", ex);
        }
    }

    private MenuReleaseView toView(MenuReleaseRecord entity) {
        MenuReleaseView view = new MenuReleaseView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setReleaseType(entity.getReleaseType());
        view.setReleaseTitle(entity.getReleaseTitle());
        view.setMenuId(entity.getMenuId());
        view.setMenuCode(entity.getMenuCode());
        view.setMenuName(entity.getMenuName());
        view.setBaselineRuleSummary(entity.getBaselineRuleSummary());
        view.setTargetRuleSummary(entity.getTargetRuleSummary());
        view.setImpactSummary(entity.getImpactSummary());
        view.setChangedDimensions(readStringList(entity.getChangedDimensionsJson()));
        view.setAddedMenuCount(entity.getAddedMenuCount());
        view.setRemovedMenuCount(entity.getRemovedMenuCount());
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

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "菜单发布单快照写入失败", ex);
        }
    }

    private String resolveReleaseType(MenuImpactPreviewRequest impactRequest) {
        return StringUtils.hasText(impactRequest.getMenuCode()) ? RELEASE_TYPE_TENANT : RELEASE_TYPE_GLOBAL;
    }

    private String resolveReleaseTitle(String releaseTitle, MenuImpactPreviewView impactSnapshot) {
        if (StringUtils.hasText(releaseTitle)) {
            return releaseTitle.trim();
        }
        String prefix = impactSnapshot != null && impactSnapshot.isTenantOverrideMode() ? "租户菜单发布单" : "全局菜单发布单";
        String menuName = impactSnapshot == null ? "未命名菜单" : impactSnapshot.getMenuName();
        return prefix + " - " + menuName + " - " + TITLE_TIME_FORMATTER.format(LocalDateTime.now());
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

    private void validateSegregation(MenuReleaseRecord entity, RbacUserSession session, String phase) {
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
