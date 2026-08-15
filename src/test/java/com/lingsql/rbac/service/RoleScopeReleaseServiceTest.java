package com.lingsql.rbac.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.rbac.entity.RbacRole;
import com.lingsql.rbac.entity.RoleScopeReleaseRecord;
import com.lingsql.rbac.model.DataScopePreviewDiffView;
import com.lingsql.rbac.model.DataScopePreviewView;
import com.lingsql.rbac.model.DataScopeRule;
import com.lingsql.rbac.model.RoleDataScopePreviewRequest;
import com.lingsql.rbac.model.RoleScopeReleaseCreateRequest;
import com.lingsql.rbac.model.RoleScopeReleaseRollbackRequest;
import com.lingsql.rbac.model.RoleScopeReleaseReviewRequest;
import com.lingsql.rbac.model.RoleScopeReleaseView;
import com.lingsql.rbac.model.RoleUpdateRequest;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RoleScopeReleaseRecordRepository;
import com.lingsql.rbac.security.RbacUserSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

class RoleScopeReleaseServiceTest {

    @Test
    void createRecordShouldPersistDraftRelease() {
        RoleScopeReleaseRecordRepository repository = Mockito.mock(RoleScopeReleaseRecordRepository.class);
        RbacRoleRepository roleRepository = Mockito.mock(RbacRoleRepository.class);
        RoleAdminService roleAdminService = Mockito.mock(RoleAdminService.class);
        DataScopePreviewService previewService = Mockito.mock(DataScopePreviewService.class);
        RoleScopeReleaseService service = new RoleScopeReleaseService(
                repository,
                roleRepository,
                roleAdminService,
                previewService,
                new ObjectMapper()
        );

        RoleDataScopePreviewRequest previewRequest = new RoleDataScopePreviewRequest();
        previewRequest.setTenantId("tenant-a");
        previewRequest.setRoleId(9L);
        DataScopeRule targetRule = new DataScopeRule();
        targetRule.setScopeType("ORG_SUBTREE");
        previewRequest.setDataScope(targetRule);
        DataScopeRule baselineRule = new DataScopeRule();
        baselineRule.setScopeType("TENANT");
        previewRequest.setBaselineDataScope(baselineRule);

        DataScopePreviewDiffView diff = new DataScopePreviewDiffView();
        diff.setBaselineSummary("角色继承：本租户数据");
        diff.setTargetSummary("当前配置：直属组织及下级范围，命中 3 个组织");
        diff.setAddedOrgCount(2);
        diff.setRemovedOrgCount(0);
        DataScopePreviewView preview = new DataScopePreviewView();
        preview.setImpactSummary("当前角色变更会直接影响 12 个已绑定用户的数据可见范围。");
        preview.setMatchedOrgCount(3);
        preview.setAffectedUserCount(12);
        preview.setDiff(diff);

        RbacRole role = new RbacRole();
        role.setId(9L);
        role.setTenantId("tenant-a");
        role.setRoleCode("ROLE_GOV");
        role.setRoleName("政府角色");

        when(previewService.previewRoleScope(eq(previewRequest), eq("tenant-a"), eq(false))).thenReturn(preview);
        when(roleRepository.findById(9L)).thenReturn(Optional.of(role));
        AtomicLong idGenerator = new AtomicLong(1L);
        when(repository.save(any(RoleScopeReleaseRecord.class))).thenAnswer(invocation -> {
            RoleScopeReleaseRecord entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(idGenerator.getAndIncrement());
            }
            return entity;
        });

        RoleScopeReleaseCreateRequest request = new RoleScopeReleaseCreateRequest();
        request.setTenantId("tenant-a");
        request.setPreviewRequest(previewRequest);

        RbacUserSession session = new RbacUserSession();
        session.setUserId(100L);
        session.setTenantId("tenant-a");
        session.setUsername("admin");

        RoleScopeReleaseView view = service.createRecord(request, "tenant-a", false, session);

