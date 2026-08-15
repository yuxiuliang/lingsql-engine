package com.lingsql.web;

import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.common.result.PageResult;
import com.lingsql.rbac.model.DataScopeBatchPreviewView;
import com.lingsql.rbac.model.DataScopePreviewView;
import com.lingsql.rbac.model.DataScopeRule;
import com.lingsql.rbac.model.RbacDataScopeType;
import com.lingsql.rbac.model.UserCreateRequest;
import com.lingsql.rbac.model.UserDataScopePreviewRequest;
import com.lingsql.rbac.model.UserScopeReleaseCreateRequest;
import com.lingsql.rbac.model.UserScopeReleaseRollbackRequest;
import com.lingsql.rbac.model.UserScopeReleaseReviewRequest;
import com.lingsql.rbac.model.UserScopeReleaseView;
import com.lingsql.rbac.model.UserView;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.service.DataScopePreviewService;
import com.lingsql.rbac.service.UserAdminService;
import com.lingsql.rbac.service.UserScopeReleaseService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAdminControllerTest {

    private UserAdminService userAdminService;
    private DataScopePreviewService dataScopePreviewService;
    private UserScopeReleaseService userScopeReleaseService;
    private AuditOperationExecutor auditOperationExecutor;
    private UserAdminController controller;

    @BeforeEach
    void setUp() {
        userAdminService = Mockito.mock(UserAdminService.class);
        dataScopePreviewService = Mockito.mock(DataScopePreviewService.class);
        userScopeReleaseService = Mockito.mock(UserScopeReleaseService.class);
        auditOperationExecutor = Mockito.mock(AuditOperationExecutor.class);
        controller = new UserAdminController(userAdminService, dataScopePreviewService, userScopeReleaseService, auditOperationExecutor);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listUsersShouldUseSessionTenantForTenantAdmin() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        UserView view = new UserView();
        view.setId(1L);
        view.setUsername("alice");
        DataScopeRule rule = new DataScopeRule();
        rule.setScopeType("INHERIT");
        view.setDataScope(rule);
        when(userAdminService.listUsers("tenant-a", "ali", 1, 10))
                .thenReturn(new PageResult<>(List.of(view), 1, 1, 10));

        ApiResponse<PageResult<UserView>> response = controller.listUsers(null, "ali", 1, 10);

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().getList().size());
        assertEquals("INHERIT", response.getData().getList().get(0).getDataScope().getScopeType());
        verify(userAdminService, times(1)).listUsers("tenant-a", "ali", 1, 10);
    }

    @Test
    void createUserShouldDelegateDataScopeByAuditExecutor() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("bob");
        request.setPassword("abc12345");
        request.setNickname("鲍勃");
        DataScopeRule requestScope = new DataScopeRule();
        requestScope.setScopeType(RbacDataScopeType.ORG_SUBTREE);
        requestScope.setScopeColumn("org_code");
        request.setDataScope(requestScope);

        UserView view = new UserView();
        view.setId(2L);
        view.setUsername("bob");
        DataScopeRule responseScope = new DataScopeRule();
        responseScope.setScopeType(RbacDataScopeType.ORG_SUBTREE);
        responseScope.setScopeColumn("org_code");
        view.setDataScope(responseScope);

        when(userAdminService.createUser(request, "tenant-a", false)).thenReturn(view);
        mockAuditExecute();

        ApiResponse<UserView> response = controller.createUser(request, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals(RbacDataScopeType.ORG_SUBTREE, response.getData().getDataScope().getScopeType());
        verify(userAdminService, times(1)).createUser(request, "tenant-a", false);
        verify(auditOperationExecutor, times(1)).execute(
                eq("USER_CREATE"),
                eq("USER"),
                eq(request),
                any(),
                any()
        );
    }

    @Test
    void previewDataScopeShouldUseSessionTenantForTenantAdmin() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        UserDataScopePreviewRequest request = new UserDataScopePreviewRequest();
        DataScopePreviewView view = new DataScopePreviewView();
        view.setSummary("用户权限命中 3 个组织");
        when(dataScopePreviewService.previewUserScope(request, "tenant-a", false)).thenReturn(view);

        ApiResponse<DataScopePreviewView> response = controller.previewDataScope(request);

        assertEquals(200, response.getCode());
        assertEquals("用户权限命中 3 个组织", response.getData().getSummary());
        verify(dataScopePreviewService).previewUserScope(request, "tenant-a", false);
    }

    @Test
    void previewDataScopeBatchShouldUseSessionTenantForTenantAdmin() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        UserDataScopePreviewRequest request = new UserDataScopePreviewRequest();
        DataScopeBatchPreviewView view = new DataScopeBatchPreviewView();
        view.setTotalScenarios(2);
        when(dataScopePreviewService.previewUserScopeBatch(request, "tenant-a", false)).thenReturn(view);

        ApiResponse<DataScopeBatchPreviewView> response = controller.previewDataScopeBatch(request);

        assertEquals(200, response.getCode());
        assertEquals(2, response.getData().getTotalScenarios());
    }

    @Test
    void listScopeReleasesShouldUseSessionTenantForTenantAdmin() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        UserScopeReleaseView view = new UserScopeReleaseView();
        view.setId(1L);
        view.setUsername("alice");
        when(userScopeReleaseService.listRecords("tenant-a", null, "tenant-a", false)).thenReturn(List.of(view));

        ApiResponse<List<UserScopeReleaseView>> response = controller.listScopeReleases(null, null);

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        verify(userScopeReleaseService).listRecords("tenant-a", null, "tenant-a", false);
    }

    @Test
    void createScopeReleaseShouldDelegateByAuditExecutor() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        UserScopeReleaseCreateRequest request = new UserScopeReleaseCreateRequest();
        UserScopeReleaseView view = new UserScopeReleaseView();
        view.setId(2L);
        view.setUsername("alice");
        when(userScopeReleaseService.createRecord(eq(request), eq("tenant-a"), eq(false), any(RbacUserSession.class))).thenReturn(view);
        mockAuditExecute();

        ApiResponse<UserScopeReleaseView> response = controller.createScopeRelease(request, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals("alice", response.getData().getUsername());
    }

    @Test
    void reviewScopeReleaseShouldDelegateByAuditExecutor() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        UserScopeReleaseReviewRequest request = new UserScopeReleaseReviewRequest();
        request.setDecision("APPROVED");
        UserScopeReleaseView view = new UserScopeReleaseView();
        view.setId(3L);
        view.setReleaseStatus("APPROVED");
        when(userScopeReleaseService.reviewRecord(eq(3L), eq(request), eq("tenant-a"), eq(false), any(RbacUserSession.class))).thenReturn(view);
        mockAuditExecute();

        ApiResponse<UserScopeReleaseView> response = controller.reviewScopeRelease(3L, request, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals("APPROVED", response.getData().getReleaseStatus());
    }

    @Test
    void applyScopeReleaseShouldDelegateByAuditExecutor() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        UserScopeReleaseView view = new UserScopeReleaseView();
        view.setId(4L);
        view.setReleaseStatus("APPLIED");
        when(userScopeReleaseService.applyRecord(eq(4L), eq("tenant-a"), eq(false), any(RbacUserSession.class))).thenReturn(view);
        mockAuditExecute();

        ApiResponse<UserScopeReleaseView> response = controller.applyScopeRelease(4L, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals("APPLIED", response.getData().getReleaseStatus());
    }

    @Test
    void createRollbackScopeReleaseShouldDelegateByAuditExecutor() {
        bindSession("tenant-a", List.of("TENANT_ADMIN"));
        UserScopeReleaseRollbackRequest request = new UserScopeReleaseRollbackRequest();
        request.setChangeRemark("回滚用户权限");
        UserScopeReleaseView view = new UserScopeReleaseView();
        view.setId(5L);
        view.setReleaseTitle("回滚-用户数据权限发布单");
        when(userScopeReleaseService.createRollbackRecord(eq(5L), eq(request), eq("tenant-a"), eq(false), any(RbacUserSession.class)))
                .thenReturn(view);
        mockAuditExecute();

        ApiResponse<UserScopeReleaseView> response = controller.createRollbackScopeRelease(5L, request, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals("回滚-用户数据权限发布单", response.getData().getReleaseTitle());
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
