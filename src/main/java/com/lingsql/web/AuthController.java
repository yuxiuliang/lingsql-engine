package com.lingsql.web;

import com.lingsql.common.result.ApiResponse;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.rbac.model.LoginRequest;
import com.lingsql.rbac.model.LoginResponse;
import com.lingsql.rbac.model.MenuView;
import com.lingsql.rbac.model.UserProfileView;
import com.lingsql.rbac.security.RbacSecurityConstants;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.service.AuthService;
import com.lingsql.rbac.service.MenuService;
import com.lingsql.rbac.service.RefreshTokenService;
import com.lingsql.rbac.service.TokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证与菜单接口。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_TOKEN_HEADER = "X-Refresh-Token";
    private static final String LOGIN_ERROR_MESSAGE = "账号、密码或租户信息错误";

    private final AuthService authService;
    private final MenuService menuService;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final LingSqlProperties properties;

    /**
     * 登录接口。
     *
     * @param request 登录请求
     * @return 登录响应
     * @author 余炘洋
     * @date 2026-01-23
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        try {
            LoginResponse response = authService.login(request);
            writeAuthCookie(httpRequest, httpResponse, response.getToken(), response.getExpiresIn());
            writeRefreshCookie(httpRequest, httpResponse, response.getRefreshToken(), response.getRefreshExpiresIn());
            return ApiResponse.success(response);
        } catch (Exception ex) {
            return ApiResponse.unauthorized(LOGIN_ERROR_MESSAGE);
        }
    }

    /**
     * 获取当前用户菜单树。
     *
     * @return 菜单树
     * @author 余炘洋
     * @date 2026-01-23
     */
    @GetMapping("/menus")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<MenuView>> menus() {
        RbacUserSession session = requireSession();
        return ApiResponse.success(menuService.listMenus(session));
    }

    /**
     * 获取当前用户信息。
     *
     * @return 用户信息
     * @author 余炘洋
     * @date 2026-01-23
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserProfileView> profile() {
        RbacUserSession session = requireSession();
        UserProfileView view = new UserProfileView();
        if (session != null) {
            view.setUserId(session.getUserId());
            view.setTenantId(session.getTenantId());
            view.setUsername(session.getUsername());
            view.setNickname(session.getNickname());
            view.setExperience(session.isExperience());
            view.setRoles(session.getRoles());
            view.setPermissions(session.getPermissions());
            view.setDataScope(session.getDataScope());
        }
        return ApiResponse.success(view);
    }

    /**
     * 退出登录。
     *
     * @param authHeader Authorization 头
     * @return 退出结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<String> logout(
            @RequestHeader(value = RbacSecurityConstants.AUTH_HEADER, required = false) String authHeader,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String token = resolveToken(authHeader, request);
        if (StringUtils.hasText(token)) {
            tokenService.revokeToken(token);
        }
        String refreshToken = resolveRefreshToken(request);
        if (StringUtils.hasText(refreshToken)) {
            RefreshTokenService.RefreshTokenPayload payload = refreshTokenService.verify(refreshToken);
            refreshTokenService.revoke(refreshToken, payload == null ? null : payload.userId());
        }
        clearRefreshCookie(request, response);
        clearAuthCookie(request, response);
        return ApiResponse.success("已退出登录");
    }

    /**
     * 刷新 Token。
     *
     * @param request  请求
     * @param response 响应
     * @return 登录响应
     * @author 余炘洋
     * @date 2026-01-27
     */
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = resolveRefreshToken(request);
        if (!StringUtils.hasText(refreshToken)) {
            return ApiResponse.unauthorized("Refresh Token 无效");
        }
        try {
            LoginResponse refreshed = authService.refresh(refreshToken);
            writeAuthCookie(request, response, refreshed.getToken(), refreshed.getExpiresIn());
            writeRefreshCookie(request, response, refreshed.getRefreshToken(), refreshed.getRefreshExpiresIn());
            return ApiResponse.success(refreshed);
        } catch (Exception ex) {
            return ApiResponse.unauthorized("Refresh Token 无效");
        }
    }

    /**
     * 解析 Token。
     *
     * @param authHeader Authorization 头
     * @return Token
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String resolveToken(String authHeader, HttpServletRequest request) {
        if (StringUtils.hasText(authHeader)) {
            if (authHeader.startsWith(RbacSecurityConstants.BEARER_PREFIX)) {
                return authHeader.substring(RbacSecurityConstants.BEARER_PREFIX.length()).trim();
            }
            return authHeader.trim();
        }
        return resolveTokenFromCookie(request);
    }

    private String resolveRefreshToken(HttpServletRequest request) {
        String header = request.getHeader(REFRESH_TOKEN_HEADER);
        if (StringUtils.hasText(header)) {
            return header.trim();
        }
        return resolveRefreshTokenFromCookie(request);
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

    private String resolveRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        String cookieName = resolveRefreshCookieName();
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void writeAuthCookie(HttpServletRequest request, HttpServletResponse response, String token, long expiresIn) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        LingSqlProperties.Auth auth = properties.getAuth();
        String sameSite = resolveSameSite(auth == null ? null : auth.getCookieSameSite());
        boolean secure = resolveSecure(request, sameSite, auth == null ? null : auth.getCookieSecure());
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(resolveCookieName(), token)
                .httpOnly(true)
                .secure(secure)
                .path(resolveCookiePath())
                .sameSite(sameSite)
                .maxAge(expiresIn);
        String domain = auth == null ? null : auth.getCookieDomain();
        if (StringUtils.hasText(domain)) {
            builder.domain(domain.trim());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    private void clearAuthCookie(HttpServletRequest request, HttpServletResponse response) {
        LingSqlProperties.Auth auth = properties.getAuth();
        String sameSite = resolveSameSite(auth == null ? null : auth.getCookieSameSite());
        boolean secure = resolveSecure(request, sameSite, auth == null ? null : auth.getCookieSecure());
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(resolveCookieName(), "")
                .httpOnly(true)
                .secure(secure)
                .path(resolveCookiePath())
                .sameSite(sameSite)
                .maxAge(0);
        String domain = auth == null ? null : auth.getCookieDomain();
        if (StringUtils.hasText(domain)) {
            builder.domain(domain.trim());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    private void writeRefreshCookie(HttpServletRequest request, HttpServletResponse response, String refreshToken, long expiresIn) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        LingSqlProperties.Auth auth = properties.getAuth();
        String sameSite = resolveSameSite(auth == null ? null : auth.getCookieSameSite());
        boolean secure = resolveSecure(request, sameSite, auth == null ? null : auth.getCookieSecure());
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(resolveRefreshCookieName(), refreshToken)
                .httpOnly(true)
                .secure(secure)
                .path(resolveCookiePath())
                .sameSite(sameSite)
                .maxAge(expiresIn);
        String domain = auth == null ? null : auth.getCookieDomain();
        if (StringUtils.hasText(domain)) {
            builder.domain(domain.trim());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    private void clearRefreshCookie(HttpServletRequest request, HttpServletResponse response) {
        LingSqlProperties.Auth auth = properties.getAuth();
        String sameSite = resolveSameSite(auth == null ? null : auth.getCookieSameSite());
        boolean secure = resolveSecure(request, sameSite, auth == null ? null : auth.getCookieSecure());
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(resolveRefreshCookieName(), "")
                .httpOnly(true)
                .secure(secure)
                .path(resolveCookiePath())
                .sameSite(sameSite)
                .maxAge(0);
        String domain = auth == null ? null : auth.getCookieDomain();
        if (StringUtils.hasText(domain)) {
            builder.domain(domain.trim());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    private String resolveCookieName() {
        LingSqlProperties.Auth auth = properties.getAuth();
        if (auth == null || !StringUtils.hasText(auth.getCookieName())) {
            return "LINGSQL_TOKEN";
        }
        return auth.getCookieName().trim();
    }

    private String resolveRefreshCookieName() {
        LingSqlProperties.Auth auth = properties.getAuth();
        if (auth == null || !StringUtils.hasText(auth.getRefreshCookieName())) {
            return "LINGSQL_REFRESH";
        }
        return auth.getRefreshCookieName().trim();
    }

    private String resolveCookiePath() {
        LingSqlProperties.Auth auth = properties.getAuth();
        if (auth == null || !StringUtils.hasText(auth.getCookiePath())) {
            return "/";
        }
        return auth.getCookiePath().trim();
    }

    private String resolveSameSite(String sameSite) {
        if (!StringUtils.hasText(sameSite)) {
            return "Lax";
        }
        String normalized = sameSite.trim();
        if ("Strict".equalsIgnoreCase(normalized)) {
            return "Strict";
        }
        if ("None".equalsIgnoreCase(normalized)) {
            return "None";
        }
        return "Lax";
    }

    private boolean resolveSecure(HttpServletRequest request, String sameSite, Boolean cookieSecure) {
        boolean secure = cookieSecure != null ? cookieSecure : request.isSecure();
        if ("None".equalsIgnoreCase(sameSite)) {
            return true;
        }
        return secure;
    }

    /**
     * 获取并校验登录会话。
     *
     * @return 登录会话
     * @author 余炘洋
     * @date 2026-01-23
     */
    private RbacUserSession requireSession() {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        if (session == null) {
            throw new BadCredentialsException("未登录或登录已过期");
        }
        return session;
    }
}
