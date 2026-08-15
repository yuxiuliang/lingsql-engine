package com.lingsql.rbac.init;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.lingsql.billing.BillingBalanceService;
import com.lingsql.billing.repository.BillingPlanRepository;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.rbac.entity.RbacPermission;
import com.lingsql.rbac.entity.RbacRole;
import com.lingsql.rbac.entity.RbacRolePermission;
import com.lingsql.rbac.entity.RbacTenantPermission;
import com.lingsql.rbac.repository.RbacMenuRepository;
import com.lingsql.rbac.repository.RbacPermissionRepository;
import com.lingsql.rbac.repository.RbacRolePermissionRepository;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RbacTenantPermissionRepository;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.rbac.repository.RbacUserRoleRepository;
import com.lingsql.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuperAdminInitializerTest {

    @Test
    void rbacSeedShouldContainOrganizationPermissionsAndMenu() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("rbac-seed.json")) {
            assertNotNull(inputStream, "rbac-seed.json 不存在");
            JsonNode root = objectMapper.readTree(inputStream);
            Set<String> permissionCodes = collectCodes(root.path("permissions"), "permCode");
            Set<String> menuCodes = collectCodes(root.path("menus"), "menuCode");

            assertEquals(true, permissionCodes.contains("MENU:ADMIN:ORG"));
            assertEquals(true, permissionCodes.contains("ADMIN:ORG:READ"));
            assertEquals(true, permissionCodes.contains("ADMIN:ORG:WRITE"));
            assertEquals(true, permissionCodes.contains("ADMIN:ORG:DELETE"));
            assertEquals(true, menuCodes.contains("MENU_ADMIN_ORG"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void repairTenantAdminOrgAccessShouldBackfillMissingRoleAndTenantPermissions() {
        RbacPermissionRepository permissionRepository = Mockito.mock(RbacPermissionRepository.class);
        RbacRoleRepository roleRepository = Mockito.mock(RbacRoleRepository.class);
        RbacRolePermissionRepository rolePermissionRepository = Mockito.mock(RbacRolePermissionRepository.class);
        RbacTenantPermissionRepository tenantPermissionRepository = Mockito.mock(RbacTenantPermissionRepository.class);

        when(permissionRepository.findByPermCodeIn(anyList())).thenReturn(List.of(
                buildPermission(1L, "MENU:ADMIN"),
                buildPermission(2L, "MENU:ADMIN:ORG"),
                buildPermission(3L, "ADMIN:ORG:READ"),
                buildPermission(4L, "ADMIN:ORG:WRITE"),
                buildPermission(5L, "ADMIN:ORG:DELETE")
        ));
        when(roleRepository.findAll()).thenReturn(List.of(buildTenantAdminRole(10L, "tenant-a")));
        when(rolePermissionRepository.findPermIdsByRoleIds(List.of(10L))).thenReturn(List.of(1L, 2L));
        when(tenantPermissionRepository.findPermIdsByTenantId("tenant-a")).thenReturn(List.of(1L));

        SuperAdminInitializer initializer = new SuperAdminInitializer(
                new LingSqlProperties(),
                Mockito.mock(TenantRepository.class),
                Mockito.mock(RbacUserRepository.class),
                roleRepository,
                permissionRepository,
                rolePermissionRepository,
                Mockito.mock(RbacUserRoleRepository.class),
                Mockito.mock(RbacMenuRepository.class),
                tenantPermissionRepository,
                Mockito.mock(PasswordEncoder.class),
                new ObjectMapper(),
                new DefaultResourceLoader(),
                Mockito.mock(BillingBalanceService.class),
                Mockito.mock(BillingPlanRepository.class)
        );

        ReflectionTestUtils.invokeMethod(initializer, "repairTenantAdminOrgAccess");

        ArgumentCaptor<List<RbacRolePermission>> rolePermissionCaptor = ArgumentCaptor.forClass(List.class);
        verify(rolePermissionRepository).saveAll(rolePermissionCaptor.capture());
        Set<Long> repairedRolePermissionIds = rolePermissionCaptor.getValue().stream()
                .map(RbacRolePermission::getPermId)
                .collect(Collectors.toSet());
        assertEquals(Set.of(3L, 4L, 5L), repairedRolePermissionIds);

        ArgumentCaptor<List<RbacTenantPermission>> tenantPermissionCaptor = ArgumentCaptor.forClass(List.class);
        verify(tenantPermissionRepository).saveAll(tenantPermissionCaptor.capture());
        Set<Long> repairedTenantPermissionIds = tenantPermissionCaptor.getValue().stream()
                .map(RbacTenantPermission::getPermId)
                .collect(Collectors.toSet());
        assertEquals(Set.of(2L, 3L, 4L, 5L), repairedTenantPermissionIds);
    }

    private Set<String> collectCodes(JsonNode nodes, String fieldName) {
        return nodes.findValues(fieldName).stream().map(JsonNode::asText).collect(Collectors.toSet());
    }

    private RbacPermission buildPermission(Long id, String code) {
        RbacPermission permission = new RbacPermission();
        permission.setId(id);
        permission.setPermCode(code);
        permission.setStatus(1);
        return permission;
    }

    private RbacRole buildTenantAdminRole(Long id, String tenantId) {
        RbacRole role = new RbacRole();
        role.setId(id);
        role.setTenantId(tenantId);
        role.setRoleCode("TENANT_ADMIN");
        role.setStatus(1);
        return role;
    }
}
