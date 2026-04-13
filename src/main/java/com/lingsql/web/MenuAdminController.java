package com.lingsql.web;

import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.model.MenuAdminView;
import com.lingsql.rbac.model.MenuImpactPreviewRequest;
import com.lingsql.rbac.model.MenuImpactPreviewView;
import com.lingsql.rbac.model.MenuPreviewRequest;
import com.lingsql.rbac.model.MenuReleaseCreateRequest;
import com.lingsql.rbac.model.MenuReleaseReviewRequest;
import com.lingsql.rbac.model.MenuReleaseView;
import com.lingsql.rbac.model.MenuRuleUpdateRequest;
import com.lingsql.rbac.model.MenuView;
import com.lingsql.rbac.model.TenantMenuAdminView;
import com.lingsql.rbac.model.TenantMenuRuleUpdateRequest;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.security.SecureTenantResolver;
import com.lingsql.rbac.service.MenuAdminService;
import com.lingsql.rbac.service.MenuReleaseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 菜单治理接口。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@RestController
@RequestMapping("/api/v1/admin/menus")
@RequiredArgsConstructor
public class MenuAdminController {

    private final MenuAdminService menuAdminService;
    private final MenuReleaseService menuReleaseService;
    private final AuditOperationExecutor auditOperationExecutor;

    /**
     * 查询全局菜单规则树。
     *
     * @return 菜单规则树
     * @author 余炘洋
     * @date 2026-04-12
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN:MENU:READ')")
    public ApiResponse<List<MenuAdminView>> listMenus() {
        RbacUserSession session = requireSession();
        if (!SecureTenantResolver.isSysAdmin(session)) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(menuAdminService.listGlobalMenus());
    }

    /**
     * 查询租户级菜单覆盖树。
     *
     * @param tenantId 租户ID
     * @return 覆盖树
     * @author 余炘洋
     * @date 2026-04-12
     */
    @GetMapping("/tenant")
    @PreAuthorize("hasAuthority('ADMIN:MENU:READ')")
    public ApiResponse<List<TenantMenuAdminView>> listTenantMenus(@RequestParam(required = false) String tenantId) {
        RbacUserSession session = requireSession();
        return ApiResponse.success(menuAdminService.listTenantMenus(
                tenantId,
                session.getTenantId(),
                SecureTenantResolver.isSysAdmin(session)
        ));
    }

