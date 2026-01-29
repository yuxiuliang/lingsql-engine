package com.lingsql.web;

import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.billing.BillingPlanService;
import com.lingsql.billing.model.BillingPlanCreateRequest;
import com.lingsql.billing.model.BillingPlanUpdateRequest;
import com.lingsql.billing.model.BillingPlanView;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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

/**
 * 套餐管理接口。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
@RestController
@RequestMapping("/api/v1/admin/plans")
@RequiredArgsConstructor
public class PlanAdminController {

    private final BillingPlanService billingPlanService;
    private final AuditOperationExecutor auditOperationExecutor;

    /**
     * 查询套餐列表。
     *
     * @param keyword 搜索关键字
     * @param status  状态过滤
     * @return 套餐列表
     * @author 余炘洋
     * @date 2026-01-29
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN:PLAN:READ')")
    public ApiResponse<List<BillingPlanView>> listPlans(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) Integer status) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(billingPlanService.listPlans(keyword, status));
    }

    /**
     * 创建套餐。
     *
     * @param request 创建请求
     * @param http    请求
     * @return 套餐视图
     * @author 余炘洋
     * @date 2026-01-29
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN:PLAN:WRITE')")
    public ApiResponse<BillingPlanView> createPlan(@Valid @RequestBody BillingPlanCreateRequest request, HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "PLAN_CREATE",
                "PLAN",
                request,
                http,
                () -> billingPlanService.createPlan(request)
        );
    }

    /**
     * 更新套餐。
     *
     * @param id      套餐ID
     * @param request 更新请求
     * @param http    请求
     * @return 套餐视图
     * @author 余炘洋
     * @date 2026-01-29
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN:PLAN:WRITE')")
    public ApiResponse<BillingPlanView> updatePlan(@PathVariable Long id,
                                                   @RequestBody BillingPlanUpdateRequest request,
                                                   HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "PLAN_UPDATE",
                "PLAN",
                request,
                http,
                () -> billingPlanService.updatePlan(id, request)
        );
    }

    /**
     * 删除套餐。
     *
     * @param id   套餐ID
     * @param http 请求
     * @return 删除结果
     * @author 余炘洋
     * @date 2026-01-29
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN:PLAN:DELETE')")
    public ApiResponse<String> deletePlan(@PathVariable Long id, HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.executeVoid(
                "PLAN_DELETE",
                "PLAN",
                "id=" + id,
                http,
                () -> billingPlanService.deletePlan(id),
                "套餐已删除"
        );
    }

    private boolean isSysAdmin() {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        return com.lingsql.rbac.security.SecureTenantResolver.isSysAdmin(session);
    }
}
