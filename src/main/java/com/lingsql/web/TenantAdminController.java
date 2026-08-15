package com.lingsql.web;

import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.tenant.model.TenantCreateRequest;
import com.lingsql.tenant.model.TenantLifecycleExtendRequest;
import com.lingsql.tenant.model.TenantLifecycleWorkbenchView;
import com.lingsql.tenant.model.TenantNotificationChannelFailureStatsView;
import com.lingsql.tenant.model.TenantLifecycleSummaryView;
import com.lingsql.tenant.model.TenantNotificationChannelHealthView;
import com.lingsql.tenant.model.TenantNotificationChannelRecentTestView;
import com.lingsql.tenant.model.TenantNotificationChannelTestRequest;
import com.lingsql.tenant.model.TenantNotificationChannelTestView;
import com.lingsql.tenant.model.TenantUpdateRequest;
import com.lingsql.tenant.model.TenantView;
import com.lingsql.tenant.service.TenantAdminService;
import com.lingsql.tenant.service.TenantNotificationChannelHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 租户管理接口。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
public class TenantAdminController {

    private final TenantAdminService tenantAdminService;
    private final TenantNotificationChannelHistoryService tenantNotificationChannelHistoryService;
    private final AuditOperationExecutor auditOperationExecutor;

    /**
     * 查询租户列表。
     *
     * @return 租户列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN:TENANT:READ')")
    public ApiResponse<List<TenantView>> listTenants() {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(tenantAdminService.listTenants());
    }

    /**
     * 查询租户生命周期汇总。
     *
     * @return 生命周期汇总
     * @author 余炘洋
     * @date 2026-02-27
     */
    @GetMapping("/lifecycle/summary")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:READ')")
    public ApiResponse<TenantLifecycleSummaryView> lifecycleSummary() {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(tenantAdminService.lifecycleSummary());
    }

    /**
     * 查询租户生命周期运营工作台。
     *
     * @param limit 返回条数上限
     * @return 工作台结果
     * @author 余炘洋
     * @date 2026-02-27
     */
    @GetMapping("/lifecycle/workbench")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:READ')")
    public ApiResponse<TenantLifecycleWorkbenchView> lifecycleWorkbench(@RequestParam(required = false) Integer limit) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(tenantAdminService.lifecycleWorkbench(limit));
    }

    /**
     * 查询租户默认通知通道健康情况。
     *
     * @return 健康视图
     * @author 余炘洋
     * @date 2026-04-15
     */
    @GetMapping("/notification-channels/health")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:READ')")
    public ApiResponse<TenantNotificationChannelHealthView> notificationChannelHealth() {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(tenantAdminService.notificationChannelHealth());
    }

    /**
     * 查询最近的通知通道测试记录。
     *
     * @param tenantId 租户ID
     * @param limit    返回条数
     * @return 最近测试记录
     * @author 余炘洋
     * @date 2026-04-15
     */
    @GetMapping("/notification-channels/tests/recent")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:READ')")
    public ApiResponse<List<TenantNotificationChannelRecentTestView>> recentNotificationChannelTests(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String operatorKeyword,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) Integer limit
    ) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(tenantNotificationChannelHistoryService.listRecentTests(
                tenantId,
                success,
                channel,
                operatorKeyword,
                startTime,
                endTime,
                limit
        ));
    }

    /**
     * 查询通知通道失败问题分布。
     *
     * @param tenantId 租户ID
     * @param limit    统计样本数
     * @return 失败统计
     * @author 余炘洋
     * @date 2026-04-15
     */
    @GetMapping("/notification-channels/tests/stats")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:READ')")
    public ApiResponse<TenantNotificationChannelFailureStatsView> notificationChannelFailureStats(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) Integer limit
    ) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(tenantNotificationChannelHistoryService.summarizeFailures(tenantId, startTime, endTime, limit));
    }

    /**
     * 续期租户生命周期。
     *
     * @param id      租户ID
     * @param request 续期请求
     * @param http    请求
     * @return 租户视图
     * @author 余炘洋
     * @date 2026-02-27
     */
    @PostMapping("/{id}/lifecycle/extend")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:WRITE')")
    public ApiResponse<TenantView> extendTenantLifecycle(@PathVariable Long id,
                                                         @RequestBody(required = false) TenantLifecycleExtendRequest request,
                                                         HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "TENANT_LIFECYCLE_EXTEND",
                "TENANT",
                request,
                http,
                () -> tenantAdminService.extendTenantLifecycle(id, request)
        );
    }

    /**
     * 恢复租户服务。
     *
     * @param id   租户ID
     * @param http 请求
     * @return 租户视图
     * @author 余炘洋
     * @date 2026-02-27
     */
    @PostMapping("/{id}/lifecycle/reactivate")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:WRITE')")
    public ApiResponse<TenantView> reactivateTenant(@PathVariable Long id, HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "TENANT_LIFECYCLE_REACTIVATE",
                "TENANT",
                "id=" + id,
                http,
                () -> tenantAdminService.reactivateTenant(id)
        );
    }

    /**
     * 创建租户。
     *
     * @param request 创建请求
     * @param http    请求
     * @return 创建结果
     * @author 余炘洋
     * @date 2026-01-26
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN:TENANT:WRITE')")
    public ApiResponse<TenantView> createTenant(@RequestBody TenantCreateRequest request, HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "TENANT_CREATE",
                "TENANT",
                request,
                http,
                () -> tenantAdminService.createTenant(request)
        );
    }

    /**
     * 更新租户。
     *
     * @param id      租户ID
     * @param request 更新请求
     * @param http    请求
     * @return 更新结果
     * @author 余炘洋
     * @date 2026-01-26
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:WRITE')")
    public ApiResponse<TenantView> updateTenant(@PathVariable Long id, @RequestBody TenantUpdateRequest request, HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "TENANT_UPDATE",
                "TENANT",
                request,
                http,
                () -> tenantAdminService.updateTenant(id, request)
        );
    }

    /**
     * 测试租户默认通知通道。
     *
     * @param request 测试请求
     * @param http    请求
     * @return 测试结果
     * @author 余炘洋
     * @date 2026-04-15
     */
    @PostMapping("/notification-channels/test")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:WRITE')")
    public ApiResponse<TenantNotificationChannelTestView> testNotificationChannels(
            @RequestBody TenantNotificationChannelTestRequest request,
            HttpServletRequest http
    ) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "TENANT_NOTIFICATION_CHANNEL_TEST",
                "TENANT",
                request,
                http,
                () -> tenantAdminService.testNotificationChannels(request),
                result -> result
        );
    }

    /**
     * 删除租户。
     *
     * @param id 租户ID
     * @param http 请求
     * @return 删除结果
     * @author 余炘洋
     * @date 2026-01-26
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:DELETE')")
    public ApiResponse<String> deleteTenant(@PathVariable Long id, HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.executeVoid(
                "TENANT_DELETE",
                "TENANT",
                "id=" + id,
                http,
                () -> tenantAdminService.deleteTenant(id),
                "租户已删除"
        );
    }


    /**
     * 判断是否系统管理员。
     *
     * @return 是否系统管理员
     * @author 余炘洋
     * @date 2026-01-26
     */
    private boolean isSysAdmin() {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        return com.lingsql.rbac.security.SecureTenantResolver.isSysAdmin(session);
    }
}
