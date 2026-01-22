package com.lingsql.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 租户拦截器，负责从请求头解析租户 ID 并写入 ThreadLocal。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Component
public class TenantInterceptor implements HandlerInterceptor {

    /**
     * 请求进入时写入租户上下文。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  处理器
     * @return 是否继续执行
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenantId = request.getHeader("X-Tenant-Id");
        if (StringUtils.hasText(tenantId)) {
            TenantContext.setTenantId(tenantId);
        }
        return true;
    }

    /**
     * 请求结束后清理上下文，避免线程复用导致数据串透。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  处理器
     * @param ex       异常
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }
}
