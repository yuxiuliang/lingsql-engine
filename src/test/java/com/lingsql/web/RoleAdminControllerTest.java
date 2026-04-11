package com.lingsql.web;

import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.model.DataScopeRule;
import com.lingsql.rbac.model.RbacDataScopeType;
import com.lingsql.rbac.model.RoleCreateRequest;
import com.lingsql.rbac.model.RoleView;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.service.RoleAdminService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleAdminControllerTest {

    private RoleAdminService roleAdminService;
    private AuditOperationExecutor auditOperationExecutor;
    private RoleAdminController controller;

    @BeforeEach
    void setUp() {
        roleAdminService = Mockito.mock(RoleAdminService.class);
        auditOperationExecutor = Mockito.mock(AuditOperationExecutor.class);
        controller = new RoleAdminController(roleAdminService, auditOperationExecutor);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listRolesShouldUseSessionTenantForTenantAdmin() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        RoleView view = new RoleView();
        view.setId(1L);
        view.setRoleCode("ROLE_GOV");
        view.setRoleName("政府角色");
        DataScopeRule rule = new DataScopeRule();
        rule.setScopeType(RbacDataScopeType.ORG_SUBTREE);
        view.setDataScope(rule);
        when(roleAdminService.listRoles("tenant-a", false)).thenReturn(List.of(view));

        ApiResponse<List<RoleView>> response = controller.listRoles(null);

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals(RbacDataScopeType.ORG_SUBTREE, response.getData().get(0).getDataScope().getScopeType());
        verify(roleAdminService, times(1)).listRoles("tenant-a", false);
    }

    @Test
    void createRoleShouldDelegateDataScopeByAuditExecutor() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        RoleCreateRequest request = new RoleCreateRequest();
        request.setRoleCode("ROLE_FINANCE");
        request.setRoleName("财务角色");
        DataScopeRule requestScope = new DataScopeRule();
        requestScope.setScopeType(RbacDataScopeType.ORG);
        requestScope.setScopeColumn("org_code");
        request.setDataScope(requestScope);

        RoleView view = new RoleView();
        view.setId(2L);
        view.setRoleCode("ROLE_FINANCE");
        view.setRoleName("财务角色");
        DataScopeRule responseScope = new DataScopeRule();
        responseScope.setScopeType(RbacDataScopeType.ORG);
        responseScope.setScopeColumn("org_code");
        view.setDataScope(responseScope);

        when(roleAdminService.createRole(request, "tenant-a", false)).thenReturn(view);
        mockAuditExecute();

        ApiResponse<RoleView> response = controller.createRole(request, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals(RbacDataScopeType.ORG, response.getData().getDataScope().getScopeType());
        verify(roleAdminService, times(1)).createRole(request, "tenant-a", false);
        verify(auditOperationExecutor, times(1)).execute(
                eq("ROLE_CREATE"),
                eq("ROLE"),
                eq(request),
                any(),
                any()
        );
    }

    private void mockAuditExecute() {
        when(auditOperationExecutor.execute(anyString(), anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return ApiResponse.success(supplier.get());
                });
    }

    private void bindSession(String tenantId, List<String> roles) {
        RbacUserSession session = new RbacUserSession();
        session.setUserId(1L);
        session.setTenantId(tenantId);
        session.setRoles(roles);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(session, "token", List.of())
        );
    }
}
