package com.lingsql.organization.service;

import com.lingsql.config.LingSqlProperties;
import com.lingsql.ops.OpsAlertService;
import com.lingsql.organization.entity.OrgRelationExecution;
import com.lingsql.organization.entity.OrgRelationExecutionTimeline;
import com.lingsql.organization.repository.OrgRelationExecutionRepository;
import com.lingsql.organization.repository.OrgRelationExecutionTimelineRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrgRelationExecutionEscalationServiceTest {

    @Test
    void refreshEscalationsShouldMarkDueSoonExecution() {
        OrgRelationExecutionRepository executionRepository = Mockito.mock(OrgRelationExecutionRepository.class);
        OrgRelationExecutionTimelineRepository timelineRepository = Mockito.mock(OrgRelationExecutionTimelineRepository.class);
        OpsAlertService opsAlertService = Mockito.mock(OpsAlertService.class);
        LingSqlProperties properties = new LingSqlProperties();
        properties.getOrgExecution().setDueSoonHours(4);
        OrgRelationExecutionEscalationService service = new OrgRelationExecutionEscalationService(
                executionRepository,
                timelineRepository,
                properties,
                opsAlertService
        );

        OrgRelationExecution execution = buildExecution();
        execution.setDueTime(LocalDateTime.now().plusHours(2));
        when(executionRepository.findByStatusInAndDueTimeIsNotNullOrderByDueTimeAscIdAsc(List.of("TODO", "IN_PROGRESS", "BLOCKED")))
                .thenReturn(List.of(execution));
        when(executionRepository.save(any(OrgRelationExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int changed = service.refreshEscalations();

        assertEquals(1, changed);
        assertEquals("DUE_SOON", execution.getEscalationStatus());
        verify(timelineRepository).save(any(OrgRelationExecutionTimeline.class));
        verify(opsAlertService, never()).sendAlert(any(), any(), any());
    }

    @Test
    void refreshEscalationsShouldEscalateOverdueExecutionAndSendAlert() {
        OrgRelationExecutionRepository executionRepository = Mockito.mock(OrgRelationExecutionRepository.class);
        OrgRelationExecutionTimelineRepository timelineRepository = Mockito.mock(OrgRelationExecutionTimelineRepository.class);
        OpsAlertService opsAlertService = Mockito.mock(OpsAlertService.class);
        LingSqlProperties properties = new LingSqlProperties();
        properties.getOrgExecution().setDueSoonHours(4);
        properties.getOrgExecution().setEscalationCooldown(Duration.ofHours(4));
        OrgRelationExecutionEscalationService service = new OrgRelationExecutionEscalationService(
                executionRepository,
                timelineRepository,
                properties,
                opsAlertService
        );

        OrgRelationExecution execution = buildExecution();
        execution.setDueTime(LocalDateTime.now().minusHours(1));
        when(executionRepository.findByStatusInAndDueTimeIsNotNullOrderByDueTimeAscIdAsc(List.of("TODO", "IN_PROGRESS", "BLOCKED")))
                .thenReturn(List.of(execution));
        when(executionRepository.save(any(OrgRelationExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(opsAlertService.sendAlert(any(), any(), any())).thenReturn(true);

        int changed = service.refreshEscalations();

        assertEquals(1, changed);
        assertEquals("ESCALATED", execution.getEscalationStatus());
        assertEquals(1, execution.getEscalationLevel());
        verify(timelineRepository).save(any(OrgRelationExecutionTimeline.class));
        verify(opsAlertService).sendAlert(any(), any(), any());
    }

    private OrgRelationExecution buildExecution() {
        OrgRelationExecution execution = new OrgRelationExecution();
        execution.setId(1L);
        execution.setTenantId("tenant-a");
        execution.setExecutionTitle("测试执行项");
        execution.setRelationType("SERVICE");
        execution.setStatus("TODO");
        execution.setPriority("P1");
        execution.setEscalationStatus("NORMAL");
        execution.setEscalationLevel(0);
        execution.setUpdateTime(LocalDateTime.now().minusMinutes(30));
        return execution;
    }
}
