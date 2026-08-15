package com.lingsql.web;

import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.model.MenuAdminView;
import com.lingsql.rbac.model.MenuImpactPreviewRequest;
import com.lingsql.rbac.model.MenuImpactPreviewView;
import com.lingsql.rbac.model.MenuPreviewRequest;
import com.lingsql.rbac.model.MenuReleaseCreateRequest;
import com.lingsql.rbac.model.MenuReleaseRollbackRequest;
import com.lingsql.rbac.model.MenuReleaseReviewRequest;
import com.lingsql.rbac.model.MenuReleaseView;
import com.lingsql.rbac.model.MenuRuleUpdateRequest;
import com.lingsql.rbac.model.MenuView;
import com.lingsql.rbac.model.TenantMenuAdminView;
import com.lingsql.rbac.model.TenantMenuRuleUpdateRequest;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.service.MenuAdminService;
import com.lingsql.rbac.service.MenuReleaseService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MenuAdminControllerTest {

    private MenuAdminService menuAdminService;
    private MenuReleaseService menuReleaseService;
    private AuditOperationExecutor auditOperationExecutor;
    private MenuAdminController controller;

    @BeforeEach
    void setUp() {
        menuAdminService = Mockito.mock(MenuAdminService.class);
        menuReleaseService = Mockito.mock(MenuReleaseService.class);
        auditOperationExecutor = Mockito.mock(AuditOperationExecutor.class);
        controller = new MenuAdminController(menuAdminService, menuReleaseService, auditOperationExecutor);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listMenusShouldRequireSysAdmin() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));

        ApiResponse<List<MenuAdminView>> response = controller.listMenus();

        assertEquals(403, response.getCode());
        verify(menuAdminService, never()).listGlobalMenus();
    }

    @Test
    void updateMenuRuleShouldDelegateByAuditExecutorForSysAdmin() {
        bindSession("system", List.of("SUPER_ADMIN"));
        MenuRuleUpdateRequest request = new MenuRuleUpdateRequest();
        request.setOrgTypeCodes(List.of("CHAIN_STORE"));
        MenuAdminView view = new MenuAdminView();
        view.setId(9L);
        view.setMenuCode("MENU_CHAIN");
        when(menuAdminService.updateGlobalMenuRule(9L, request)).thenReturn(view);
        mockAuditExecute();

        ApiResponse<MenuAdminView> response = controller.updateMenuRule(9L, request, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals("MENU_CHAIN", response.getData().getMenuCode());
        verify(auditOperationExecutor).execute(
                eq("MENU_RULE_UPDATE"),
                eq("MENU"),
                eq(request),
                any(),
                any()
        );
    }

    @Test
    void previewMenusShouldUseSessionTenantForTenantAdmin() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        MenuPreviewRequest request = new MenuPreviewRequest();
        request.setPermissionCodes(List.of("MENU:CHAIN"));
        when(menuAdminService.previewMenus(request, "tenant-a", false)).thenReturn(List.of(new MenuView()));

        ApiResponse<List<MenuView>> response = controller.previewMenus(request);

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        verify(menuAdminService).previewMenus(request, "tenant-a", false);
    }

    @Test
    void previewMenuImpactShouldUseSessionTenantForTenantAdmin() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        MenuImpactPreviewRequest request = new MenuImpactPreviewRequest();
        request.setMenuCode("MENU_CHAIN");
        MenuImpactPreviewView view = new MenuImpactPreviewView();
        view.setMenuCode("MENU_CHAIN");
        view.setTargetMatched(true);
        when(menuAdminService.previewMenuImpact(request, "tenant-a", false)).thenReturn(view);
        when(auditOperationExecutor.execute(anyString(), anyString(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return ApiResponse.success(supplier.get());
                });

        ApiResponse<MenuImpactPreviewView> response = controller.previewMenuImpact(request, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals("MENU_CHAIN", response.getData().getMenuCode());
        verify(menuAdminService).previewMenuImpact(request, "tenant-a", false);
        verify(auditOperationExecutor).execute(
                eq("TENANT_MENU_RULE_IMPACT_PREVIEW"),
                eq("TENANT_MENU"),
                eq(request),
                any(),
                any(),
                any()
        );
    }

    @Test
    void listTenantMenusShouldUseSessionTenantForTenantAdmin() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        TenantMenuAdminView view = new TenantMenuAdminView();
        view.setMenuCode("MENU_CHAIN");
        when(menuAdminService.listTenantMenus(null, "tenant-a", false)).thenReturn(List.of(view));

        ApiResponse<List<TenantMenuAdminView>> response = controller.listTenantMenus(null);

        assertEquals(200, response.getCode());
        assertEquals("MENU_CHAIN", response.getData().getFirst().getMenuCode());
        verify(menuAdminService).listTenantMenus(null, "tenant-a", false);
    }

    @Test
    void updateTenantMenuRuleShouldDelegateByAuditExecutor() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        TenantMenuRuleUpdateRequest request = new TenantMenuRuleUpdateRequest();
        request.setVisible(0);
        TenantMenuAdminView view = new TenantMenuAdminView();
        view.setMenuCode("MENU_CHAIN");
        when(menuAdminService.upsertTenantMenuRule("MENU_CHAIN", request, "tenant-a", false)).thenReturn(view);
        mockAuditExecute();

        ApiResponse<TenantMenuAdminView> response = controller.updateTenantMenuRule("MENU_CHAIN", request, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals("MENU_CHAIN", response.getData().getMenuCode());
        verify(auditOperationExecutor).execute(
                eq("TENANT_MENU_RULE_UPDATE"),
                eq("TENANT_MENU"),
                eq(request),
                any(),
                any()
        );
    }

    @Test
    void clearTenantMenuRuleShouldDelegateByAuditExecutor() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        mockAuditExecuteVoid();

        ApiResponse<String> response = controller.clearTenantMenuRule("MENU_CHAIN", null, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals("租户菜单覆盖已清除", response.getData());
        verify(auditOperationExecutor).executeVoid(
                eq("TENANT_MENU_RULE_CLEAR"),
                eq("TENANT_MENU"),
                eq(null),
                any(),
                any(),
                eq("租户菜单覆盖已清除")
        );
    }

    @Test
    void listReleasesShouldUseSessionTenantForTenantAdmin() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        MenuReleaseView view = new MenuReleaseView();
        view.setId(1L);
        view.setMenuCode("MENU_CHAIN");
        when(menuReleaseService.listRecords(null, null, "tenant-a", false)).thenReturn(List.of(view));

        ApiResponse<List<MenuReleaseView>> response = controller.listReleases(null, null);

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        verify(menuReleaseService).listRecords(null, null, "tenant-a", false);
    }

    @Test
    void createReleaseShouldDelegateByAuditExecutor() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        MenuReleaseCreateRequest request = new MenuReleaseCreateRequest();
        MenuReleaseView view = new MenuReleaseView();
        view.setId(2L);
        view.setMenuCode("MENU_CHAIN");
        when(menuReleaseService.createRecord(eq(request), eq("tenant-a"), eq(false), any(RbacUserSession.class))).thenReturn(view);
        mockAuditExecute();

        ApiResponse<MenuReleaseView> response = controller.createRelease(request, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals("MENU_CHAIN", response.getData().getMenuCode());
        verify(auditOperationExecutor).execute(
                eq("MENU_RELEASE_CREATE"),
                eq("MENU_RELEASE"),
                eq(request),
                any(),
                any()
        );
    }

    @Test
    void reviewReleaseShouldDelegateByAuditExecutor() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        MenuReleaseReviewRequest request = new MenuReleaseReviewRequest();
        request.setDecision("APPROVED");
        MenuReleaseView view = new MenuReleaseView();
        view.setId(3L);
        view.setReleaseStatus("APPROVED");
        when(menuReleaseService.reviewRecord(eq(3L), eq(request), eq("tenant-a"), eq(false), any(RbacUserSession.class))).thenReturn(view);
        mockAuditExecute();

        ApiResponse<MenuReleaseView> response = controller.reviewRelease(3L, request, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals("APPROVED", response.getData().getReleaseStatus());
        verify(auditOperationExecutor).execute(
                eq("MENU_RELEASE_REVIEW"),
                eq("MENU_RELEASE"),
                eq(request),
                any(),
                any()
        );
    }

    @Test
    void applyReleaseShouldDelegateByAuditExecutor() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        MenuReleaseView view = new MenuReleaseView();
        view.setId(4L);
        view.setReleaseStatus("APPLIED");
        when(menuReleaseService.applyRecord(eq(4L), eq("tenant-a"), eq(false), any(RbacUserSession.class))).thenReturn(view);
        mockAuditExecute();

        ApiResponse<MenuReleaseView> response = controller.applyRelease(4L, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals("APPLIED", response.getData().getReleaseStatus());
        verify(auditOperationExecutor).execute(
                eq("MENU_RELEASE_APPLY"),
                eq("MENU_RELEASE"),
                eq("id=4"),
                any(),
                any()
        );
    }

    @Test
    void createRollbackReleaseShouldDelegateByAuditExecutor() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        MenuReleaseRollbackRequest request = new MenuReleaseRollbackRequest();
        request.setChangeRemark("回滚验证");
        MenuReleaseView view = new MenuReleaseView();
        view.setId(5L);
        view.setReleaseTitle("回滚-菜单发布单");
        when(menuReleaseService.createRollbackRecord(eq(9L), eq(request), eq("tenant-a"), eq(false), any(RbacUserSession.class)))
                .thenReturn(view);
        mockAuditExecute();

        ApiResponse<MenuReleaseView> response = controller.createRollbackRelease(9L, request, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals(5L, response.getData().getId());
        verify(auditOperationExecutor).execute(
                eq("MENU_RELEASE_ROLLBACK_CREATE"),
                eq("MENU_RELEASE"),
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

    private void mockAuditExecuteVoid() {
        when(auditOperationExecutor.executeVoid(anyString(), anyString(), any(), any(), any(), anyString()))
                .thenAnswer(invocation -> {
                    Runnable runnable = invocation.getArgument(4);
                    runnable.run();
                    return ApiResponse.success(invocation.getArgument(5));
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
