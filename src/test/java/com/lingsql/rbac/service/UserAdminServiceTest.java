package com.lingsql.rbac.service;

import com.lingsql.common.pagination.PageParamResolver;
import com.lingsql.common.exception.BizException;
import com.lingsql.rbac.entity.RbacUser;
import com.lingsql.rbac.entity.RbacUserDataScope;
import com.lingsql.rbac.model.DataScopeRule;
import com.lingsql.rbac.model.RbacDataScopeType;
import com.lingsql.rbac.model.UserCreateRequest;
import com.lingsql.rbac.model.UserUpdateRequest;
import com.lingsql.rbac.model.UserView;
import com.lingsql.rbac.repository.RbacRoleDataScopeRepository;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RbacUserDataScopeRepository;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.rbac.repository.RbacUserRoleRepository;
import com.lingsql.organization.repository.OrgUnitRepository;
import com.lingsql.organization.repository.UserOrgMembershipRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAdminServiceTest {

    @Test
    void createUserShouldPersistTenantLevelOverride() {
        TestContext ctx = new TestContext();

        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("alice");
        request.setPassword("abc12345");
        request.setNickname("爱丽丝");
        DataScopeRule dataScope = new DataScopeRule();
        dataScope.setScopeType(RbacDataScopeType.TENANT);
        request.setDataScope(dataScope);

        when(ctx.userRepository.findByTenantIdAndUsername("TENANT_A", "alice")).thenReturn(Optional.empty());
        when(ctx.passwordEncoder.encode("abc12345")).thenReturn("encoded_pwd");
        when(ctx.userRepository.save(any(RbacUser.class))).thenAnswer(invocation -> {
            RbacUser user = invocation.getArgument(0);
            user.setId(101L);
            return user;
        });
        when(ctx.userRoleRepository.findRoleIdsByUserId(101L)).thenReturn(List.of());
        when(ctx.userOrgMembershipRepository.findByUserIdOrderByIsPrimaryDescIdAsc(101L)).thenReturn(List.of());
        when(ctx.userDataScopeRepository.findByUserId(101L))
                .thenReturn(Optional.of(buildScope(101L, RbacDataScopeType.TENANT, "", "")));

        UserView view = ctx.userAdminService.createUser(request, "TENANT_A", false);

        assertEquals(RbacDataScopeType.TENANT, view.getDataScope().getScopeType());
        assertEquals(RbacDataScopeType.TENANT, view.getEffectiveDataScope().getScopeType());
        assertEquals("USER_OVERRIDE", view.getEffectiveDataScopeSource());
        verify(ctx.userDataScopeRepository).deleteByUserId(101L);
        verify(ctx.userDataScopeRepository).save(any(RbacUserDataScope.class));
        verify(ctx.tokenService, never()).revokeUserTokens(any());
    }

    @Test
    void createUserShouldRejectOrgScopeWithoutOrgMembership() {
        TestContext ctx = new TestContext();

        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("carol");
        request.setPassword("abc12345");
        DataScopeRule dataScope = new DataScopeRule();
        dataScope.setScopeType(RbacDataScopeType.ORG_SUBTREE);
        dataScope.setScopeColumn("org_code");
        request.setDataScope(dataScope);

        when(ctx.userRepository.findByTenantIdAndUsername("TENANT_A", "carol")).thenReturn(Optional.empty());
        when(ctx.passwordEncoder.encode("abc12345")).thenReturn("encoded_pwd");
        when(ctx.userRepository.save(any(RbacUser.class))).thenAnswer(invocation -> {
            RbacUser user = invocation.getArgument(0);
            user.setId(103L);
            return user;
        });
        when(ctx.userOrgMembershipRepository.findByUserIdOrderByIsPrimaryDescIdAsc(103L)).thenReturn(List.of());

        assertThrows(BizException.class, () -> ctx.userAdminService.createUser(request, "TENANT_A", false));
    }

    @Test
    void updateUserShouldClearOverrideWhenResetToInheritedScope() {
        TestContext ctx = new TestContext();

        RbacUser user = new RbacUser();
        user.setId(102L);
        user.setTenantId("TENANT_A");
        user.setUsername("bob");
        user.setNickname("鲍勃");
        user.setStatus(1);
        user.setIsExperience(0);

        UserUpdateRequest request = new UserUpdateRequest();
        DataScopeRule dataScope = new DataScopeRule();
        dataScope.setScopeType("INHERIT");
        request.setDataScope(dataScope);

        when(ctx.userRepository.findById(102L)).thenReturn(Optional.of(user));
        when(ctx.userRepository.save(any(RbacUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ctx.userRoleRepository.findRoleIdsByUserId(102L)).thenReturn(List.of());
        when(ctx.userOrgMembershipRepository.findByUserIdOrderByIsPrimaryDescIdAsc(102L)).thenReturn(List.of());
        when(ctx.userDataScopeRepository.findByUserId(102L)).thenReturn(Optional.empty());

        UserView view = ctx.userAdminService.updateUser(102L, request, "TENANT_A", false);

        assertEquals("INHERIT", view.getDataScope().getScopeType());
        assertEquals(RbacDataScopeType.TENANT, view.getEffectiveDataScope().getScopeType());
        assertEquals("DEFAULT_TENANT", view.getEffectiveDataScopeSource());
        verify(ctx.userDataScopeRepository).deleteByUserId(102L);
        verify(ctx.tokenService).revokeUserTokens(102L);
        verify(ctx.refreshTokenService).revokeByUserId(102L);
    }

    private static RbacUserDataScope buildScope(Long userId, String scopeType, String scopeColumn, String scopeRule) {
        RbacUserDataScope entity = new RbacUserDataScope();
        entity.setUserId(userId);
        entity.setScopeType(scopeType);
        entity.setScopeColumn(scopeColumn);
        entity.setScopeRule(scopeRule);
        entity.setStatus(1);
        return entity;
    }

    private static final class TestContext {
        private final RbacUserRepository userRepository = Mockito.mock(RbacUserRepository.class);
        private final RbacUserRoleRepository userRoleRepository = Mockito.mock(RbacUserRoleRepository.class);
        private final RbacRoleDataScopeRepository roleDataScopeRepository = Mockito.mock(RbacRoleDataScopeRepository.class);
        private final RbacRoleRepository roleRepository = Mockito.mock(RbacRoleRepository.class);
        private final PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
        private final TokenService tokenService = Mockito.mock(TokenService.class);
        private final RefreshTokenService refreshTokenService = Mockito.mock(RefreshTokenService.class);
        private final PageParamResolver pageParamResolver = Mockito.mock(PageParamResolver.class);
        private final OrgUnitRepository orgUnitRepository = Mockito.mock(OrgUnitRepository.class);
        private final UserOrgMembershipRepository userOrgMembershipRepository = Mockito.mock(UserOrgMembershipRepository.class);
        private final RbacUserDataScopeRepository userDataScopeRepository = Mockito.mock(RbacUserDataScopeRepository.class);
        private final UserAdminService userAdminService = new UserAdminService(
                userRepository,
                userRoleRepository,
                roleDataScopeRepository,
                roleRepository,
                passwordEncoder,
                tokenService,
                refreshTokenService,
                pageParamResolver,
                orgUnitRepository,
                userOrgMembershipRepository,
                userDataScopeRepository
        );
    }
}
