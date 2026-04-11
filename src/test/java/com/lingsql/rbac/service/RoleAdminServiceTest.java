package com.lingsql.rbac.service;

import com.lingsql.rbac.entity.RbacRole;
import com.lingsql.rbac.entity.RbacRoleDataScope;
import com.lingsql.rbac.model.DataScopeRule;
import com.lingsql.rbac.model.RbacDataScopeType;
import com.lingsql.rbac.model.RoleCreateRequest;
import com.lingsql.rbac.model.RoleUpdateRequest;
import com.lingsql.rbac.model.RoleView;
import com.lingsql.rbac.repository.RbacPermissionRepository;
import com.lingsql.rbac.repository.RbacRoleDataScopeRepository;
import com.lingsql.rbac.repository.RbacRolePermissionRepository;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RbacUserRoleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleAdminServiceTest {

    @Test
    void createRoleShouldPersistOrgSubtreeDataScope() {
        TestContext ctx = new TestContext();

        RoleCreateRequest request = new RoleCreateRequest();
        request.setRoleCode("ROLE_SALES");
        request.setRoleName("销售角色");
        DataScopeRule dataScope = new DataScopeRule();
        dataScope.setScopeType(RbacDataScopeType.ORG_SUBTREE);
        dataScope.setScopeColumn("org_code");
        request.setDataScope(dataScope);

        when(ctx.roleRepository.findByTenantIdAndRoleCode("TENANT_A", "ROLE_SALES")).thenReturn(Optional.empty());
        when(ctx.roleRepository.save(any(RbacRole.class))).thenAnswer(invocation -> {
            RbacRole role = invocation.getArgument(0);
            role.setId(10L);
            return role;
        });
        when(ctx.rolePermissionRepository.findPermIdsByRoleIds(List.of(10L))).thenReturn(List.of());
        when(ctx.roleDataScopeRepository.findActiveByRoleIds(List.of(10L))).thenReturn(List.of(buildScope(10L, RbacDataScopeType.ORG_SUBTREE, "org_code", "")));

        RoleView view = ctx.roleAdminService.createRole(request, "TENANT_A", false);

        assertEquals(RbacDataScopeType.ORG_SUBTREE, view.getDataScope().getScopeType());
        assertEquals("org_code", view.getDataScope().getScopeColumn());
        verify(ctx.roleDataScopeRepository).deleteByRoleId(10L);
        verify(ctx.roleDataScopeRepository).save(any(RbacRoleDataScope.class));
    }

    @Test
    void updateRoleShouldClearRoleDataScopeWhenResetToTenant() {
        TestContext ctx = new TestContext();

        RbacRole role = new RbacRole();
        role.setId(11L);
        role.setTenantId("TENANT_A");
        role.setRoleCode("ROLE_FINANCE");
        role.setRoleName("财务");
        role.setStatus(1);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setRoleName("财务主管");
        DataScopeRule dataScope = new DataScopeRule();
        dataScope.setScopeType(RbacDataScopeType.TENANT);
        request.setDataScope(dataScope);

        when(ctx.roleRepository.findById(11L)).thenReturn(Optional.of(role));
        when(ctx.roleRepository.save(any(RbacRole.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ctx.rolePermissionRepository.findPermIdsByRoleIds(List.of(11L))).thenReturn(List.of());
        when(ctx.roleDataScopeRepository.findActiveByRoleIds(List.of(11L))).thenReturn(List.of());
        when(ctx.userRoleRepository.findUserIdsByRoleId(11L)).thenReturn(List.of());

        RoleView view = ctx.roleAdminService.updateRole(11L, request, "TENANT_A", false);

        assertEquals("财务主管", view.getRoleName());
        assertEquals(RbacDataScopeType.TENANT, view.getDataScope().getScopeType());
        verify(ctx.roleDataScopeRepository).deleteByRoleId(11L);
    }

    private static RbacRoleDataScope buildScope(Long roleId, String scopeType, String scopeColumn, String scopeRule) {
        RbacRoleDataScope entity = new RbacRoleDataScope();
        entity.setRoleId(roleId);
        entity.setScopeType(scopeType);
        entity.setScopeColumn(scopeColumn);
        entity.setScopeRule(scopeRule);
        entity.setStatus(1);
        return entity;
    }

    private static final class TestContext {
        private final RbacRoleRepository roleRepository = Mockito.mock(RbacRoleRepository.class);
        private final RbacRolePermissionRepository rolePermissionRepository = Mockito.mock(RbacRolePermissionRepository.class);
        private final RbacRoleDataScopeRepository roleDataScopeRepository = Mockito.mock(RbacRoleDataScopeRepository.class);
        private final RbacPermissionRepository permissionRepository = Mockito.mock(RbacPermissionRepository.class);
        private final TenantPermissionService tenantPermissionService = Mockito.mock(TenantPermissionService.class);
        private final RbacUserRoleRepository userRoleRepository = Mockito.mock(RbacUserRoleRepository.class);
        private final TokenService tokenService = Mockito.mock(TokenService.class);
        private final RefreshTokenService refreshTokenService = Mockito.mock(RefreshTokenService.class);
        private final RoleAdminService roleAdminService = new RoleAdminService(
                roleRepository,
                rolePermissionRepository,
                roleDataScopeRepository,
                permissionRepository,
                tenantPermissionService,
                userRoleRepository,
                tokenService,
                refreshTokenService
        );
    }
}
