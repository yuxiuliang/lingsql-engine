package com.lingsql.web;

import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.model.LoginRequest;
import com.lingsql.rbac.model.LoginResponse;
import com.lingsql.rbac.security.RbacSecurityConstants;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.security.RefreshTokenCookieManager;
import com.lingsql.rbac.service.AuthService;
import com.lingsql.rbac.service.MenuService;
import com.lingsql.rbac.service.RefreshTokenService;
import com.lingsql.rbac.service.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerCookieTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginShouldWriteRefreshTokenCookie() {
        AuthService authService = Mockito.mock(AuthService.class);
        LoginResponse loginResponse = buildLoginResponse("access_token", "refresh_token_1", 7200L);
        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);
        RefreshTokenCookieManager cookieManager = Mockito.mock(RefreshTokenCookieManager.class);
        AuthController controller = new AuthController(
                authService,
                Mockito.mock(MenuService.class),
                Mockito.mock(TokenService.class),
                Mockito.mock(RefreshTokenService.class),
                cookieManager
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(cookieManager.isRefreshTokenCookieEnabled()).thenReturn(true);

        ApiResponse<LoginResponse> result = controller.login(new LoginRequest(), request, response);

        assertEquals(200, result.getCode());
        assertNull(result.getData().getRefreshToken());
        verify(cookieManager).writeRefreshTokenCookie(request, response, "refresh_token_1", 7200L);
    }

    @Test
    void refreshShouldUseCookieWhenHeaderMissing() {
        AuthService authService = Mockito.mock(AuthService.class);
        LoginResponse loginResponse = buildLoginResponse("access_token_2", "refresh_token_2", 3600L);
        when(authService.refresh("cookie_refresh_token")).thenReturn(loginResponse);
        RefreshTokenService refreshTokenService = Mockito.mock(RefreshTokenService.class);
        RefreshTokenCookieManager cookieManager = Mockito.mock(RefreshTokenCookieManager.class);
        AuthController controller = new AuthController(
                authService,
                Mockito.mock(MenuService.class),
                Mockito.mock(TokenService.class),
                refreshTokenService,
                cookieManager
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(cookieManager.resolveRefreshToken(request)).thenReturn("cookie_refresh_token");
        when(cookieManager.isRefreshTokenCookieEnabled()).thenReturn(true);

        ApiResponse<LoginResponse> result = controller.refresh(request, response);

        assertEquals(200, result.getCode());
        assertNull(result.getData().getRefreshToken());
        verify(cookieManager).writeRefreshTokenCookie(request, response, "refresh_token_2", 3600L);
    }

    @Test
    void loginShouldKeepRefreshTokenInBodyWhenCookieDisabled() {
        AuthService authService = Mockito.mock(AuthService.class);
        LoginResponse loginResponse = buildLoginResponse("access_token", "refresh_token_1", 7200L);
        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);
        RefreshTokenCookieManager cookieManager = Mockito.mock(RefreshTokenCookieManager.class);
        when(cookieManager.isRefreshTokenCookieEnabled()).thenReturn(false);
        AuthController controller = new AuthController(
                authService,
                Mockito.mock(MenuService.class),
                Mockito.mock(TokenService.class),
                Mockito.mock(RefreshTokenService.class),
                cookieManager
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiResponse<LoginResponse> result = controller.login(new LoginRequest(), request, response);

        assertEquals(200, result.getCode());
        assertEquals("refresh_token_1", result.getData().getRefreshToken());
    }

    @Test
    void refreshShouldClearCookieWhenRefreshTokenMissing() {
        AuthService authService = Mockito.mock(AuthService.class);
        RefreshTokenCookieManager cookieManager = Mockito.mock(RefreshTokenCookieManager.class);
        AuthController controller = new AuthController(
                authService,
                Mockito.mock(MenuService.class),
                Mockito.mock(TokenService.class),
                Mockito.mock(RefreshTokenService.class),
                cookieManager
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(cookieManager.resolveRefreshToken(request)).thenReturn(null);

        ApiResponse<LoginResponse> result = controller.refresh(request, response);

        assertEquals(401, result.getCode());
        verify(cookieManager).clearRefreshTokenCookie(request, response);
        verify(authService, never()).refresh(any());
    }

    @Test
    void logoutShouldRevokeTokensAndClearCookie() {
        TokenService tokenService = Mockito.mock(TokenService.class);
        RefreshTokenService refreshTokenService = Mockito.mock(RefreshTokenService.class);
        RefreshTokenCookieManager cookieManager = Mockito.mock(RefreshTokenCookieManager.class);
        AuthController controller = new AuthController(
                Mockito.mock(AuthService.class),
                Mockito.mock(MenuService.class),
                tokenService,
                refreshTokenService,
                cookieManager
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Refresh-Token", "refresh_token_x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(refreshTokenService.verify("refresh_token_x"))
                .thenReturn(new RefreshTokenService.RefreshTokenPayload(12L, "TENANT_A", 1L, 1L));

        ApiResponse<String> result = controller.logout(
                RbacSecurityConstants.BEARER_PREFIX + "access_token_x",
                request,
                response
        );

        assertEquals(200, result.getCode());
        verify(tokenService).revokeToken("access_token_x");
        verify(refreshTokenService).revoke("refresh_token_x", 12L);
        verify(cookieManager).clearRefreshTokenCookie(request, response);
    }

    @Test
    void profileShouldExposeRelationScopesFromSession() {
        AuthController controller = new AuthController(
                Mockito.mock(AuthService.class),
                Mockito.mock(MenuService.class),
                Mockito.mock(TokenService.class),
                Mockito.mock(RefreshTokenService.class),
                Mockito.mock(RefreshTokenCookieManager.class)
        );
        RbacUserSession session = new RbacUserSession();
        session.setUserId(7L);
        session.setTenantId("TENANT_A");
        session.setUsername("leader");
        Map<String, List<String>> relationScopes = new LinkedHashMap<>();
        relationScopes.put("SUPERVISE", List.of("CITY", "CITY_DISTRICT"));
        relationScopes.put("COOPERATE", List.of("CAMPUS_B"));
        session.setRelationScopes(relationScopes);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(session, "token", List.of())
        );

        ApiResponse<com.lingsql.rbac.model.UserProfileView> result = controller.profile();

        assertEquals(200, result.getCode());
        assertEquals(List.of("CITY", "CITY_DISTRICT"), result.getData().getRelationScopes().get("SUPERVISE"));
        assertEquals(List.of("CAMPUS_B"), result.getData().getRelationScopes().get("COOPERATE"));
    }

    private LoginResponse buildLoginResponse(String token, String refreshToken, long refreshExpiresIn) {
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setExpiresIn(3600L);
        response.setRefreshToken(refreshToken);
        response.setRefreshExpiresIn(refreshExpiresIn);
        response.setUserId(1L);
        response.setTenantId("TENANT_A");
        response.setUsername("admin");
        response.setRelationScopes(Map.of("OWNERSHIP", List.of("ORG_A")));
        return response;
    }
}