    /**
     * 更新全局菜单规则。
     *
     * @param id      菜单ID
     * @param request 更新请求
     * @param http    请求
     * @return 更新后的菜单
     * @author 余炘洋
     * @date 2026-04-12
     */
    @PutMapping("/{id}/rule")
    @PreAuthorize("hasAuthority('ADMIN:MENU:WRITE')")
    public ApiResponse<MenuAdminView> updateMenuRule(
            @PathVariable Long id,
            @RequestBody MenuRuleUpdateRequest request,
            HttpServletRequest http
    ) {
        RbacUserSession session = requireSession();
        if (!SecureTenantResolver.isSysAdmin(session)) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "MENU_RULE_UPDATE",
                "MENU",
                request,
                http,
                () -> menuAdminService.updateGlobalMenuRule(id, request)
        );
    }

    /**
     * 保存租户级菜单覆盖。
     *
     * @param menuCode 菜单编码
     * @param request  更新请求
     * @param http     请求
     * @return 更新后的租户菜单
     * @author 余炘洋
     * @date 2026-04-12
     */
    @PutMapping("/tenant/{menuCode}")
    @PreAuthorize("hasAuthority('ADMIN:MENU:WRITE')")
    public ApiResponse<TenantMenuAdminView> updateTenantMenuRule(
            @PathVariable String menuCode,
            @RequestBody TenantMenuRuleUpdateRequest request,
            HttpServletRequest http
    ) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "TENANT_MENU_RULE_UPDATE",
                "TENANT_MENU",
                request,
                http,
                () -> menuAdminService.upsertTenantMenuRule(menuCode, request, session.getTenantId(), sysAdmin)
        );
    }

    /**
     * 清除租户级菜单覆盖。
     *
     * @param menuCode 菜单编码
     * @param tenantId 租户ID
     * @param http     请求
     * @return 清除结果
     * @author 余炘洋
     * @date 2026-04-12
     */
    @DeleteMapping("/tenant/{menuCode}")
    @PreAuthorize("hasAuthority('ADMIN:MENU:WRITE')")
    public ApiResponse<String> clearTenantMenuRule(
            @PathVariable String menuCode,
            @RequestParam(required = false) String tenantId,
            HttpServletRequest http
    ) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.executeVoid(
                "TENANT_MENU_RULE_CLEAR",
                "TENANT_MENU",
                tenantId,
                http,
                () -> menuAdminService.clearTenantMenuRule(menuCode, tenantId, session.getTenantId(), sysAdmin),
                "租户菜单覆盖已清除"
        );
    }

    /**
     * 预览指定组织上下文命中的菜单树。
     *
     * @param request 预览请求
     * @return 菜单树
     * @author 余炘洋
     * @date 2026-04-12
     */
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('ADMIN:MENU:READ')")
    public ApiResponse<List<MenuView>> previewMenus(@RequestBody(required = false) MenuPreviewRequest request) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return ApiResponse.success(menuAdminService.previewMenus(request, session.getTenantId(), sysAdmin));
    }

    /**
     * 预览菜单规则变更影响。
     *
     * @param request 影响分析请求
     * @return 影响分析结果
     * @author 余炘洋
     * @date 2026-04-13
     */
    @PostMapping("/impact")
    @PreAuthorize("hasAuthority('ADMIN:MENU:READ')")
    public ApiResponse<MenuImpactPreviewView> previewMenuImpact(@RequestBody MenuImpactPreviewRequest request,
                                                               HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        String action = StringUtils.hasText(request == null ? null : request.getMenuCode())
                ? "TENANT_MENU_RULE_IMPACT_PREVIEW"
                : "MENU_RULE_IMPACT_PREVIEW";
        String resource = StringUtils.hasText(request == null ? null : request.getMenuCode()) ? "TENANT_MENU" : "MENU";
        return auditOperationExecutor.execute(
                action,
                resource,
                request,
                http,
                () -> menuAdminService.previewMenuImpact(request, session.getTenantId(), sysAdmin),
                result -> buildImpactAuditDetail(request, result)
        );
    }

    /**
     * 查询菜单发布单列表。
     *
     * @param tenantId 租户ID
     * @param status   发布状态
     * @return 发布单列表
     * @author 余炘洋
     * @date 2026-04-13
     */
    @GetMapping("/releases")
    @PreAuthorize("hasAnyAuthority('ADMIN:MENU:RELEASE:READ','ADMIN:MENU:READ')")
    public ApiResponse<List<MenuReleaseView>> listReleases(@RequestParam(required = false) String tenantId,
                                                           @RequestParam(required = false) String status) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return ApiResponse.success(menuReleaseService.listRecords(tenantId, status, session.getTenantId(), sysAdmin));
    }

    /**
     * 创建菜单发布单。
     *
     * @param request 请求体
     * @param http    请求
     * @return 发布单
     * @author 余炘洋
     * @date 2026-04-13
     */
    @PostMapping("/releases")
    @PreAuthorize("hasAnyAuthority('ADMIN:MENU:RELEASE:CREATE','ADMIN:MENU:WRITE')")
    public ApiResponse<MenuReleaseView> createRelease(@RequestBody MenuReleaseCreateRequest request,
                                                      HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "MENU_RELEASE_CREATE",
                "MENU_RELEASE",
                request,
                http,
                () -> menuReleaseService.createRecord(request, session.getTenantId(), sysAdmin, session)
        );
    }

    /**
     * 审核菜单发布单。
     *
     * @param id      发布单ID
     * @param request 审核请求
     * @param http    请求
     * @return 发布单
     * @author 余炘洋
     * @date 2026-04-13
     */
    @PutMapping("/releases/{id}/review")
    @PreAuthorize("hasAnyAuthority('ADMIN:MENU:RELEASE:REVIEW','ADMIN:MENU:WRITE')")
    public ApiResponse<MenuReleaseView> reviewRelease(@PathVariable Long id,
                                                      @RequestBody MenuReleaseReviewRequest request,
                                                      HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "MENU_RELEASE_REVIEW",
                "MENU_RELEASE",
                request,
                http,
                () -> menuReleaseService.reviewRecord(id, request, session.getTenantId(), sysAdmin, session)
        );
    }

    /**
     * 应用菜单发布单。
     *
     * @param id   发布单ID
     * @param http 请求
     * @return 发布单
     * @author 余炘洋
     * @date 2026-04-13
     */
    @PutMapping("/releases/{id}/apply")
    @PreAuthorize("hasAnyAuthority('ADMIN:MENU:RELEASE:APPLY','ADMIN:MENU:WRITE')")
    public ApiResponse<MenuReleaseView> applyRelease(@PathVariable Long id, HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "MENU_RELEASE_APPLY",
                "MENU_RELEASE",
                "id=" + id,
                http,
                () -> menuReleaseService.applyRecord(id, session.getTenantId(), sysAdmin, session)
        );
    }

    private Map<String, Object> buildImpactAuditDetail(MenuImpactPreviewRequest request, MenuImpactPreviewView result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("request", request);
        if (result == null) {
            return detail;
        }
        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("tenantId", result.getTenantId());
        outcome.put("menuCode", result.getMenuCode());
        outcome.put("menuName", result.getMenuName());
        outcome.put("tenantOverrideMode", result.isTenantOverrideMode());
        outcome.put("baselineMenuCount", result.getBaselineMenuCount());
        outcome.put("targetMenuCount", result.getTargetMenuCount());
        outcome.put("addedMenuCount", result.getAddedMenuCount());
        outcome.put("removedMenuCount", result.getRemovedMenuCount());
        outcome.put("baselineMatched", result.isBaselineMatched());
        outcome.put("targetMatched", result.isTargetMatched());
        outcome.put("changedDimensions", result.getChangedDimensions());
        outcome.put("impactSummary", result.getImpactSummary());
        detail.put("result", outcome);
        return detail;
    }

    /**
     * 获取当前会话。
     *
     * @return 会话信息
     * @author 余炘洋
     * @date 2026-04-12
     */
    private RbacUserSession requireSession() {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        if (session == null) {
            throw new BadCredentialsException("未登录或登录已过期");
        }
        return session;
    }
}
