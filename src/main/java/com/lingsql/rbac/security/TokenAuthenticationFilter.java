package com.lingsql.rbac.security;

import com.lingsql.context.SessionInfo;
import com.lingsql.context.TenantContext;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.rbac.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Token 鉴权过滤器。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Component
@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final LingSqlProperties properties;
    private static final String MDC_TENANT_ID = "tenantId";
    private static final String MDC_USER_ID = "userId";

    /**
     * 过滤请求并注入认证信息。
     *
     * @param request     请求
     * @param response    响应
     * @param filterChain 过滤链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = resolveToken(request);
            if (StringUtils.hasText(token)) {
                RbacUserSession session = tokenService.getSession(token);
                if (session != null) {
                    if (session.getUserId() == null) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        return;
                    }
                    List<SimpleGrantedAuthority> authorities = session.getPermissions().stream()
                            .filter(StringUtils::hasText)
                            .map(SimpleGrantedAuthority::new)
                            .toList();
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(session, token, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    TenantContext.setSessionInfo(new SessionInfo(session.getTenantId(), session.getUserId()));
                    if (StringUtils.hasText(session.getTenantId())) {
                        MDC.put(MDC_TENANT_ID, session.getTenantId());
                    }
                    if (session.getUserId() != null) {
                        MDC.put(MDC_USER_ID, String.valueOf(session.getUserId()));
                    }
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 解析 Token。
     *
     * @param request 请求
     * @return Token
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader(RbacSecurityConstants.AUTH_HEADER);
        if (StringUtils.hasText(auth)) {
            if (auth.startsWith(RbacSecurityConstants.BEARER_PREFIX)) {
                return auth.substring(RbacSecurityConstants.BEARER_PREFIX.length()).trim();
            }
            return auth.trim();
        }
        return resolveTokenFromCookie(request);
    }

    private String resolveTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        String cookieName = resolveCookieName();
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String resolveCookieName() {
        LingSqlProperties.Auth auth = properties.getAuth();
        if (auth == null || !StringUtils.hasText(auth.getCookieName())) {
            return "LINGSQL_TOKEN";
        }
        return auth.getCookieName().trim();
    }
}
