package com.lingsql.rbac.security;

import com.lingsql.common.result.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Set;

/**
 * 首次登录改密会话的服务端路径边界。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
@Component
public class PasswordChangeOnlyAuthorizationFilter extends OncePerRequestFilter {

    public static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";
    private static final Set<AllowedRequest> ALLOWED_REQUESTS = Set.of(
            new AllowedRequest(HttpMethod.PUT.name(), "/api/v1/auth/password"),
            new AllowedRequest(HttpMethod.POST.name(), "/api/v1/auth/logout")
    );

    private final ObjectMapper objectMapper;

    public PasswordChangeOnlyAuthorizationFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod()) || !isPasswordChangeOnlySession()) {
            filterChain.doFilter(request, response);
            return;
        }
        AllowedRequest current = new AllowedRequest(request.getMethod(), request.getServletPath());
        if (ALLOWED_REQUESTS.contains(current)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.forbidden(PASSWORD_CHANGE_REQUIRED)
        ));
    }

    private boolean isPasswordChangeOnlySession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof RbacUserSession session
                && session.getPurpose() == SessionPurpose.PASSWORD_CHANGE_ONLY;
    }

    private record AllowedRequest(String method, String path) {
    }
}
