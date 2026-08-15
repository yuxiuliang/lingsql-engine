package com.lingsql.rbac.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.rbac.entity.RbacUser;
import com.lingsql.rbac.entity.UserScopeReleaseRecord;
import com.lingsql.rbac.model.DataScopePreviewDiffView;
import com.lingsql.rbac.model.DataScopePreviewView;
import com.lingsql.rbac.model.DataScopeRule;
import com.lingsql.rbac.model.UserDataScopePreviewRequest;
import com.lingsql.rbac.model.UserScopeReleaseCreateRequest;
import com.lingsql.rbac.model.UserScopeReleaseRollbackRequest;
import com.lingsql.rbac.model.UserScopeReleaseReviewRequest;
import com.lingsql.rbac.model.UserScopeReleaseView;
import com.lingsql.rbac.model.UserUpdateRequest;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.rbac.repository.UserScopeReleaseRecordRepository;
import com.lingsql.rbac.security.RbacUserSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserScopeReleaseServiceTest {

    @Test
    void createRecordShouldPersistDraftRelease() {
        UserScopeReleaseRecordRepository repository = Mockito.mock(UserScopeReleaseRecordRepository.class);
        RbacUserRepository userRepository = Mockito.mock(RbacUserRepository.class);
        UserAdminService userAdminService = Mockito.mock(UserAdminService.class);
        DataScopePreviewService previewService = Mockito.mock(DataScopePreviewService.class);
        UserScopeReleaseService service = new UserScopeReleaseService(
                repository,
                userRepository,
                userAdminService,
                previewService,
                new ObjectMapper()
        );

        UserDataScopePreviewRequest previewRequest = new UserDataScopePreviewRequest();
        previewRequest.setTenantId("tenant-a");
        previewRequest.setUserId(8L);
        DataScopeRule targetRule = new DataScopeRule();
        targetRule.setScopeType("RELATION");
        previewRequest.setDataScope(targetRule);
        DataScopeRule baselineRule = new DataScopeRule();
        baselineRule.setScopeType("INHERIT");
        previewRequest.setBaselineDataScope(baselineRule);
        previewRequest.setRoleIds(List.of(11L, 12L));
        previewRequest.setBaselineRoleIds(List.of(11L, 12L));
        previewRequest.setOrgUnitIds(List.of(21L));
        previewRequest.setBaselineOrgUnitIds(List.of(21L));

        DataScopePreviewDiffView diff = new DataScopePreviewDiffView();
        diff.setBaselineSummary("继承角色：本租户数据");
        diff.setTargetSummary("当前配置：关系目标组织范围，命中 2 个组织");
        diff.setAddedOrgCount(2);
        diff.setRemovedOrgCount(0);
        DataScopePreviewView preview = new DataScopePreviewView();
        preview.setDiff(diff);
        preview.setImpactSummary("当前用户变更会影响 1 个用户自身的数据可见范围，并涉及 2 个绑定角色的继承关系。");
        preview.setMatchedOrgCount(2);
        preview.setAffectedRoleCount(2);
        preview.setAffectedUserCount(1);

        RbacUser user = new RbacUser();
        user.setId(8L);
        user.setTenantId("tenant-a");
        user.setUsername("alice");
        user.setNickname("爱丽丝");

        when(previewService.previewUserScope(eq(previewRequest), eq("tenant-a"), eq(false))).thenReturn(preview);
        when(userRepository.findById(8L)).thenReturn(Optional.of(user));
        AtomicLong idGenerator = new AtomicLong(1L);
        when(repository.save(any(UserScopeReleaseRecord.class))).thenAnswer(invocation -> {
            UserScopeReleaseRecord entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(idGenerator.getAndIncrement());
            }
            return entity;
        });

        UserScopeReleaseCreateRequest request = new UserScopeReleaseCreateRequest();
        request.setTenantId("tenant-a");
        request.setPreviewRequest(previewRequest);

        RbacUserSession session = new RbacUserSession();
        session.setUserId(100L);
        session.setTenantId("tenant-a");
        session.setUsername("admin");

        UserScopeReleaseView view = service.createRecord(request, "tenant-a", false, session);

        assertEquals("DRAFT", view.getReleaseStatus());
        assertEquals("alice", view.getUsername());
        assertEquals(1, view.getAffectedUserCount());
    }

    @Test
    void reviewRecordShouldRejectSelfReview() {
        UserScopeReleaseRecordRepository repository = Mockito.mock(UserScopeReleaseRecordRepository.class);
        RbacUserRepository userRepository = Mockito.mock(RbacUserRepository.class);
        UserAdminService userAdminService = Mockito.mock(UserAdminService.class);
        DataScopePreviewService previewService = Mockito.mock(DataScopePreviewService.class);
        UserScopeReleaseService service = new UserScopeReleaseService(
                repository,
                userRepository,
                userAdminService,
                previewService,
                new ObjectMapper()
        );

        UserScopeReleaseRecord entity = new UserScopeReleaseRecord();
        entity.setId(2L);
        entity.setTenantId("tenant-a");
        entity.setReleaseStatus("DRAFT");
        entity.setRequestedByUserId(200L);
        when(repository.findById(2L)).thenReturn(Optional.of(entity));

        UserScopeReleaseReviewRequest request = new UserScopeReleaseReviewRequest();
        request.setDecision("APPROVED");

        RbacUserSession session = new RbacUserSession();
        session.setUserId(200L);
        session.setTenantId("tenant-a");

        BizException ex = assertThrows(BizException.class,
                () -> service.reviewRecord(2L, request, "tenant-a", false, session));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void applyRecordShouldUpdateUserDataScope() throws Exception {
        UserScopeReleaseRecordRepository repository = Mockito.mock(UserScopeReleaseRecordRepository.class);
        RbacUserRepository userRepository = Mockito.mock(RbacUserRepository.class);
        UserAdminService userAdminService = Mockito.mock(UserAdminService.class);
        DataScopePreviewService previewService = Mockito.mock(DataScopePreviewService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        UserScopeReleaseService service = new UserScopeReleaseService(
                repository,
                userRepository,
                userAdminService,
                previewService,
                objectMapper
        );

        UserDataScopePreviewRequest previewRequest = new UserDataScopePreviewRequest();
        previewRequest.setTenantId("tenant-a");
        previewRequest.setUserId(8L);
        DataScopeRule targetRule = new DataScopeRule();
        targetRule.setScopeType("RELATION");
        previewRequest.setDataScope(targetRule);
        DataScopeRule baselineRule = new DataScopeRule();
        baselineRule.setScopeType("INHERIT");
        previewRequest.setBaselineDataScope(baselineRule);
        previewRequest.setRoleIds(List.of(11L));
        previewRequest.setBaselineRoleIds(List.of(11L));
        previewRequest.setOrgUnitIds(List.of(21L));
        previewRequest.setBaselineOrgUnitIds(List.of(21L));

        UserScopeReleaseRecord entity = new UserScopeReleaseRecord();
        entity.setId(3L);
        entity.setTenantId("tenant-a");
        entity.setReleaseStatus("APPROVED");
        entity.setRequestedByUserId(300L);
        entity.setPreviewRequestJson(objectMapper.writeValueAsString(previewRequest));
        when(repository.findById(3L)).thenReturn(Optional.of(entity));
        when(repository.save(any(UserScopeReleaseRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userAdminService.resolveCurrentUserDataScope(8L)).thenReturn(baselineRule);
        when(userAdminService.resolveCurrentUserRoleIds(8L)).thenReturn(List.of(11L));
        when(userAdminService.resolveCurrentUserOrgUnitIds(8L)).thenReturn(List.of(21L));

        RbacUserSession session = new RbacUserSession();
        session.setUserId(301L);
        session.setTenantId("tenant-a");
        session.setUsername("operator");

        UserScopeReleaseView view = service.applyRecord(3L, "tenant-a", false, session);

        assertEquals("APPLIED", view.getReleaseStatus());
        ArgumentCaptor<UserUpdateRequest> captor = ArgumentCaptor.forClass(UserUpdateRequest.class);
        verify(userAdminService).updateUser(eq(8L), captor.capture(), eq("tenant-a"), eq(false));
        assertEquals("RELATION", captor.getValue().getDataScope().getScopeType());
    }

    @Test
    void applyRecordShouldRejectWhenRoleBindingDrifts() throws Exception {
        UserScopeReleaseRecordRepository repository = Mockito.mock(UserScopeReleaseRecordRepository.class);
        RbacUserRepository userRepository = Mockito.mock(RbacUserRepository.class);
        UserAdminService userAdminService = Mockito.mock(UserAdminService.class);
        DataScopePreviewService previewService = Mockito.mock(DataScopePreviewService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        UserScopeReleaseService service = new UserScopeReleaseService(
                repository,
                userRepository,
                userAdminService,
                previewService,
                objectMapper
        );

        UserDataScopePreviewRequest previewRequest = new UserDataScopePreviewRequest();
        previewRequest.setTenantId("tenant-a");
        previewRequest.setUserId(8L);
        DataScopeRule baselineRule = new DataScopeRule();
        baselineRule.setScopeType("INHERIT");
        previewRequest.setBaselineDataScope(baselineRule);
        previewRequest.setBaselineRoleIds(List.of(11L));
        previewRequest.setBaselineOrgUnitIds(List.of(21L));

        UserScopeReleaseRecord entity = new UserScopeReleaseRecord();
        entity.setId(4L);
        entity.setTenantId("tenant-a");
        entity.setReleaseStatus("APPROVED");
        entity.setRequestedByUserId(400L);
        entity.setPreviewRequestJson(objectMapper.writeValueAsString(previewRequest));
        when(repository.findById(4L)).thenReturn(Optional.of(entity));
        when(userAdminService.resolveCurrentUserDataScope(8L)).thenReturn(baselineRule);
        when(userAdminService.resolveCurrentUserRoleIds(8L)).thenReturn(List.of(12L));
        when(userAdminService.resolveCurrentUserOrgUnitIds(8L)).thenReturn(List.of(21L));

        RbacUserSession session = new RbacUserSession();
        session.setUserId(401L);
        session.setTenantId("tenant-a");

        BizException ex = assertThrows(BizException.class,
                () -> service.applyRecord(4L, "tenant-a", false, session));

        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void createRollbackRecordShouldCreateDraftRelease() throws Exception {
        UserScopeReleaseRecordRepository repository = Mockito.mock(UserScopeReleaseRecordRepository.class);
        RbacUserRepository userRepository = Mockito.mock(RbacUserRepository.class);
        UserAdminService userAdminService = Mockito.mock(UserAdminService.class);
        DataScopePreviewService previewService = Mockito.mock(DataScopePreviewService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        UserScopeReleaseService service = new UserScopeReleaseService(
                repository,
                userRepository,
                userAdminService,
                previewService,
                objectMapper
        );

        UserDataScopePreviewRequest sourcePreviewRequest = new UserDataScopePreviewRequest();
        sourcePreviewRequest.setTenantId("tenant-a");
        sourcePreviewRequest.setUserId(8L);
        DataScopeRule targetRule = new DataScopeRule();
        targetRule.setScopeType("RELATION");
        sourcePreviewRequest.setDataScope(targetRule);
        DataScopeRule baselineRule = new DataScopeRule();
        baselineRule.setScopeType("INHERIT");
        sourcePreviewRequest.setBaselineDataScope(baselineRule);
        sourcePreviewRequest.setRoleIds(List.of(11L, 12L));
        sourcePreviewRequest.setBaselineRoleIds(List.of(11L));
        sourcePreviewRequest.setOrgUnitIds(List.of(21L, 22L));
        sourcePreviewRequest.setBaselineOrgUnitIds(List.of(21L));

        UserScopeReleaseRecord source = new UserScopeReleaseRecord();
        source.setId(5L);
        source.setTenantId("tenant-a");
        source.setReleaseStatus("APPLIED");
        source.setRequestedByUserId(500L);
        source.setReleaseTitle("用户数据权限发布单");
        source.setPreviewRequestJson(objectMapper.writeValueAsString(sourcePreviewRequest));
        when(repository.findById(5L)).thenReturn(Optional.of(source));
        when(userAdminService.resolveCurrentUserDataScope(8L)).thenReturn(targetRule);
        when(userAdminService.resolveCurrentUserRoleIds(8L)).thenReturn(List.of(11L, 12L));
        when(userAdminService.resolveCurrentUserOrgUnitIds(8L)).thenReturn(List.of(21L, 22L));

        DataScopePreviewDiffView diff = new DataScopePreviewDiffView();
        diff.setBaselineSummary("当前配置：关系目标组织范围，命中 2 个组织");
        diff.setTargetSummary("继承角色：本租户数据");
        diff.setAddedOrgCount(0);
        diff.setRemovedOrgCount(2);
        DataScopePreviewView preview = new DataScopePreviewView();
        preview.setDiff(diff);
        preview.setImpactSummary("当前用户变更会影响 1 个用户自身的数据可见范围，并涉及 1 个绑定角色的继承关系。");
        preview.setAffectedRoleCount(1);
        preview.setAffectedUserCount(1);
        when(previewService.previewUserScope(any(UserDataScopePreviewRequest.class), eq("tenant-a"), eq(false))).thenReturn(preview);

        RbacUser user = new RbacUser();
        user.setId(8L);
        user.setTenantId("tenant-a");
        user.setUsername("alice");
        user.setNickname("爱丽丝");
        when(userRepository.findById(8L)).thenReturn(Optional.of(user));

        AtomicLong idGenerator = new AtomicLong(10L);
        when(repository.save(any(UserScopeReleaseRecord.class))).thenAnswer(invocation -> {
            UserScopeReleaseRecord entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(idGenerator.getAndIncrement());
            }
            return entity;
        });

        UserScopeReleaseRollbackRequest request = new UserScopeReleaseRollbackRequest();
        request.setChangeRemark("回滚到继承角色");
        RbacUserSession session = new RbacUserSession();
        session.setUserId(501L);
        session.setTenantId("tenant-a");
        session.setUsername("reviewer");

        UserScopeReleaseView view = service.createRollbackRecord(5L, request, "tenant-a", false, session);

        assertEquals("DRAFT", view.getReleaseStatus());
        assertEquals("alice", view.getUsername());
    }
}
