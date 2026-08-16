package com.lingsql.web;

import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.SecureTenantResolver;
import com.lingsql.tenant.readiness.TenantFirstQuestionReadiness;
import com.lingsql.tenant.readiness.model.TenantFirstQuestionReadinessView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户首问就绪只读管理入口。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
@RestController
@RequestMapping("/api/v1/admin/tenant-readiness")
@RequiredArgsConstructor
public class TenantReadinessAdminController {

    private final TenantFirstQuestionReadiness readiness;

    /**
     * 查询指定租户的首问就绪事实。
     *
     * @param tenantId 租户标识
     * @return 首问就绪视图
     */
    @GetMapping("/{tenantId}/first-question")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:READ')")
    public ApiResponse<TenantFirstQuestionReadinessView> firstQuestion(@PathVariable String tenantId) {
        if (!SecureTenantResolver.isSysAdmin(RbacSecurityUtils.currentSession())) {
            throw new AccessDeniedException("无权限访问");
        }
        return ApiResponse.success(readiness.evaluate(tenantId));
    }
}
