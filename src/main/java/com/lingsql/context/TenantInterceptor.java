package com.lingsql.context;

import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.security.SecureTenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 租户拦截器，负责从请求头解析租户 ID 并写入 ThreadLocal。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {
    private static final String MDC_TENANT_ID = "tenantId";
    private static final String MDC_USER_ID = "userId";

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
        if ("/api/v1/auth/login".equalsIgnoreCase(request.getRequestURI())
                || "/api/v1/auth/refresh".equalsIgnoreCase(request.getRequestURI())) {
            return true;
        }
        String tenantId = decodeTenantId(request.getHeader("X-Tenant-Id"));
        RbacUserSession session = RbacSecurityUtils.currentSession();
        if (session == null || session.getUserId() == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        if (session != null) {
            if (!SecureTenantResolver.isSysAdmin(session)
                    && StringUtils.hasText(tenantId)
                    && StringUtils.hasText(session.getTenantId())
                    && !session.getTenantId().equalsIgnoreCase(tenantId.trim())) {
                log.warn("越权租户访问拦截: userId={}, username={}, sessionTenantId={}, inputTenantId={}",
                        session.getUserId(), session.getUsername(), session.getTenantId(), tenantId);
                throw new AccessDeniedException("无权限访问");
            }
            if (!StringUtils.hasText(tenantId)) {
                tenantId = session.getTenantId();
            }
        }
        if (StringUtils.hasText(tenantId)) {
            TenantContext.setSessionInfo(new SessionInfo(tenantId, session.getUserId()));
            MDC.put(MDC_TENANT_ID, tenantId);
            if (session.getUserId() != null) {
                MDC.put(MDC_USER_ID, String.valueOf(session.getUserId()));
            }
        }
        return true;
    }

    /**
     * 解码租户请求头，兼容 URL 编码场景。
     *
     * @param tenantId 租户请求头
     * @return 解码后的租户ID
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String decodeTenantId(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return tenantId;
        }
        if (!tenantId.contains("%")) {
            return tenantId;
        }
        try {
            return URLDecoder.decode(tenantId, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            log.warn("租户请求头解码失败: {}", tenantId, ex);
            return tenantId;
        }
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
