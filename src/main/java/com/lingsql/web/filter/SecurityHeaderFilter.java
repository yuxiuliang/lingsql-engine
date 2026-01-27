package com.lingsql.web.filter;

import com.lingsql.config.LingSqlProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * 安全响应头过滤器。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class SecurityHeaderFilter extends OncePerRequestFilter {

    private static final String HEADER_CSP = "Content-Security-Policy";
    private static final String HEADER_HSTS = "Strict-Transport-Security";
    private static final String HEADER_X_CONTENT_TYPE = "X-Content-Type-Options";
    private static final String HEADER_X_FRAME = "X-Frame-Options";
    private static final String HEADER_REFERRER = "Referrer-Policy";

    private final LingSqlProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        LingSqlProperties.Security security = properties.getSecurity();
        if (security != null && security.isSecurityHeadersEnabled()) {
            applyHeaders(request, response, security);
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !StringUtils.hasText(uri) || !uri.startsWith("/api/");
    }

    private void applyHeaders(HttpServletRequest request, HttpServletResponse response, LingSqlProperties.Security security) {
        response.setHeader(HEADER_X_CONTENT_TYPE, "nosniff");
        response.setHeader(HEADER_X_FRAME, "DENY");
        response.setHeader(HEADER_REFERRER, "no-referrer");

        String csp = security.getContentSecurityPolicy();
        if (StringUtils.hasText(csp)) {
            response.setHeader(HEADER_CSP, csp.trim());
        }
        boolean secureRequest = request.isSecure();
        if (security.isHstsAlways() || secureRequest) {
            Duration maxAge = security.getHstsMaxAge();
            long seconds = maxAge == null ? Duration.ofDays(365).getSeconds() : Math.max(0L, maxAge.getSeconds());
            StringBuilder hsts = new StringBuilder("max-age=").append(seconds);
            if (security.isHstsIncludeSubDomains()) {
                hsts.append("; includeSubDomains");
            }
            if (security.isHstsPreload()) {
                hsts.append("; preload");
            }
            response.setHeader(HEADER_HSTS, hsts.toString());
        }
    }
}
