package com.lingsql.organization.service;

import tools.jackson.databind.ObjectMapper;
import com.lingsql.common.exception.BizException;
import com.lingsql.organization.entity.OrgRelationExecution;
import com.lingsql.organization.entity.OrgRelationExecutionTimeline;
import com.lingsql.organization.model.OrgRelationExecutionCreateRequest;
import com.lingsql.organization.model.OrgRelationExecutionTimelineView;
import com.lingsql.organization.model.OrgRelationExecutionUpdateRequest;
import com.lingsql.organization.model.OrgRelationExecutionView;
import com.lingsql.organization.model.OrgRelationRouteCandidateView;
import com.lingsql.organization.model.OrgRelationRouteResolveView;
import com.lingsql.organization.repository.OrgRelationExecutionRepository;
import com.lingsql.organization.repository.OrgRelationExecutionTimelineRepository;
import com.lingsql.rbac.entity.RbacUser;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.rbac.security.RbacUserSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class OrgRelationExecutionServiceTest {

    @Test
    void createExecutionShouldCreateTodoWhenPrimaryRouteResolved() {
        OrgRelationExecutionRepository repository = Mockito.mock(OrgRelationExecutionRepository.class);
        OrgRelationExecutionTimelineRepository timelineRepository = Mockito.mock(OrgRelationExecutionTimelineRepository.class);
        OrganizationAdminService organizationAdminService = Mockito.mock(OrganizationAdminService.class);
        RbacUserRepository userRepository = Mockito.mock(RbacUserRepository.class);
        OrgRelationExecutionService service = new OrgRelationExecutionService(
                repository,
                timelineRepository,
                organizationAdminService,
                userRepository,
                new ObjectMapper()
        );

        OrgRelationRouteResolveView routeResolveView = buildRouteResolve(false, 5L, 20L, "SERVICE_CENTER");
        when(organizationAdminService.resolveRelationRoute("tenant-a", 10L, "SERVICE")).thenReturn(routeResolveView);

        RbacUser assignee = new RbacUser();
        assignee.setId(20L);
        assignee.setTenantId("tenant-a");
        assignee.setUsername("service.owner");
        assignee.setNickname("服务负责人");
        when(userRepository.findById(20L)).thenReturn(Optional.of(assignee));
        when(repository.save(any(OrgRelationExecution.class))).thenAnswer(invocation -> {
            OrgRelationExecution entity = invocation.getArgument(0);
            entity.setId(100L);
            return entity;
        });

        OrgRelationExecutionCreateRequest request = new OrgRelationExecutionCreateRequest();
        request.setSourceOrgUnitId(10L);
        request.setRelationType("SERVICE");
        request.setExecutionTitle("创建服务执行项");
        request.setExecutionContent("需要跟进售后服务");

        OrgRelationExecutionView view = service.createExecution(request, "tenant-a", false, buildSession());

        assertEquals(100L, view.getId());
        assertEquals("TODO", view.getStatus());
        assertEquals("SERVICE_CENTER", view.getTargetOrgUnitCode());
        assertEquals("服务负责人 (service.owner)", view.getAssigneeName());
        assertEquals(24, view.getSlaHours());
        assertNotNull(view.getDueTime());
        assertEquals("ON_TRACK", view.getSlaStatus());
        Mockito.verify(timelineRepository).save(any(OrgRelationExecutionTimeline.class));
    }

    @Test
    void createExecutionShouldRejectWhenRouteAmbiguousWithoutRelationId() {
        OrgRelationExecutionRepository repository = Mockito.mock(OrgRelationExecutionRepository.class);
        OrgRelationExecutionTimelineRepository timelineRepository = Mockito.mock(OrgRelationExecutionTimelineRepository.class);
        OrganizationAdminService organizationAdminService = Mockito.mock(OrganizationAdminService.class);
        RbacUserRepository userRepository = Mockito.mock(RbacUserRepository.class);
        OrgRelationExecutionService service = new OrgRelationExecutionService(
                repository,
                timelineRepository,
                organizationAdminService,
                userRepository,
                new ObjectMapper()
        );

        OrgRelationRouteResolveView routeResolveView = buildRouteResolve(true, 5L, 20L, "TARGET_A");
        OrgRelationRouteCandidateView candidate = new OrgRelationRouteCandidateView();
        candidate.setRelationId(6L);
        candidate.setRelationType("COOPERATE");
        candidate.setRelationName("协同B");
        candidate.setTargetOrgUnitId(31L);
        candidate.setTargetOrgUnitCode("TARGET_B");
        candidate.setTargetOrgUnitName("协同B");
        candidate.setScopeUnitCount(1);
        candidate.setScopeUnitCodes(List.of("TARGET_B"));
        candidate.setRouteScopeSummary("当前仅覆盖目标组织“协同B”本身。");
        routeResolveView.getCandidates().add(candidate);
        routeResolveView.setCandidateCount(2);
        when(organizationAdminService.resolveRelationRoute("tenant-a", 10L, "COOPERATE")).thenReturn(routeResolveView);

        OrgRelationExecutionCreateRequest request = new OrgRelationExecutionCreateRequest();
        request.setSourceOrgUnitId(10L);
        request.setRelationType("COOPERATE");
        request.setExecutionTitle("创建协同执行项");

        assertThrows(BizException.class, () -> service.createExecution(request, "tenant-a", false, buildSession()));
    }

    @Test
    void updateExecutionShouldRequireResultSummaryWhenDone() {
        OrgRelationExecutionRepository repository = Mockito.mock(OrgRelationExecutionRepository.class);
        OrgRelationExecutionTimelineRepository timelineRepository = Mockito.mock(OrgRelationExecutionTimelineRepository.class);
        OrganizationAdminService organizationAdminService = Mockito.mock(OrganizationAdminService.class);
        RbacUserRepository userRepository = Mockito.mock(RbacUserRepository.class);
        OrgRelationExecutionService service = new OrgRelationExecutionService(
                repository,
                timelineRepository,
                organizationAdminService,
                userRepository,
                new ObjectMapper()
        );

        OrgRelationExecution entity = buildExecutionEntity();
        entity.setStatus("IN_PROGRESS");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        OrgRelationExecutionUpdateRequest request = new OrgRelationExecutionUpdateRequest();
        request.setStatus("DONE");

        assertThrows(BizException.class, () -> service.updateExecution(1L, request, "tenant-a", false, buildSession()));
    }

    @Test
    void updateExecutionShouldCompleteExecutionWhenResultProvided() {
        OrgRelationExecutionRepository repository = Mockito.mock(OrgRelationExecutionRepository.class);
        OrgRelationExecutionTimelineRepository timelineRepository = Mockito.mock(OrgRelationExecutionTimelineRepository.class);
        OrganizationAdminService organizationAdminService = Mockito.mock(OrganizationAdminService.class);
        RbacUserRepository userRepository = Mockito.mock(RbacUserRepository.class);
        OrgRelationExecutionService service = new OrgRelationExecutionService(
                repository,
                timelineRepository,
                organizationAdminService,
                userRepository,
                new ObjectMapper()
        );

        OrgRelationExecution entity = buildExecutionEntity();
        entity.setStatus("IN_PROGRESS");
        entity.setStartedTime(LocalDateTime.now().minusHours(1));
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(OrgRelationExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrgRelationExecutionUpdateRequest request = new OrgRelationExecutionUpdateRequest();
        request.setStatus("DONE");
        request.setResultSummary("已完成处理");

        OrgRelationExecutionView view = service.updateExecution(1L, request, "tenant-a", false, buildSession());

        assertEquals("DONE", view.getStatus());
        assertEquals("已完成处理", view.getResultSummary());
        assertNotNull(view.getCompletedTime());
        assertEquals("COMPLETED_ON_TIME", view.getSlaStatus());
        Mockito.verify(timelineRepository).save(any(OrgRelationExecutionTimeline.class));
    }

    @Test
    void listExecutionTimelineShouldReturnLatestEvents() {
        OrgRelationExecutionRepository repository = Mockito.mock(OrgRelationExecutionRepository.class);
        OrgRelationExecutionTimelineRepository timelineRepository = Mockito.mock(OrgRelationExecutionTimelineRepository.class);
        OrganizationAdminService organizationAdminService = Mockito.mock(OrganizationAdminService.class);
        RbacUserRepository userRepository = Mockito.mock(RbacUserRepository.class);
        OrgRelationExecutionService service = new OrgRelationExecutionService(
                repository,
                timelineRepository,
                organizationAdminService,
                userRepository,
                new ObjectMapper()
        );

        OrgRelationExecution execution = buildExecutionEntity();
        when(repository.findById(1L)).thenReturn(Optional.of(execution));

        OrgRelationExecutionTimeline timeline = new OrgRelationExecutionTimeline();
        timeline.setId(101L);
        timeline.setTenantId("tenant-a");
        timeline.setExecutionId(1L);
        timeline.setEventType("UPDATED");
        timeline.setEventSummary("状态更新");
        timeline.setActorName("管理员");
        timeline.setBeforeStatus("TODO");
        timeline.setAfterStatus("IN_PROGRESS");
        timeline.setCreateTime(LocalDateTime.now());
        when(timelineRepository.findByTenantIdAndExecutionIdOrderByCreateTimeDescIdDesc("tenant-a", 1L))
                .thenReturn(List.of(timeline));

        List<OrgRelationExecutionTimelineView> views = service.listExecutionTimeline(1L, "tenant-a", false);

        assertEquals(1, views.size());
        assertEquals("状态更新", views.getFirst().getEventSummary());
    }

    private OrgRelationRouteResolveView buildRouteResolve(boolean ambiguous, Long relationId, Long leaderUserId, String targetCode) {
        OrgRelationRouteResolveView routeResolveView = new OrgRelationRouteResolveView();
        routeResolveView.setTenantId("tenant-a");
        routeResolveView.setSourceOrgUnitId(10L);
        routeResolveView.setSourceOrgUnitCode("SRC");
        routeResolveView.setSourceOrgUnitName("源组织");
        routeResolveView.setRelationType(ambiguous ? "COOPERATE" : "SERVICE");
        routeResolveView.setMatched(true);
        routeResolveView.setAmbiguous(ambiguous);
        routeResolveView.setCandidateCount(1);

        OrgRelationRouteCandidateView candidate = new OrgRelationRouteCandidateView();
        candidate.setRelationId(relationId);
        candidate.setRelationType(routeResolveView.getRelationType());
        candidate.setRelationName(ambiguous ? "协同A" : "服务归口");
        candidate.setTargetOrgUnitId(30L);
        candidate.setTargetOrgUnitCode(targetCode);
        candidate.setTargetOrgUnitName(ambiguous ? "协同A" : "共享服务中心");
        candidate.setTargetLeaderUserId(leaderUserId);
        candidate.setScopeUnitCount(2);
        candidate.setScopeUnitCodes(List.of(targetCode, targetCode + "_CHILD"));
        candidate.setRouteScopeSummary("当前覆盖目标组织及下级共 2 个节点。");
        routeResolveView.setCandidates(new java.util.ArrayList<>(List.of(candidate)));
        return routeResolveView;
    }

    private OrgRelationExecution buildExecutionEntity() {
        OrgRelationExecution entity = new OrgRelationExecution();
        entity.setId(1L);
        entity.setTenantId("tenant-a");
        entity.setRelationId(5L);
        entity.setRelationType("SERVICE");
        entity.setRelationName("服务归口");
        entity.setSceneCode("SERVICE_DESK");
        entity.setSceneName("服务归口");
        entity.setExecutionTitle("售后服务工单");
        entity.setPriority("P1");
        entity.setSourceOrgUnitId(10L);
        entity.setSourceOrgUnitCode("SRC");
        entity.setSourceOrgUnitName("源组织");
        entity.setTargetOrgUnitId(30L);
        entity.setTargetOrgUnitCode("SERVICE_CENTER");
        entity.setTargetOrgUnitName("共享服务中心");
        entity.setRequestedByUserId(1L);
        entity.setRequestedByName("管理员");
        entity.setAssigneeUserId(20L);
        entity.setAssigneeName("服务负责人");
        entity.setCreateTime(LocalDateTime.now().minusHours(2));
        entity.setUpdateTime(LocalDateTime.now().minusMinutes(10));
        return entity;
    }

    private RbacUserSession buildSession() {
        RbacUserSession session = new RbacUserSession();
        session.setUserId(1L);
        session.setTenantId("tenant-a");
        session.setUsername("admin");
        session.setNickname("管理员");
        return session;
    }
}