        assertEquals("DRAFT", view.getReleaseStatus());
        assertEquals("ROLE_GOV", view.getRoleCode());
        assertEquals(12, view.getAffectedUserCount());
    }

    @Test
    void reviewRecordShouldRejectSelfReview() {
        RoleScopeReleaseRecordRepository repository = Mockito.mock(RoleScopeReleaseRecordRepository.class);
        RbacRoleRepository roleRepository = Mockito.mock(RbacRoleRepository.class);
        RoleAdminService roleAdminService = Mockito.mock(RoleAdminService.class);
        DataScopePreviewService previewService = Mockito.mock(DataScopePreviewService.class);
        RoleScopeReleaseService service = new RoleScopeReleaseService(
                repository,
                roleRepository,
                roleAdminService,
                previewService,
                new ObjectMapper()
        );

        RoleScopeReleaseRecord entity = new RoleScopeReleaseRecord();
        entity.setId(2L);
        entity.setTenantId("tenant-a");
        entity.setReleaseStatus("DRAFT");
        entity.setRequestedByUserId(200L);
        when(repository.findById(2L)).thenReturn(Optional.of(entity));

        RoleScopeReleaseReviewRequest request = new RoleScopeReleaseReviewRequest();
        request.setDecision("APPROVED");

        RbacUserSession session = new RbacUserSession();
        session.setUserId(200L);
        session.setTenantId("tenant-a");

        BizException ex = assertThrows(BizException.class,
                () -> service.reviewRecord(2L, request, "tenant-a", false, session));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void applyRecordShouldUpdateRoleDataScope() throws Exception {
        RoleScopeReleaseRecordRepository repository = Mockito.mock(RoleScopeReleaseRecordRepository.class);
        RbacRoleRepository roleRepository = Mockito.mock(RbacRoleRepository.class);
        RoleAdminService roleAdminService = Mockito.mock(RoleAdminService.class);
        DataScopePreviewService previewService = Mockito.mock(DataScopePreviewService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RoleScopeReleaseService service = new RoleScopeReleaseService(
                repository,
                roleRepository,
                roleAdminService,
                previewService,
                objectMapper
        );

        RoleDataScopePreviewRequest previewRequest = new RoleDataScopePreviewRequest();
        previewRequest.setTenantId("tenant-a");
        previewRequest.setRoleId(9L);
        DataScopeRule targetRule = new DataScopeRule();
        targetRule.setScopeType("ORG_SUBTREE");
        previewRequest.setDataScope(targetRule);
        DataScopeRule baselineRule = new DataScopeRule();
        baselineRule.setScopeType("TENANT");
        previewRequest.setBaselineDataScope(baselineRule);

        RoleScopeReleaseRecord entity = new RoleScopeReleaseRecord();
        entity.setId(3L);
        entity.setTenantId("tenant-a");
        entity.setReleaseStatus("APPROVED");
        entity.setRequestedByUserId(300L);
        entity.setPreviewRequestJson(objectMapper.writeValueAsString(previewRequest));
        when(repository.findById(3L)).thenReturn(Optional.of(entity));
        when(repository.save(any(RoleScopeReleaseRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleAdminService.resolveCurrentRoleDataScope(9L)).thenReturn(baselineRule);

        RbacUserSession session = new RbacUserSession();
        session.setUserId(301L);
        session.setTenantId("tenant-a");
        session.setUsername("operator");

        RoleScopeReleaseView view = service.applyRecord(3L, "tenant-a", false, session);

        assertEquals("APPLIED", view.getReleaseStatus());
        ArgumentCaptor<RoleUpdateRequest> captor = ArgumentCaptor.forClass(RoleUpdateRequest.class);
        verify(roleAdminService).updateRole(eq(9L), captor.capture(), eq("tenant-a"), eq(false));
        assertEquals("ORG_SUBTREE", captor.getValue().getDataScope().getScopeType());
    }

    @Test
    void applyRecordShouldRejectWhenBaselineDrifts() throws Exception {
        RoleScopeReleaseRecordRepository repository = Mockito.mock(RoleScopeReleaseRecordRepository.class);
        RbacRoleRepository roleRepository = Mockito.mock(RbacRoleRepository.class);
        RoleAdminService roleAdminService = Mockito.mock(RoleAdminService.class);
        DataScopePreviewService previewService = Mockito.mock(DataScopePreviewService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RoleScopeReleaseService service = new RoleScopeReleaseService(
                repository,
                roleRepository,
                roleAdminService,
                previewService,
                objectMapper
        );

        RoleDataScopePreviewRequest previewRequest = new RoleDataScopePreviewRequest();
        previewRequest.setTenantId("tenant-a");
        previewRequest.setRoleId(9L);
        DataScopeRule targetRule = new DataScopeRule();
        targetRule.setScopeType("ORG_SUBTREE");
        previewRequest.setDataScope(targetRule);
        DataScopeRule baselineRule = new DataScopeRule();
        baselineRule.setScopeType("TENANT");
        previewRequest.setBaselineDataScope(baselineRule);

        RoleScopeReleaseRecord entity = new RoleScopeReleaseRecord();
        entity.setId(4L);
        entity.setTenantId("tenant-a");
        entity.setReleaseStatus("APPROVED");
        entity.setRequestedByUserId(400L);
        entity.setPreviewRequestJson(objectMapper.writeValueAsString(previewRequest));
        when(repository.findById(4L)).thenReturn(Optional.of(entity));
        DataScopeRule driftedRule = new DataScopeRule();
        driftedRule.setScopeType("RELATION");
        when(roleAdminService.resolveCurrentRoleDataScope(9L)).thenReturn(driftedRule);

        RbacUserSession session = new RbacUserSession();
        session.setUserId(401L);
        session.setTenantId("tenant-a");

        BizException ex = assertThrows(BizException.class,
                () -> service.applyRecord(4L, "tenant-a", false, session));

        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void createRollbackRecordShouldCreateDraftRelease() throws Exception {
        RoleScopeReleaseRecordRepository repository = Mockito.mock(RoleScopeReleaseRecordRepository.class);
        RbacRoleRepository roleRepository = Mockito.mock(RbacRoleRepository.class);
        RoleAdminService roleAdminService = Mockito.mock(RoleAdminService.class);
        DataScopePreviewService previewService = Mockito.mock(DataScopePreviewService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RoleScopeReleaseService service = new RoleScopeReleaseService(
                repository,
                roleRepository,
                roleAdminService,
                previewService,
                objectMapper
        );

        RoleDataScopePreviewRequest sourcePreviewRequest = new RoleDataScopePreviewRequest();
        sourcePreviewRequest.setTenantId("tenant-a");
        sourcePreviewRequest.setRoleId(9L);
        DataScopeRule targetRule = new DataScopeRule();
        targetRule.setScopeType("ORG_SUBTREE");
        sourcePreviewRequest.setDataScope(targetRule);
        DataScopeRule baselineRule = new DataScopeRule();
        baselineRule.setScopeType("TENANT");
        sourcePreviewRequest.setBaselineDataScope(baselineRule);

        RoleScopeReleaseRecord source = new RoleScopeReleaseRecord();
        source.setId(5L);
        source.setTenantId("tenant-a");
        source.setReleaseStatus("APPLIED");
        source.setRequestedByUserId(500L);
        source.setReleaseTitle("角色数据权限发布单");
        source.setPreviewRequestJson(objectMapper.writeValueAsString(sourcePreviewRequest));
        when(repository.findById(5L)).thenReturn(Optional.of(source));
        when(roleAdminService.resolveCurrentRoleDataScope(9L)).thenReturn(targetRule);

        DataScopePreviewDiffView diff = new DataScopePreviewDiffView();
        diff.setBaselineSummary("当前配置：直属组织及下级范围，命中 3 个组织");
        diff.setTargetSummary("当前配置：本租户数据，不额外限制组织范围");
        DataScopePreviewView preview = new DataScopePreviewView();
        preview.setImpactSummary("当前角色变更会直接影响 12 个已绑定用户的数据可见范围。");
        preview.setMatchedOrgCount(1);
        preview.setAffectedUserCount(12);
        preview.setDiff(diff);
        when(previewService.previewRoleScope(any(RoleDataScopePreviewRequest.class), eq("tenant-a"), eq(false))).thenReturn(preview);

        RbacRole role = new RbacRole();
        role.setId(9L);
        role.setTenantId("tenant-a");
        role.setRoleCode("ROLE_GOV");
        role.setRoleName("政府角色");
        when(roleRepository.findById(9L)).thenReturn(Optional.of(role));
        AtomicLong idGenerator = new AtomicLong(10L);
        when(repository.save(any(RoleScopeReleaseRecord.class))).thenAnswer(invocation -> {
            RoleScopeReleaseRecord entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(idGenerator.getAndIncrement());
            }
            return entity;
        });

        RoleScopeReleaseRollbackRequest request = new RoleScopeReleaseRollbackRequest();
        request.setChangeRemark("回滚到本租户");
        RbacUserSession session = new RbacUserSession();
        session.setUserId(501L);
        session.setTenantId("tenant-a");
        session.setUsername("reviewer");

        RoleScopeReleaseView view = service.createRollbackRecord(5L, request, "tenant-a", false, session);

        assertEquals("DRAFT", view.getReleaseStatus());
        assertEquals("ROLE_GOV", view.getRoleCode());
    }
}
