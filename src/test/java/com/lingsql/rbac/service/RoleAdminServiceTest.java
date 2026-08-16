package com.lingsql.rbac.service;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.organization.service.OrgGovernanceCatalogService;
import com.lingsql.rbac.authorization.IAuthorizationSessionLifecycle;
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
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        RoleView view = ctx.roleAdminService.updateRole(11L, request, "TENANT_A", false);

        assertEquals("财务主管", view.getRoleName());
        assertEquals(RbacDataScopeType.TENANT, view.getDataScope().getScopeType());
        verify(ctx.roleDataScopeRepository).deleteByRoleId(11L);
    }

    @Test
    void createRoleShouldPersistRelationSubtreeDataScope() {
        TestContext ctx = new TestContext();

        RoleCreateRequest request = new RoleCreateRequest();
        request.setRoleCode("ROLE_SERVICE");
        request.setRoleName("服务归口角色");
        DataScopeRule dataScope = new DataScopeRule();
        dataScope.setScopeType(RbacDataScopeType.RELATION_SUBTREE);
        dataScope.setScopeColumn("org_code");
        dataScope.setRelationTypes(List.of("service", "matrix"));
        request.setDataScope(dataScope);

        when(ctx.roleRepository.findByTenantIdAndRoleCode("TENANT_A", "ROLE_SERVICE")).thenReturn(Optional.empty());
        when(ctx.roleRepository.save(any(RbacRole.class))).thenAnswer(invocation -> {
            RbacRole role = invocation.getArgument(0);
            role.setId(12L);
            return role;
        });
        when(ctx.rolePermissionRepository.findPermIdsByRoleIds(List.of(12L))).thenReturn(List.of());
        when(ctx.roleDataScopeRepository.findActiveByRoleIds(List.of(12L)))
                .thenReturn(List.of(buildScope(12L, RbacDataScopeType.RELATION_SUBTREE, "org_code", "SERVICE,MATRIX")));

        RoleView view = ctx.roleAdminService.createRole(request, "TENANT_A", false);

        assertEquals(RbacDataScopeType.RELATION_SUBTREE, view.getDataScope().getScopeType());
        assertEquals(List.of("SERVICE", "MATRIX"), view.getDataScope().getRelationTypes());
        verify(ctx.roleDataScopeRepository).save(any(RbacRoleDataScope.class));
        verify(ctx.orgGovernanceCatalogService).validateRelationTypes(
                "TENANT_A",
                List.of("SERVICE", "MATRIX"),
                "数据权限关系类型"
        );
    }

    @Test
    void createRoleShouldRejectUnknownRelationType() {
        TestContext ctx = new TestContext();

        RoleCreateRequest request = new RoleCreateRequest();
        request.setRoleCode("ROLE_CUSTOM");
        request.setRoleName("自定义关系角色");
        DataScopeRule dataScope = new DataScopeRule();
        dataScope.setScopeType(RbacDataScopeType.RELATION);
        dataScope.setScopeColumn("org_code");
        dataScope.setRelationTypes(List.of("unknown_type"));
        request.setDataScope(dataScope);

        when(ctx.roleRepository.findByTenantIdAndRoleCode("TENANT_A", "ROLE_CUSTOM")).thenReturn(Optional.empty());
        when(ctx.roleRepository.save(any(RbacRole.class))).thenAnswer(invocation -> {
            RbacRole role = invocation.getArgument(0);
            role.setId(13L);
            return role;
        });
        Mockito.doThrow(new BizException(ErrorCode.INVALID_PARAM, "数据权限关系类型存在未定义项: UNKNOWN_TYPE"))
                .when(ctx.orgGovernanceCatalogService)
                .validateRelationTypes("TENANT_A", List.of("UNKNOWN_TYPE"), "数据权限关系类型");

        BizException ex = assertThrows(BizException.class, () -> ctx.roleAdminService.createRole(request, "TENANT_A", false));

        assertEquals(ErrorCode.INVALID_PARAM, ex.getErrorCode());
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
        private final IAuthorizationSessionLifecycle authorizationSessionLifecycle =
                Mockito.mock(IAuthorizationSessionLifecycle.class);
        private final OrgGovernanceCatalogService orgGovernanceCatalogService = Mockito.mock(OrgGovernanceCatalogService.class);
        private final RoleAdminService roleAdminService = new RoleAdminService(
                roleRepository,
                rolePermissionRepository,
                roleDataScopeRepository,
                permissionRepository,
                tenantPermissionService,
                authorizationSessionLifecycle,
                orgGovernanceCatalogService
        );

        private TestContext() {
            when(authorizationSessionLifecycle.publish(any(), any())).thenAnswer(invocation ->
                    ((Supplier<?>) invocation.getArgument(1)).get()
            );
        }
    }
}
