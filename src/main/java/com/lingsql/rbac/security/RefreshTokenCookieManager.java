package com.lingsql.rbac.security;

import com.lingsql.config.LingSqlProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Refresh Token Cookie 管理器。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieManager {

    private final LingSqlProperties properties;

    /**
     * 判断是否启用 Refresh Token Cookie 承载。
     *
     * @return 是否启用
     * @author 余炘洋
     * @date 2026-04-07
     */
    public boolean isRefreshTokenCookieEnabled() {
        return isEnabled();
    }

    /**
     * 写入 Refresh Token Cookie。
     *
     * @param request      HTTP 请求
     * @param response     HTTP 响应
     * @param refreshToken Refresh Token
     * @param maxAgeSeconds 过期秒数
     * @author 余炘洋
     * @date 2026-02-27
     */
    public void writeRefreshTokenCookie(
            HttpServletRequest request,
            HttpServletResponse response,
            String refreshToken,
            long maxAgeSeconds
    ) {
        if (!isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(refreshToken) || maxAgeSeconds <= 0) {
            clearRefreshTokenCookie(request, response);
            return;
        }
        String cookieValue = refreshToken.trim();
        ResponseCookie cookie = ResponseCookie.from(resolveCookieName(), cookieValue)
                .httpOnly(true)
                .secure(resolveSecure(request))
                .path(resolveCookiePath())
                .sameSite(resolveSameSite())
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * 清理 Refresh Token Cookie。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @author 余炘洋
     * @date 2026-02-27
     */
    public void clearRefreshTokenCookie(HttpServletRequest request, HttpServletResponse response) {
        if (!isEnabled()) {
            return;
        }
        ResponseCookie cookie = ResponseCookie.from(resolveCookieName(), "")
                .httpOnly(true)
                .secure(resolveSecure(request))
                .path(resolveCookiePath())
                .sameSite(resolveSameSite())
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * 从 Cookie 中解析 Refresh Token。
     *
     * @param request HTTP 请求
     * @return Refresh Token
     * @author 余炘洋
     * @date 2026-02-27
     */
    public String resolveRefreshToken(HttpServletRequest request) {
        if (!isEnabled() || request == null) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        String cookieName = resolveCookieName();
        for (Cookie cookie : cookies) {
            if (cookie == null || !cookieName.equals(cookie.getName())) {
                continue;
            }
            if (StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue().trim();
            }
            return null;
        }
        return null;
    }

    private boolean isEnabled() {
        LingSqlProperties.Auth auth = properties.getAuth();
        return auth == null || auth.isRefreshCookieEnabled();
    }

    private String resolveCookieName() {
        LingSqlProperties.Auth auth = properties.getAuth();
        if (auth == null || !StringUtils.hasText(auth.getRefreshCookieName())) {
            return "lingsql_refresh_token";
        }
        return auth.getRefreshCookieName().trim();
    }

    private String resolveCookiePath() {
        LingSqlProperties.Auth auth = properties.getAuth();
        if (auth == null || !StringUtils.hasText(auth.getRefreshCookiePath())) {
            return "/api/v1/auth";
        }
        String path = auth.getRefreshCookiePath().trim();
        return path.startsWith("/") ? path : "/" + path;
    }

    private String resolveSameSite() {
        LingSqlProperties.Auth auth = properties.getAuth();
        if (auth == null || !StringUtils.hasText(auth.getRefreshCookieSameSite())) {
            return "Lax";
        }
        return auth.getRefreshCookieSameSite().trim();
    }

    private boolean resolveSecure(HttpServletRequest request) {
        LingSqlProperties.Auth auth = properties.getAuth();
        if (auth != null && auth.isRefreshCookieSecure()) {
            return true;
        }
        return request != null && request.isSecure();
    }
}
