package com.lingsql.rbac.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.rbac.entity.RbacUser;
import com.lingsql.rbac.model.LoginRequest;
import com.lingsql.rbac.model.LoginResponse;
import com.lingsql.rbac.repository.RbacPermissionRepository;
import com.lingsql.rbac.repository.RbacRoleDataScopeRepository;
import com.lingsql.rbac.repository.RbacRolePermissionRepository;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RbacUserDataScopeRepository;
import com.lingsql.rbac.repository.RbacUserExperiencePolicyRepository;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.rbac.repository.RbacUserRoleRepository;
import com.lingsql.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceSingleSessionTest {

    @Test
    void loginShouldRevokeHistoricalSessionsWhenSingleSessionEnabled() {
        TestContext ctx = new TestContext(true);
        RbacUser user = buildActiveUser();
        when(ctx.userRepository.findByTenantIdAndUsername("TENANT_A", "alice")).thenReturn(Optional.of(user));
        when(ctx.passwordEncoder.matches("pwd", "hashed_pwd")).thenReturn(true);
        when(ctx.userRoleRepository.findRoleIdsByUserId(100L)).thenReturn(List.of());
        when(ctx.userDataScopeRepository.findByUserId(100L)).thenReturn(Optional.empty());
        when(ctx.tokenService.createToken(any())).thenReturn("access_token");
        when(ctx.tokenService.getExpireSeconds(any())).thenReturn(3600L);
        when(ctx.refreshTokenService.issueRefreshToken(any(), any())).thenReturn("refresh_token");
        when(ctx.userRepository.save(any(RbacUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ctx.tenantRepository.findByTenantIdAndIsDeleted(anyString(), anyInt())).thenReturn(Optional.empty());

        LoginResponse response = ctx.authService.login(buildLoginRequest());

        assertEquals("access_token", response.getToken());
        verify(ctx.tokenService).revokeUserTokens(100L);
        verify(ctx.refreshTokenService).revokeByUserId(100L);
    }

    @Test
    void loginShouldNotRevokeHistoricalSessionsWhenSingleSessionDisabled() {
        TestContext ctx = new TestContext(false);
        RbacUser user = buildActiveUser();
        when(ctx.userRepository.findByTenantIdAndUsername("TENANT_A", "alice")).thenReturn(Optional.of(user));
        when(ctx.passwordEncoder.matches("pwd", "hashed_pwd")).thenReturn(true);
        when(ctx.userRoleRepository.findRoleIdsByUserId(100L)).thenReturn(List.of());
        when(ctx.userDataScopeRepository.findByUserId(100L)).thenReturn(Optional.empty());
        when(ctx.tokenService.createToken(any())).thenReturn("access_token");
        when(ctx.tokenService.getExpireSeconds(any())).thenReturn(3600L);
        when(ctx.refreshTokenService.issueRefreshToken(any(), any())).thenReturn("refresh_token");
        when(ctx.userRepository.save(any(RbacUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ctx.tenantRepository.findByTenantIdAndIsDeleted(anyString(), anyInt())).thenReturn(Optional.empty());

        LoginResponse response = ctx.authService.login(buildLoginRequest());

        assertEquals("access_token", response.getToken());
        verify(ctx.tokenService, never()).revokeUserTokens(anyLong());
        verify(ctx.refreshTokenService, never()).revokeByUserId(anyLong());
    }

    @Test
    void refreshShouldRevokeHistoricalAccessTokenWhenSingleSessionEnabled() {
        TestContext ctx = new TestContext(true);
        RbacUser user = buildActiveUser();
        RefreshTokenService.RefreshTokenPayload payload =
                new RefreshTokenService.RefreshTokenPayload(100L, "TENANT_A", System.currentTimeMillis());
        RefreshTokenService.RefreshTokenResult result =
                new RefreshTokenService.RefreshTokenResult(payload, "refresh_token_new");
        when(ctx.refreshTokenService.rotateRefreshToken(anyString(), any(Duration.class))).thenReturn(result);
        when(ctx.userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(ctx.userRoleRepository.findRoleIdsByUserId(100L)).thenReturn(List.of());
        when(ctx.userDataScopeRepository.findByUserId(100L)).thenReturn(Optional.empty());
        when(ctx.tokenService.createToken(any())).thenReturn("access_token_new");
        when(ctx.tokenService.getExpireSeconds(any())).thenReturn(7200L);
        when(ctx.tenantRepository.findByTenantIdAndIsDeleted(anyString(), anyInt())).thenReturn(Optional.empty());

        LoginResponse response = ctx.authService.refresh("refresh_token_old");

        assertEquals("access_token_new", response.getToken());
        assertEquals("refresh_token_new", response.getRefreshToken());
        verify(ctx.tokenService).revokeUserTokens(100L);
    }

    private static RbacUser buildActiveUser() {
        RbacUser user = new RbacUser();
        user.setId(100L);
        user.setTenantId("TENANT_A");
        user.setUsername("alice");
        user.setPassword("hashed_pwd");
        user.setStatus(1);
        user.setIsExperience(0);
        user.setFailedLoginCount(0);
        return user;
    }

    private static LoginRequest buildLoginRequest() {
        LoginRequest request = new LoginRequest();
        request.setTenantId("TENANT_A");
        request.setUsername("alice");
        request.setPassword("pwd");
        return request;
    }

    private static final class TestContext {
        private final RbacUserRepository userRepository = Mockito.mock(RbacUserRepository.class);
        private final RbacUserRoleRepository userRoleRepository = Mockito.mock(RbacUserRoleRepository.class);
        private final RbacRoleRepository roleRepository = Mockito.mock(RbacRoleRepository.class);
        private final RbacRolePermissionRepository rolePermissionRepository = Mockito.mock(RbacRolePermissionRepository.class);
        private final RbacPermissionRepository permissionRepository = Mockito.mock(RbacPermissionRepository.class);
        private final RbacRoleDataScopeRepository roleDataScopeRepository = Mockito.mock(RbacRoleDataScopeRepository.class);
        private final RbacUserDataScopeRepository userDataScopeRepository = Mockito.mock(RbacUserDataScopeRepository.class);
        private final RbacUserExperiencePolicyRepository experiencePolicyRepository = Mockito.mock(RbacUserExperiencePolicyRepository.class);
        private final TokenService tokenService = Mockito.mock(TokenService.class);
        private final RefreshTokenService refreshTokenService = Mockito.mock(RefreshTokenService.class);
        private final TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        private final PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
        private final AuthService authService;

        private TestContext(boolean singleSessionEnabled) {
            LingSqlProperties properties = new LingSqlProperties();
            properties.getAuth().setSingleSessionEnabled(singleSessionEnabled);
            this.authService = new AuthService(
                    userRepository,
                    userRoleRepository,
                    roleRepository,
                    rolePermissionRepository,
                    permissionRepository,
                    roleDataScopeRepository,
                    userDataScopeRepository,
                    experiencePolicyRepository,
                    tokenService,
                    refreshTokenService,
                    tenantRepository,
                    properties,
                    passwordEncoder,
                    new ObjectMapper()
            );
        }
    }
}
