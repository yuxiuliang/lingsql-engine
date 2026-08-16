package com.lingsql.tenant.service;

import com.lingsql.billing.BillingBalanceService;
import com.lingsql.billing.entity.BillingPlan;
import com.lingsql.billing.repository.BillingPlanRepository;
import com.lingsql.datasource.TenantDataSourceConfigService;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.executor.SqlTaskTracker;
import com.lingsql.ops.OpsAlertService;
import com.lingsql.organization.model.OrgTemplateApplyRequest;
import com.lingsql.organization.service.OrganizationAdminService;
import com.lingsql.rbac.entity.RbacPermission;
import com.lingsql.rbac.entity.RbacRole;
import com.lingsql.rbac.authorization.IAuthorizationSessionLifecycle;
import com.lingsql.rbac.repository.RbacPermissionRepository;
import com.lingsql.rbac.repository.RbacRoleDataScopeRepository;
import com.lingsql.rbac.repository.RbacRolePermissionRepository;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RbacTenantPermissionRepository;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.model.TenantCreateRequest;
import com.lingsql.tenant.model.TenantAdminAccountRequest;
import com.lingsql.tenant.model.TenantDataSourceProvisionRequest;
import com.lingsql.tenant.model.TenantLifecycleSnapshot;
import com.lingsql.tenant.model.TenantLifecycleState;
import com.lingsql.tenant.model.TenantRiskLevel;
import com.lingsql.tenant.model.TenantRiskSnapshot;
import com.lingsql.tenant.model.TenantProvisioningReceipt;
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
        BillingPlanRepository billingPlanRepository = Mockito.mock(BillingPlanRepository.class);
        TenantDataSourceConfigService dataSourceConfigService = Mockito.mock(TenantDataSourceConfigService.class);
        TenantDbConfigRepository tenantDbConfigRepository = Mockito.mock(TenantDbConfigRepository.class);

        TenantAdminService service = new TenantAdminService(
                tenantRepository,
                tenantDbConfigRepository,
                permissionRepository,
                tenantPermissionRepository,
                roleRepository,
                rolePermissionRepository,
                Mockito.mock(RbacRoleDataScopeRepository.class),
                Mockito.mock(RbacUserRepository.class),
                Mockito.mock(SqlTaskTracker.class),
                billingBalanceService,
                billingPlanRepository,
                lifecycleService,
                organizationAdminService,
                Mockito.mock(OpsAlertService.class),
                Mockito.mock(com.lingsql.rbac.service.UserAdminService.class),
                Mockito.mock(com.lingsql.rbac.repository.RbacUserRoleRepository.class),
                dataSourceConfigService,
                Mockito.mock(IAuthorizationSessionLifecycle.class)
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
                buildPermission(14L, "ADMIN:ORG:WRITE"),
                buildPermission(15L, "ADMIN:ORG:DELETE"),
                buildPermission(16L, "MENU:CHAT"),
                buildPermission(17L, "MENU:CHAT:HISTORY"),
                buildPermission(18L, "API:CHAT:ASK"),
                buildPermission(19L, "API:CHAT:HISTORY:READ"),
                buildPermission(20L, "API:CHAT:HISTORY:WRITE"),
                buildPermission(21L, "CONFIG:CHAT:READ")
        ));
        BillingPlan plan = new BillingPlan();
        plan.setPlanName("试点套餐");
        plan.setStatus(1);
        plan.setDefaultQuotaBalance(1000L);
        plan.setServiceLevel("PILOT");
        plan.setTenantType("ENTERPRISE");
        when(billingPlanRepository.findByPlanNameAndIsDeleted("试点套餐", 0)).thenReturn(Optional.of(plan));
        when(tenantPermissionRepository.findPermIdsByTenantId("AUTO001")).thenReturn(List.of());
        when(rolePermissionRepository.findPermIdsByRoleIds(List.of(2L))).thenReturn(List.of());
        when(lifecycleService.evaluate(any(TenantInfo.class)))
                .thenReturn(new TenantLifecycleSnapshot(TenantLifecycleState.ACTIVE, Long.MAX_VALUE, 0));
        when(lifecycleService.evaluateRisk(any(TenantInfo.class)))
                .thenReturn(new TenantRiskSnapshot(0, TenantRiskLevel.LOW, List.of("稳定")));

        TenantCreateRequest request = new TenantCreateRequest();
        request.setTenantId("AUTO001");
        request.setTenantName("演示租户");
        request.setPlanName("试点套餐");
        request.setOrgTemplateCode("CHAIN_STORE_STANDARD");
        request.setOrgRootUnitName("华东品牌总部");
        request.setNotificationChannels(List.of("dingtalk", "wecom"));
        TenantAdminAccountRequest adminAccount = new TenantAdminAccountRequest();
        adminAccount.setUsername("tenant-admin");
        adminAccount.setPassword("Password123");
        request.setAdminAccount(adminAccount);
        TenantDataSourceProvisionRequest dataSource = new TenantDataSourceProvisionRequest();
        dataSource.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/demo");
        dataSource.setUsername("readonly");
        dataSource.setPassword("Database123");
        dataSource.setDriverClass("com.mysql.cj.jdbc.Driver");
        dataSource.setIsolationMode("PHYSICAL");
        dataSource.setWhiteListTables("orders,customers");
        dataSource.setWhiteListColumns("orders.id,orders.amount,customers.id");
        request.setDataSource(dataSource);

        TenantProvisioningReceipt receipt = service.createTenant(request);

        assertEquals("AUTO001", receipt.tenantId());
        assertEquals("READY", receipt.status());
        assertEquals(1000L, receipt.openingBalance());
        verify(dataSourceConfigService).verifyReadOnlyConnection(any());
        verify(tenantDbConfigRepository).save(any());
        verify(billingBalanceService).resetBalance("AUTO001", 1000L);
        ArgumentCaptor<OrgTemplateApplyRequest> templateCaptor = ArgumentCaptor.forClass(OrgTemplateApplyRequest.class);
        verify(organizationAdminService).applyTemplate(templateCaptor.capture(), Mockito.eq("AUTO001"), Mockito.eq(false));
        assertEquals("AUTO001", templateCaptor.getValue().getTenantId());
        assertEquals("CHAIN_STORE_STANDARD", templateCaptor.getValue().getTemplateCode());
        assertEquals("华东品牌总部", templateCaptor.getValue().getRootUnitName());
    }

    private RbacPermission buildPermission(Long id, String permCode) {
        RbacPermission permission = new RbacPermission();
        permission.setId(id);
        permission.setPermCode(permCode);
        permission.setStatus(1);
        return permission;
    }
}
