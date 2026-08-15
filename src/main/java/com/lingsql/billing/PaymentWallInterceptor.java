package com.lingsql.billing;

import tools.jackson.databind.ObjectMapper;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 付费墙拦截器。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentWallInterceptor implements HandlerInterceptor {

    private final TenantBillingService billingService;
    private final ObjectMapper objectMapper;

    /**
     * 拦截请求，校验配额。
     *
     * @param request  请求
     * @param response 响应
     * @param handler  处理器
     * @return 是否放行
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String tenantId = TenantContext.getTenantId();
        if (!StringUtils.hasText(tenantId)) {
            return true;
        }
        BillingCheckResult result = billingService.checkAccess(tenantId);
        if (result.allowed()) {
            return true;
        }
        log.warn("付费墙拦截: tenantId={}, path={}", tenantId, request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> body = ApiResponse.forbidden(result.message() == null ? "请升级套餐" : result.message());
        response.getWriter().write(objectMapper.writeValueAsString(body));
        return false;
    }
}
