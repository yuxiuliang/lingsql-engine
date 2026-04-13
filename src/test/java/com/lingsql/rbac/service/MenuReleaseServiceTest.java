package com.lingsql.rbac.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.rbac.entity.MenuReleaseRecord;
import com.lingsql.rbac.model.MenuImpactPreviewRequest;
import com.lingsql.rbac.model.MenuImpactPreviewView;
import com.lingsql.rbac.model.MenuReleaseCreateRequest;
import com.lingsql.rbac.model.MenuReleaseReviewRequest;
import com.lingsql.rbac.model.MenuReleaseView;
import com.lingsql.rbac.model.MenuRuleUpdateRequest;
import com.lingsql.rbac.model.TenantMenuRuleUpdateRequest;
import com.lingsql.rbac.repository.MenuReleaseRecordRepository;
import com.lingsql.rbac.security.RbacUserSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MenuReleaseServiceTest {

    @Test
    void createRecordShouldPersistDraftReleaseWithComputedSnapshot() {
        MenuReleaseRecordRepository repository = Mockito.mock(MenuReleaseRecordRepository.class);
        MenuAdminService menuAdminService = Mockito.mock(MenuAdminService.class);
        MenuReleaseService service = new MenuReleaseService(repository, menuAdminService, new ObjectMapper());

        MenuImpactPreviewRequest impactRequest = new MenuImpactPreviewRequest();
        impactRequest.setTenantId("tenant-a");
        impactRequest.setMenuId(9L);
        MenuRuleUpdateRequest globalRule = new MenuRuleUpdateRequest();
        globalRule.setOrgTypeCodes(List.of("CHAIN_STORE"));
        impactRequest.setGlobalRule(globalRule);

        MenuImpactPreviewView preview = new MenuImpactPreviewView();
        preview.setTenantId("tenant-a");
        preview.setMenuCode("MENU_CHAIN");
        preview.setMenuName("连锁菜单");
        preview.setTenantOverrideMode(false);
        preview.setBaselineRuleSummary("未配置额外规则");
        preview.setTargetRuleSummary("组织类型 CHAIN_STORE");
        preview.setImpactSummary("基线菜单 10 项，目标菜单 11 项，新增 1 项，移除 0 项，当前样本上下文下目标菜单可见。");
        preview.setChangedDimensions(List.of("组织类型"));
        preview.setAddedMenuCount(1);
        preview.setRemovedMenuCount(0);
        when(menuAdminService.previewMenuImpact(eq(impactRequest), eq("tenant-a"), eq(false))).thenReturn(preview);

        AtomicLong idGenerator = new AtomicLong(1L);
        when(repository.save(any(MenuReleaseRecord.class))).thenAnswer(invocation -> {
            MenuReleaseRecord record = invocation.getArgument(0);
            if (record.getId() == null) {
                record.setId(idGenerator.getAndIncrement());
            }
            return record;
        });

        MenuReleaseCreateRequest request = new MenuReleaseCreateRequest();
        request.setTenantId("tenant-a");
        request.setImpactRequest(impactRequest);

        RbacUserSession session = new RbacUserSession();
        session.setUserId(100L);
        session.setTenantId("tenant-a");
        session.setUsername("admin");
        session.setNickname("管理员");

        MenuReleaseView view = service.createRecord(request, "tenant-a", false, session);

        assertEquals("DRAFT", view.getReleaseStatus());
        assertEquals("MENU_CHAIN", view.getMenuCode());
        assertEquals(List.of("组织类型"), view.getChangedDimensions());
        verify(menuAdminService).previewMenuImpact(impactRequest, "tenant-a", false);
    }

    @Test
    void reviewRecordShouldApproveDraftRelease() {
        MenuReleaseRecordRepository repository = Mockito.mock(MenuReleaseRecordRepository.class);
        MenuAdminService menuAdminService = Mockito.mock(MenuAdminService.class);
        MenuReleaseService service = new MenuReleaseService(repository, menuAdminService, new ObjectMapper());

        MenuReleaseRecord entity = new MenuReleaseRecord();
        entity.setId(2L);
        entity.setTenantId("tenant-a");
        entity.setMenuCode("MENU_CHAIN");
        entity.setMenuName("连锁菜单");
        entity.setReleaseStatus("DRAFT");
        entity.setChangedDimensionsJson("[\"组织类型\"]");
        entity.setCreateTime(LocalDateTime.now());
        when(repository.findById(2L)).thenReturn(Optional.of(entity));
        when(repository.save(any(MenuReleaseRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MenuReleaseReviewRequest request = new MenuReleaseReviewRequest();
        request.setDecision("APPROVED");
        request.setReviewComment("影响分析通过");

        RbacUserSession session = new RbacUserSession();
        session.setUserId(200L);
        session.setTenantId("tenant-a");
        session.setUsername("reviewer");

        MenuReleaseView view = service.reviewRecord(2L, request, "tenant-a", false, session);

        assertEquals("APPROVED", view.getReleaseStatus());
        assertEquals("影响分析通过", view.getReviewComment());
    }

    @Test
    void applyRecordShouldInvokeTenantMenuUpdate() throws Exception {
        MenuReleaseRecordRepository repository = Mockito.mock(MenuReleaseRecordRepository.class);
        MenuAdminService menuAdminService = Mockito.mock(MenuAdminService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        MenuReleaseService service = new MenuReleaseService(repository, menuAdminService, objectMapper);

        MenuImpactPreviewRequest impactRequest = new MenuImpactPreviewRequest();
        impactRequest.setTenantId("tenant-a");
        impactRequest.setMenuCode("MENU_CHAIN");
        TenantMenuRuleUpdateRequest tenantRule = new TenantMenuRuleUpdateRequest();
        tenantRule.setTenantId("tenant-a");
        tenantRule.setVisible(0);
        impactRequest.setTenantRule(tenantRule);

        MenuReleaseRecord entity = new MenuReleaseRecord();
        entity.setId(3L);
        entity.setTenantId("tenant-a");
        entity.setReleaseType("TENANT_RULE");
        entity.setReleaseStatus("APPROVED");
        entity.setImpactRequestJson(objectMapper.writeValueAsString(impactRequest));
        when(repository.findById(3L)).thenReturn(Optional.of(entity));
        when(repository.save(any(MenuReleaseRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RbacUserSession session = new RbacUserSession();
        session.setUserId(300L);
        session.setTenantId("tenant-a");
        session.setUsername("applier");

        MenuReleaseView view = service.applyRecord(3L, "tenant-a", false, session);

        assertEquals("APPLIED", view.getReleaseStatus());
        verify(menuAdminService).upsertTenantMenuRule("MENU_CHAIN", tenantRule, "tenant-a", false);
    }

    @Test
    void applyRecordShouldRejectNonApprovedRelease() {
        MenuReleaseRecordRepository repository = Mockito.mock(MenuReleaseRecordRepository.class);
        MenuAdminService menuAdminService = Mockito.mock(MenuAdminService.class);
        MenuReleaseService service = new MenuReleaseService(repository, menuAdminService, new ObjectMapper());

        MenuReleaseRecord entity = new MenuReleaseRecord();
        entity.setId(4L);
        entity.setTenantId("tenant-a");
        entity.setReleaseStatus("DRAFT");
        when(repository.findById(4L)).thenReturn(Optional.of(entity));

        BizException ex = assertThrows(BizException.class,
                () -> service.applyRecord(4L, "tenant-a", false, new RbacUserSession()));

        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void reviewRecordShouldRejectSelfReview() {
        MenuReleaseRecordRepository repository = Mockito.mock(MenuReleaseRecordRepository.class);
        MenuAdminService menuAdminService = Mockito.mock(MenuAdminService.class);
        MenuReleaseService service = new MenuReleaseService(repository, menuAdminService, new ObjectMapper());

        MenuReleaseRecord entity = new MenuReleaseRecord();
        entity.setId(5L);
        entity.setTenantId("tenant-a");
        entity.setReleaseStatus("DRAFT");
        entity.setRequestedByUserId(500L);
        when(repository.findById(5L)).thenReturn(Optional.of(entity));

        MenuReleaseReviewRequest request = new MenuReleaseReviewRequest();
        request.setDecision("APPROVED");

        RbacUserSession session = new RbacUserSession();
        session.setUserId(500L);
        session.setTenantId("tenant-a");

        BizException ex = assertThrows(BizException.class,
                () -> service.reviewRecord(5L, request, "tenant-a", false, session));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void applyRecordShouldRejectSelfApply() throws Exception {
        MenuReleaseRecordRepository repository = Mockito.mock(MenuReleaseRecordRepository.class);
        MenuAdminService menuAdminService = Mockito.mock(MenuAdminService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        MenuReleaseService service = new MenuReleaseService(repository, menuAdminService, objectMapper);

        MenuImpactPreviewRequest impactRequest = new MenuImpactPreviewRequest();
        impactRequest.setTenantId("tenant-a");
        impactRequest.setMenuCode("MENU_CHAIN");

        MenuReleaseRecord entity = new MenuReleaseRecord();
        entity.setId(6L);
        entity.setTenantId("tenant-a");
        entity.setReleaseType("TENANT_RULE");
        entity.setReleaseStatus("APPROVED");
        entity.setRequestedByUserId(600L);
        entity.setImpactRequestJson(objectMapper.writeValueAsString(impactRequest));
        when(repository.findById(6L)).thenReturn(Optional.of(entity));

        RbacUserSession session = new RbacUserSession();
        session.setUserId(600L);
        session.setTenantId("tenant-a");

        BizException ex = assertThrows(BizException.class,
                () -> service.applyRecord(6L, "tenant-a", false, session));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }
}
