package com.lingsql.rbac.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.rbac.entity.MenuReleaseRecord;
import com.lingsql.rbac.model.MenuImpactPreviewRequest;
import com.lingsql.rbac.model.MenuImpactPreviewView;
import com.lingsql.rbac.model.MenuPreviewRequest;
import com.lingsql.rbac.model.MenuReleaseCreateRequest;
import com.lingsql.rbac.model.MenuReleaseRollbackRequest;
import com.lingsql.rbac.model.MenuReleaseReviewRequest;
import com.lingsql.rbac.model.MenuReleaseView;
import com.lingsql.rbac.model.MenuRuleSnapshotView;
import com.lingsql.rbac.model.MenuTenantOverrideSnapshotView;
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
        preview.setBaselineRuleSnapshot(new MenuRuleSnapshotView());
        preview.setTargetRuleSummary("组织类型 CHAIN_STORE");
        MenuRuleSnapshotView targetSnapshot = new MenuRuleSnapshotView();
        targetSnapshot.setOrgTypeCodes(List.of("CHAIN_STORE"));
        preview.setTargetRuleSnapshot(targetSnapshot);
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
        MenuImpactPreviewView impactSnapshot = new MenuImpactPreviewView();
        impactSnapshot.setBaselineRuleSnapshot(new MenuRuleSnapshotView());

        MenuReleaseRecord entity = new MenuReleaseRecord();
        entity.setId(3L);
        entity.setTenantId("tenant-a");
        entity.setReleaseType("TENANT_RULE");
        entity.setReleaseStatus("APPROVED");
        entity.setImpactRequestJson(objectMapper.writeValueAsString(impactRequest));
        entity.setImpactSnapshotJson(objectMapper.writeValueAsString(impactSnapshot));
        when(repository.findById(3L)).thenReturn(Optional.of(entity));
        when(repository.save(any(MenuReleaseRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(menuAdminService.resolveCurrentRuleSnapshot("tenant-a", null, "MENU_CHAIN", true))
                .thenReturn(new MenuRuleSnapshotView());

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
        MenuImpactPreviewView impactSnapshot = new MenuImpactPreviewView();
        impactSnapshot.setBaselineRuleSnapshot(new MenuRuleSnapshotView());

        MenuReleaseRecord entity = new MenuReleaseRecord();
        entity.setId(6L);
        entity.setTenantId("tenant-a");
        entity.setReleaseType("TENANT_RULE");
        entity.setReleaseStatus("APPROVED");
        entity.setRequestedByUserId(600L);
        entity.setImpactRequestJson(objectMapper.writeValueAsString(impactRequest));
        entity.setImpactSnapshotJson(objectMapper.writeValueAsString(impactSnapshot));
        when(repository.findById(6L)).thenReturn(Optional.of(entity));

        RbacUserSession session = new RbacUserSession();
        session.setUserId(600L);
        session.setTenantId("tenant-a");

        BizException ex = assertThrows(BizException.class,
                () -> service.applyRecord(6L, "tenant-a", false, session));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void applyRecordShouldRejectWhenBaselineSnapshotDrifts() throws Exception {
        MenuReleaseRecordRepository repository = Mockito.mock(MenuReleaseRecordRepository.class);
        MenuAdminService menuAdminService = Mockito.mock(MenuAdminService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        MenuReleaseService service = new MenuReleaseService(repository, menuAdminService, objectMapper);

        MenuImpactPreviewRequest impactRequest = new MenuImpactPreviewRequest();
        impactRequest.setTenantId("tenant-a");
        impactRequest.setMenuCode("MENU_CHAIN");
        MenuRuleSnapshotView baselineSnapshot = new MenuRuleSnapshotView();
        baselineSnapshot.setVisible(1);
        MenuImpactPreviewView impactSnapshot = new MenuImpactPreviewView();
        impactSnapshot.setBaselineRuleSnapshot(baselineSnapshot);

        MenuReleaseRecord entity = new MenuReleaseRecord();
        entity.setId(7L);
        entity.setTenantId("tenant-a");
        entity.setReleaseType("TENANT_RULE");
        entity.setReleaseStatus("APPROVED");
        entity.setImpactRequestJson(objectMapper.writeValueAsString(impactRequest));
        entity.setImpactSnapshotJson(objectMapper.writeValueAsString(impactSnapshot));
        when(repository.findById(7L)).thenReturn(Optional.of(entity));

        MenuRuleSnapshotView currentSnapshot = new MenuRuleSnapshotView();
        currentSnapshot.setVisible(0);
        when(menuAdminService.resolveCurrentRuleSnapshot("tenant-a", null, "MENU_CHAIN", true))
                .thenReturn(currentSnapshot);

        RbacUserSession session = new RbacUserSession();
        session.setUserId(701L);
        session.setTenantId("tenant-a");

        BizException ex = assertThrows(BizException.class,
                () -> service.applyRecord(7L, "tenant-a", false, session));

        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void createRollbackRecordShouldCreateDraftReleaseFromAppliedSnapshot() throws Exception {
        MenuReleaseRecordRepository repository = Mockito.mock(MenuReleaseRecordRepository.class);
        MenuAdminService menuAdminService = Mockito.mock(MenuAdminService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        MenuReleaseService service = new MenuReleaseService(repository, menuAdminService, objectMapper);

        MenuImpactPreviewRequest sourceImpactRequest = new MenuImpactPreviewRequest();
        sourceImpactRequest.setTenantId("tenant-a");
        sourceImpactRequest.setMenuCode("MENU_CHAIN");
        MenuPreviewRequest previewContext = new MenuPreviewRequest();
        previewContext.setPermissionCodes(List.of("MENU:CHAIN"));
        sourceImpactRequest.setPreviewContext(previewContext);

        MenuTenantOverrideSnapshotView targetOverrideSnapshot = new MenuTenantOverrideSnapshotView();
        targetOverrideSnapshot.setOverrideActive(true);
        targetOverrideSnapshot.setVisible(0);
        MenuTenantOverrideSnapshotView baselineOverrideSnapshot = new MenuTenantOverrideSnapshotView();
        baselineOverrideSnapshot.setOverrideActive(false);
        MenuRuleSnapshotView targetRuleSnapshot = new MenuRuleSnapshotView();
        targetRuleSnapshot.setVisible(0);
        MenuRuleSnapshotView baselineRuleSnapshot = new MenuRuleSnapshotView();
        baselineRuleSnapshot.setVisible(1);

        MenuImpactPreviewView sourceImpactSnapshot = new MenuImpactPreviewView();
        sourceImpactSnapshot.setTenantId("tenant-a");
        sourceImpactSnapshot.setMenuCode("MENU_CHAIN");
        sourceImpactSnapshot.setMenuName("连锁菜单");
        sourceImpactSnapshot.setTenantOverrideMode(true);
        sourceImpactSnapshot.setBaselineRuleSnapshot(baselineRuleSnapshot);
        sourceImpactSnapshot.setTargetRuleSnapshot(targetRuleSnapshot);
        sourceImpactSnapshot.setBaselineOverrideSnapshot(baselineOverrideSnapshot);
        sourceImpactSnapshot.setTargetOverrideSnapshot(targetOverrideSnapshot);

        MenuReleaseRecord source = new MenuReleaseRecord();
        source.setId(8L);
        source.setTenantId("tenant-a");
        source.setReleaseType("TENANT_RULE");
        source.setReleaseTitle("租户菜单发布单");
        source.setReleaseStatus("APPLIED");
        source.setImpactRequestJson(objectMapper.writeValueAsString(sourceImpactRequest));
        source.setImpactSnapshotJson(objectMapper.writeValueAsString(sourceImpactSnapshot));
        when(repository.findById(8L)).thenReturn(Optional.of(source));

        when(menuAdminService.resolveCurrentRuleSnapshot("tenant-a", null, "MENU_CHAIN", true))
                .thenReturn(targetRuleSnapshot);
        when(menuAdminService.resolveCurrentOverrideSnapshot("tenant-a", "MENU_CHAIN"))
                .thenReturn(targetOverrideSnapshot);

        MenuImpactPreviewView rollbackPreview = new MenuImpactPreviewView();
        rollbackPreview.setTenantId("tenant-a");
        rollbackPreview.setMenuCode("MENU_CHAIN");
        rollbackPreview.setMenuName("连锁菜单");
        rollbackPreview.setTenantOverrideMode(true);
        rollbackPreview.setBaselineRuleSummary("隐藏");
        rollbackPreview.setTargetRuleSummary("显示");
        rollbackPreview.setBaselineRuleSnapshot(targetRuleSnapshot);
        rollbackPreview.setTargetRuleSnapshot(baselineRuleSnapshot);
        rollbackPreview.setBaselineOverrideSnapshot(targetOverrideSnapshot);
        rollbackPreview.setTargetOverrideSnapshot(baselineOverrideSnapshot);
        when(menuAdminService.previewMenuImpact(any(MenuImpactPreviewRequest.class), eq("tenant-a"), eq(false)))
                .thenReturn(rollbackPreview);

        AtomicLong idGenerator = new AtomicLong(100L);
        when(repository.save(any(MenuReleaseRecord.class))).thenAnswer(invocation -> {
            MenuReleaseRecord record = invocation.getArgument(0);
            if (record.getId() == null) {
                record.setId(idGenerator.getAndIncrement());
            }
            return record;
        });

        MenuReleaseRollbackRequest rollbackRequest = new MenuReleaseRollbackRequest();
        rollbackRequest.setChangeRemark("回滚到发布前");

        RbacUserSession session = new RbacUserSession();
        session.setUserId(801L);
        session.setTenantId("tenant-a");
        session.setUsername("rollback.user");

        MenuReleaseView view = service.createRollbackRecord(8L, rollbackRequest, "tenant-a", false, session);

        assertEquals("DRAFT", view.getReleaseStatus());
        assertEquals("MENU_CHAIN", view.getMenuCode());
        verify(menuAdminService).resolveCurrentRuleSnapshot("tenant-a", null, "MENU_CHAIN", true);
        verify(menuAdminService).resolveCurrentOverrideSnapshot("tenant-a", "MENU_CHAIN");
    }
}
