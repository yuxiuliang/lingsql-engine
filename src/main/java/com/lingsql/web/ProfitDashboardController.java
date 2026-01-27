package com.lingsql.web;

import com.lingsql.common.result.ApiResponse;
import com.lingsql.dashboard.model.ProfitDashboardView;
import com.lingsql.dashboard.service.ProfitDashboardService;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.security.SecureTenantResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 老板仪表盘接口。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class ProfitDashboardController {

    private final ProfitDashboardService dashboardService;

    /**
     * 查询老板仪表盘数据。
     *
     * @param days 统计天数
     * @return 仪表盘数据
     * @author 余炘洋
     * @date 2026-01-27
     */
    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('ADMIN:SQLLOG:READ')")
    public ApiResponse<ProfitDashboardView> overview(@RequestParam(defaultValue = "30") int days) {
        RbacUserSession session = requireSession();
        if (!SecureTenantResolver.isSysAdmin(session)) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(dashboardService.buildDashboard(days));
    }

    /**
     * 获取当前会话。
     *
     * @return 会话信息
     * @author 余炘洋
     * @date 2026-01-27
     */
    private RbacUserSession requireSession() {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        if (session == null) {
            throw new BadCredentialsException("未登录或登录已过期");
        }
        return session;
    }
}
