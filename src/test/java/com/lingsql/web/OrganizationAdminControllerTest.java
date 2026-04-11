package com.lingsql.web;

import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.organization.model.OrgUnitTypeCreateRequest;
import com.lingsql.organization.model.OrgUnitTypeView;
import com.lingsql.organization.service.OrganizationAdminService;
import com.lingsql.rbac.security.RbacUserSession;
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

class OrganizationAdminControllerTest {

    private OrganizationAdminService organizationAdminService;
    private AuditOperationExecutor auditOperationExecutor;
    private OrganizationAdminController controller;

    @BeforeEach
    void setUp() {
        organizationAdminService = Mockito.mock(OrganizationAdminService.class);
        auditOperationExecutor = Mockito.mock(AuditOperationExecutor.class);
        controller = new OrganizationAdminController(organizationAdminService, auditOperationExecutor);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listUnitTypesShouldUseSessionTenantForTenantAdmin() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        OrgUnitTypeView view = new OrgUnitTypeView();
        view.setId(1L);
        view.setTypeCode("GOV");
        view.setTypeName("行政层级");
        when(organizationAdminService.listUnitTypes("tenant-a")).thenReturn(List.of(view));

        ApiResponse<List<OrgUnitTypeView>> response = controller.listUnitTypes(null);

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        verify(organizationAdminService, times(1)).listUnitTypes("tenant-a");
    }

    @Test
    void createUnitTypeShouldDelegateByAuditExecutor() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        OrgUnitTypeCreateRequest request = new OrgUnitTypeCreateRequest();
        request.setTypeCode("SCHOOL");
        request.setTypeName("学校组织");
        OrgUnitTypeView view = new OrgUnitTypeView();
        view.setId(2L);
        view.setTypeCode("SCHOOL");
        view.setTypeName("学校组织");
        when(organizationAdminService.createUnitType(request, "tenant-a", false)).thenReturn(view);
        mockAuditExecute();

        ApiResponse<OrgUnitTypeView> response = controller.createUnitType(request, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals("SCHOOL", response.getData().getTypeCode());
        verify(organizationAdminService, times(1)).createUnitType(request, "tenant-a", false);
        verify(auditOperationExecutor, times(1)).execute(
                eq("ORG_TYPE_CREATE"),
                eq("ORG_TYPE"),
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
