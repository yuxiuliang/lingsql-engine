package com.lingsql.web;

import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.model.LoginRequest;
import com.lingsql.rbac.model.LoginResponse;
import com.lingsql.rbac.model.MenuView;
import com.lingsql.rbac.model.RequiredPasswordChangeRequest;
import com.lingsql.rbac.model.UserProfileView;
import com.lingsql.rbac.security.RbacSecurityConstants;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.security.RefreshTokenCookieManager;
import com.lingsql.rbac.service.AuthService;
import com.lingsql.rbac.service.MenuService;
import com.lingsql.rbac.service.RefreshTokenService;
import com.lingsql.rbac.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    private final AuthService authService;
    private final MenuService menuService;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookieManager refreshTokenCookieManager;

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
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        LoginResponse response = authService.login(request);
        refreshTokenCookieManager.writeRefreshTokenCookie(
                servletRequest,
                servletResponse,
                response.getRefreshToken(),
                response.getRefreshExpiresIn()
        );
        return ApiResponse.success(sanitizeLoginResponse(response));
    }

    /**
     * 完成首次登录强制改密。
     *
     * @param request 改密请求
     * @return 完整登录响应
     */
    @PutMapping("/password")
    @PreAuthorize("hasAuthority('AUTH:PASSWORD:CHANGE')")
    public ApiResponse<LoginResponse> changeRequiredPassword(
            @Valid @RequestBody RequiredPasswordChangeRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        LoginResponse response = authService.changeRequiredPassword(requireSession(), request);
        refreshTokenCookieManager.writeRefreshTokenCookie(
                servletRequest,
                servletResponse,
                response.getRefreshToken(),
                response.getRefreshExpiresIn()
        );
        return ApiResponse.success(sanitizeLoginResponse(response));
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
            view.setRelationScopes(session.getRelationScopes());
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
        String token = resolveToken(authHeader);
        if (StringUtils.hasText(token)) {
            tokenService.revokeToken(token);
        }
        String refreshToken = resolveRefreshToken(request);
        if (StringUtils.hasText(refreshToken)) {
            RefreshTokenService.RefreshTokenPayload payload = refreshTokenService.verify(refreshToken);
            refreshTokenService.revoke(refreshToken, payload == null ? null : payload.userId());
        }
        refreshTokenCookieManager.clearRefreshTokenCookie(request, response);
        return ApiResponse.success("已退出登录");
    }

    /**
     * 刷新 Token。
     *
     * @param request 请求
     * @return 登录响应
     * @author 余炘洋
     * @date 2026-01-27
     */
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = resolveRefreshToken(request);
        if (!StringUtils.hasText(refreshToken)) {
            refreshTokenCookieManager.clearRefreshTokenCookie(request, response);
            return ApiResponse.unauthorized("Refresh Token 无效");
        }
        try {
            LoginResponse refreshed = authService.refresh(refreshToken);
            refreshTokenCookieManager.writeRefreshTokenCookie(
                    request,
                    response,
                    refreshed.getRefreshToken(),
                    refreshed.getRefreshExpiresIn()
            );
            return ApiResponse.success(sanitizeLoginResponse(refreshed));
        } catch (BadCredentialsException ex) {
            refreshTokenCookieManager.clearRefreshTokenCookie(request, response);
            throw ex;
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
    private String resolveToken(String authHeader) {
        if (StringUtils.hasText(authHeader)) {
            if (authHeader.startsWith(RbacSecurityConstants.BEARER_PREFIX)) {
                return authHeader.substring(RbacSecurityConstants.BEARER_PREFIX.length()).trim();
            }
            return authHeader.trim();
        }
        return null;
    }

    private String resolveRefreshToken(HttpServletRequest request) {
        String header = request.getHeader(REFRESH_TOKEN_HEADER);
        if (StringUtils.hasText(header)) {
            return header.trim();
        }
        return refreshTokenCookieManager.resolveRefreshToken(request);
    }

    /**
     * Cookie 模式下移除响应体中的 Refresh Token，避免再次暴露给前端脚本。
     *
     * @param response 登录响应
     * @return 脱敏后的响应
     * @author 余炘洋
     * @date 2026-04-07
     */
    private LoginResponse sanitizeLoginResponse(LoginResponse response) {
        if (response == null) {
            return null;
        }
        if (refreshTokenCookieManager.isRefreshTokenCookieEnabled()) {
            response.setRefreshToken(null);
        }
        return response;
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
