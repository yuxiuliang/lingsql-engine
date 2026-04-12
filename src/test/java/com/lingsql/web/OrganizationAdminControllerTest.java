package com.lingsql.web;

import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.organization.model.OrgRelationExecutionCreateRequest;
import com.lingsql.organization.model.OrgRelationExecutionTimelineView;
import com.lingsql.organization.model.OrgRelationExecutionUpdateRequest;
import com.lingsql.organization.model.OrgRelationExecutionView;
import com.lingsql.organization.model.OrgRelationRouteResolveView;
import com.lingsql.organization.model.OrgRelationWorkbenchView;
import com.lingsql.organization.model.OrgTemplateApplyRequest;
import com.lingsql.organization.model.OrgTemplateView;
import com.lingsql.organization.model.OrgUnitTypeCreateRequest;
import com.lingsql.organization.model.OrgUnitTypeView;
import com.lingsql.organization.service.OrgRelationExecutionService;
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
    private OrgRelationExecutionService orgRelationExecutionService;
    private AuditOperationExecutor auditOperationExecutor;
    private OrganizationAdminController controller;

    @BeforeEach
    void setUp() {
        organizationAdminService = Mockito.mock(OrganizationAdminService.class);
        orgRelationExecutionService = Mockito.mock(OrgRelationExecutionService.class);
        auditOperationExecutor = Mockito.mock(AuditOperationExecutor.class);
        controller = new OrganizationAdminController(organizationAdminService, orgRelationExecutionService, auditOperationExecutor);
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

    @Test
    void listTemplatesShouldReturnBuiltInTemplates() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        OrgTemplateView view = new OrgTemplateView();
        view.setTemplateCode("GOVERNMENT_STANDARD");
        view.setTemplateName("政府标准模板");
        when(organizationAdminService.listTemplates()).thenReturn(List.of(view));

        ApiResponse<List<OrgTemplateView>> response = controller.listTemplates();

        assertEquals(200, response.getCode());
        assertEquals("GOVERNMENT_STANDARD", response.getData().getFirst().getTemplateCode());
        verify(organizationAdminService).listTemplates();
    }

    @Test
    void relationWorkbenchShouldUseSessionTenantForTenantAdmin() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        OrgRelationWorkbenchView workbench = new OrgRelationWorkbenchView();
        workbench.setTotalTasks(3);
        when(organizationAdminService.relationWorkbench("tenant-a")).thenReturn(workbench);

        ApiResponse<OrgRelationWorkbenchView> response = controller.relationWorkbench(null);

        assertEquals(200, response.getCode());
        assertEquals(3, response.getData().getTotalTasks());
        verify(organizationAdminService).relationWorkbench("tenant-a");
    }

    @Test
    void resolveRelationRouteShouldUseSessionTenantForTenantAdmin() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        OrgRelationRouteResolveView view = new OrgRelationRouteResolveView();
        view.setMatched(true);
        view.setPrimaryTargetOrgUnitCode("SERVICE_CENTER");
        when(organizationAdminService.resolveRelationRoute("tenant-a", 12L, "SERVICE")).thenReturn(view);

        ApiResponse<OrgRelationRouteResolveView> response = controller.resolveRelationRoute(null, 12L, "SERVICE");

        assertEquals(200, response.getCode());
        assertEquals("SERVICE_CENTER", response.getData().getPrimaryTargetOrgUnitCode());
        verify(organizationAdminService).resolveRelationRoute("tenant-a", 12L, "SERVICE");
    }

    @Test
    void listExecutionsShouldUseSessionTenantForTenantAdmin() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        OrgRelationExecutionView view = new OrgRelationExecutionView();
        view.setId(9L);
        view.setExecutionTitle("售后服务工单");
        when(orgRelationExecutionService.listExecutions("tenant-a", null, null)).thenReturn(List.of(view));

        ApiResponse<List<OrgRelationExecutionView>> response = controller.listExecutions(null, null, null);

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        verify(orgRelationExecutionService).listExecutions("tenant-a", null, null);
    }

    @Test
    void executionTimelineShouldUseSessionTenantForTenantAdmin() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        OrgRelationExecutionTimelineView view = new OrgRelationExecutionTimelineView();
        view.setId(1L);
        view.setEventSummary("创建执行项");
        when(orgRelationExecutionService.listExecutionTimeline(9L, "tenant-a", false)).thenReturn(List.of(view));

        ApiResponse<List<OrgRelationExecutionTimelineView>> response = controller.executionTimeline(9L);

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        verify(orgRelationExecutionService).listExecutionTimeline(9L, "tenant-a", false);
    }

    @Test
    void createExecutionShouldDelegateByAuditExecutor() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        OrgRelationExecutionCreateRequest request = new OrgRelationExecutionCreateRequest();
        request.setSourceOrgUnitId(12L);
        request.setRelationType("SERVICE");
        request.setExecutionTitle("创建服务执行项");
        OrgRelationExecutionView view = new OrgRelationExecutionView();
        view.setId(10L);
        view.setExecutionTitle("创建服务执行项");
        when(orgRelationExecutionService.createExecution(eq(request), eq("tenant-a"), eq(false), any(RbacUserSession.class)))
                .thenReturn(view);
        mockAuditExecute();

        ApiResponse<OrgRelationExecutionView> response = controller.createExecution(request, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals(10L, response.getData().getId());
        verify(auditOperationExecutor).execute(
                eq("ORG_RELATION_EXECUTION_CREATE"),
                eq("ORG_RELATION_EXECUTION"),
                eq(request),
                any(),
                any()
        );
    }

    @Test
    void updateExecutionShouldDelegateByAuditExecutor() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        OrgRelationExecutionUpdateRequest request = new OrgRelationExecutionUpdateRequest();
        request.setStatus("DONE");
        request.setResultSummary("处理完成");
        OrgRelationExecutionView view = new OrgRelationExecutionView();
        view.setId(11L);
        view.setStatus("DONE");
        when(orgRelationExecutionService.updateExecution(eq(11L), eq(request), eq("tenant-a"), eq(false), any(RbacUserSession.class))).thenReturn(view);
        mockAuditExecute();

        ApiResponse<OrgRelationExecutionView> response = controller.updateExecution(11L, request, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals("DONE", response.getData().getStatus());
        verify(orgRelationExecutionService).updateExecution(eq(11L), eq(request), eq("tenant-a"), eq(false), any(RbacUserSession.class));
        verify(auditOperationExecutor).execute(
                eq("ORG_RELATION_EXECUTION_UPDATE"),
                eq("ORG_RELATION_EXECUTION"),
                eq(request),
                any(),
                any()
        );
    }

    @Test
    void applyTemplateShouldDelegateByAuditExecutor() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        OrgTemplateApplyRequest request = new OrgTemplateApplyRequest();
        request.setTemplateCode("CHAIN_STORE_STANDARD");
        when(organizationAdminService.applyTemplate(request, "tenant-a", false))
                .thenReturn("已初始化模板“连锁门店模板”");
        mockAuditExecute();

        ApiResponse<String> response = controller.applyTemplate(request, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals("已初始化模板“连锁门店模板”", response.getData());
        verify(organizationAdminService).applyTemplate(request, "tenant-a", false);
        verify(auditOperationExecutor).execute(
                eq("ORG_TEMPLATE_APPLY"),
                eq("ORG_TEMPLATE"),
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
