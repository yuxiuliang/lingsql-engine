package com.lingsql.tenant.service;

import com.lingsql.billing.BillingBalanceService;
import com.lingsql.billing.repository.BillingPlanRepository;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.executor.SqlTaskTracker;
import com.lingsql.organization.model.OrgTemplateApplyRequest;
import com.lingsql.organization.service.OrganizationAdminService;
import com.lingsql.rbac.entity.RbacPermission;
import com.lingsql.rbac.entity.RbacRole;
import com.lingsql.rbac.repository.RbacPermissionRepository;
import com.lingsql.rbac.repository.RbacRoleDataScopeRepository;
import com.lingsql.rbac.repository.RbacRolePermissionRepository;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RbacTenantPermissionRepository;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.model.TenantCreateRequest;
import com.lingsql.tenant.model.TenantLifecycleSnapshot;
import com.lingsql.tenant.model.TenantLifecycleState;
import com.lingsql.tenant.model.TenantRiskLevel;
import com.lingsql.tenant.model.TenantRiskSnapshot;
import com.lingsql.tenant.model.TenantView;
import com.lingsql.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantAdminServiceOrgBootstrapTest {

    @Test
    void createTenantShouldApplyOrganizationTemplateWhenProvided() {
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        RbacPermissionRepository permissionRepository = Mockito.mock(RbacPermissionRepository.class);
        RbacRoleRepository roleRepository = Mockito.mock(RbacRoleRepository.class);
        RbacRolePermissionRepository rolePermissionRepository = Mockito.mock(RbacRolePermissionRepository.class);
        RbacTenantPermissionRepository tenantPermissionRepository = Mockito.mock(RbacTenantPermissionRepository.class);
        OrganizationAdminService organizationAdminService = Mockito.mock(OrganizationAdminService.class);
        TenantLifecycleService lifecycleService = Mockito.mock(TenantLifecycleService.class);
        BillingBalanceService billingBalanceService = Mockito.mock(BillingBalanceService.class);

        TenantAdminService service = new TenantAdminService(
                tenantRepository,
                Mockito.mock(TenantDbConfigRepository.class),
                permissionRepository,
                tenantPermissionRepository,
                roleRepository,
                rolePermissionRepository,
                Mockito.mock(RbacRoleDataScopeRepository.class),
                Mockito.mock(RbacUserRepository.class),
                Mockito.mock(SqlTaskTracker.class),
                billingBalanceService,
                Mockito.mock(BillingPlanRepository.class),
                lifecycleService,
                organizationAdminService
        );

        when(tenantRepository.existsByTenantIdAndIsDeleted(any(), any(Integer.class))).thenReturn(false);
        when(tenantRepository.save(any(TenantInfo.class))).thenAnswer(invocation -> {
            TenantInfo tenant = invocation.getArgument(0);
            tenant.setId(1L);
            return tenant;
        });
        when(roleRepository.findByTenantIdAndRoleCode("AUTO001", "TENANT_ADMIN")).thenReturn(Optional.empty());
        when(roleRepository.save(any(RbacRole.class))).thenAnswer(invocation -> {
            RbacRole role = invocation.getArgument(0);
            role.setId(2L);
            return role;
        });
        when(permissionRepository.findByPermCodeIn(any())).thenReturn(List.of(
                buildPermission(11L, "MENU:ADMIN"),
                buildPermission(12L, "MENU:ADMIN:ORG"),
                buildPermission(13L, "ADMIN:ORG:READ"),
                buildPermission(14L, "ADMIN:ORG:WRITE")
        ));
        when(tenantPermissionRepository.findPermIdsByTenantId("AUTO001")).thenReturn(List.of());
        when(rolePermissionRepository.findPermIdsByRoleIds(List.of(2L))).thenReturn(List.of());
        when(lifecycleService.evaluate(any(TenantInfo.class)))
                .thenReturn(new TenantLifecycleSnapshot(TenantLifecycleState.ACTIVE, Long.MAX_VALUE, 0));
        when(lifecycleService.evaluateRisk(any(TenantInfo.class)))
                .thenReturn(new TenantRiskSnapshot(0, TenantRiskLevel.LOW, List.of("稳定")));

        TenantCreateRequest request = new TenantCreateRequest();
        request.setTenantId("AUTO001");
        request.setTenantName("演示租户");
        request.setOrgTemplateCode("CHAIN_STORE_STANDARD");

        TenantView view = service.createTenant(request);

        assertEquals("AUTO001", view.getTenantId());
        verify(billingBalanceService).resetBalance("AUTO001", 0L);
        ArgumentCaptor<OrgTemplateApplyRequest> templateCaptor = ArgumentCaptor.forClass(OrgTemplateApplyRequest.class);
        verify(organizationAdminService).applyTemplate(templateCaptor.capture(), Mockito.eq("AUTO001"), Mockito.eq(false));
        assertEquals("AUTO001", templateCaptor.getValue().getTenantId());
        assertEquals("CHAIN_STORE_STANDARD", templateCaptor.getValue().getTemplateCode());
    }

    private RbacPermission buildPermission(Long id, String permCode) {
        RbacPermission permission = new RbacPermission();
        permission.setId(id);
        permission.setPermCode(permCode);
        permission.setStatus(1);
        return permission;
    }
}
