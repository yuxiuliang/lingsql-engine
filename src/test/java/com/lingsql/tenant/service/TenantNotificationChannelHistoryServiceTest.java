package com.lingsql.tenant.service;

import tools.jackson.databind.ObjectMapper;
import com.lingsql.audit.model.AuditLogQuery;
import com.lingsql.audit.model.AuditLogView;
import com.lingsql.audit.service.AuditLogService;
import com.lingsql.common.result.PageResult;
import com.lingsql.tenant.model.TenantNotificationChannelFailureStatsView;
import com.lingsql.tenant.model.TenantNotificationChannelRecentTestView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantNotificationChannelHistoryServiceTest {

    @Test
    void shouldListRecentNotificationTestsFromAuditLogs() {
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        TenantNotificationChannelHistoryService service = new TenantNotificationChannelHistoryService(auditLogService, new ObjectMapper());

        AuditLogView auditLog = new AuditLogView();
        auditLog.setId(101L);
        auditLog.setTenantId("TENANT_A");
        auditLog.setUsername("sys_admin");
        auditLog.setSuccess(1);
        auditLog.setSummary("测试成功");
        auditLog.setCreateTime(LocalDateTime.of(2026, 4, 15, 18, 0, 0));
        auditLog.setDetail("""
                {
                  "tenantId":"TENANT_A",
                  "tenantName":"演示租户",
                  "requestedChannels":["webhook","dingtalk"],
                  "success":false,
                  "summary":"Webhook 成功，钉钉失败",
                  "channelResults":[
                    {"channel":"webhook","success":true,"statusCode":200,"detail":"ok"},
                    {"channel":"dingtalk","success":false,"statusCode":500,"detail":"internal error"}
                  ]
                }
                """);

        when(auditLogService.query(Mockito.any(AuditLogQuery.class), Mockito.eq(1), Mockito.eq(10)))
                .thenReturn(new PageResult<>(List.of(auditLog), 1, 1, 10));

        List<TenantNotificationChannelRecentTestView> result = service.listRecentTests(null, null, null, null, null, null, 10);

        assertEquals(1, result.size());
        TenantNotificationChannelRecentTestView first = result.getFirst();
        assertEquals(101L, first.getAuditLogId());
        assertEquals("TENANT_A", first.getTenantId());
        assertEquals("演示租户", first.getTenantName());
        assertEquals(List.of("webhook", "dingtalk"), first.getRequestedChannels());
        assertFalse(Boolean.TRUE.equals(first.getSuccess()));
        assertEquals("Webhook 成功，钉钉失败", first.getSummary());
        assertEquals("sys_admin", first.getOperatorUsername());
        assertEquals(2, first.getChannelResults().size());
        assertTrue(Boolean.TRUE.equals(first.getChannelResults().getFirst().getSuccess()));

        ArgumentCaptor<AuditLogQuery> queryCaptor = ArgumentCaptor.forClass(AuditLogQuery.class);
        verify(auditLogService).query(queryCaptor.capture(), Mockito.eq(1), Mockito.eq(10));
        assertEquals("TENANT_NOTIFICATION_CHANNEL_TEST", queryCaptor.getValue().getAction());
    }

    @Test
    void shouldSummarizeFailureStatsFromRecentTests() {
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        TenantNotificationChannelHistoryService service = new TenantNotificationChannelHistoryService(auditLogService, new ObjectMapper());

        AuditLogView failedAudit = new AuditLogView();
        failedAudit.setId(201L);
        failedAudit.setTenantId("TENANT_A");
        failedAudit.setSuccess(0);
        failedAudit.setDetail("""
                {
                  "tenantId":"TENANT_A",
                  "requestedChannels":["webhook","dingtalk"],
                  "success":false,
                  "summary":"Webhook 成功，钉钉失败",
                  "channelResults":[
                    {"channel":"dingtalk","success":false,"statusCode":500,"detail":"internal error"},
                    {"channel":"webhook","success":true,"statusCode":200,"detail":"ok"}
                  ]
                }
                """);
        AuditLogView failedAudit2 = new AuditLogView();
        failedAudit2.setId(202L);
        failedAudit2.setTenantId("TENANT_A");
        failedAudit2.setSuccess(0);
        failedAudit2.setDetail("""
                {
                  "tenantId":"TENANT_A",
                  "requestedChannels":["wecom"],
                  "success":false,
                  "summary":"企微超时",
                  "channelResults":[
                    {"channel":"wecom","success":false,"statusCode":504,"detail":"timeout"}
                  ]
                }
                """);
        AuditLogView successAudit = new AuditLogView();
        successAudit.setId(203L);
        successAudit.setTenantId("TENANT_A");
        successAudit.setSuccess(1);
        successAudit.setDetail("""
                {
                  "tenantId":"TENANT_A",
                  "requestedChannels":["webhook"],
                  "success":true,
                  "summary":"全部成功"
                }
                """);

        when(auditLogService.query(Mockito.any(AuditLogQuery.class), Mockito.eq(1), Mockito.eq(100)))
                .thenReturn(new PageResult<>(List.of(failedAudit, failedAudit2, successAudit), 3, 1, 100));

        TenantNotificationChannelFailureStatsView view = service.summarizeFailures("TENANT_A", null, null, 100);

        assertEquals("TENANT_A", view.getTenantId());
        assertEquals(3, view.getTotalTests());
        assertEquals(2, view.getFailedTests());
        assertEquals(33.33D, view.getSuccessRate(), 0.01D);
        assertEquals("dingtalk", view.getTopFailingChannels().getFirst().getChannel());
        assertEquals("internal error", view.getTopFailureReasons().getFirst().getReason());
    }

    @Test
    void shouldFilterRecentTestsBySuccessChannelAndOperator() {
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        TenantNotificationChannelHistoryService service = new TenantNotificationChannelHistoryService(auditLogService, new ObjectMapper());

        AuditLogView failedAudit = new AuditLogView();
        failedAudit.setId(301L);
        failedAudit.setTenantId("TENANT_A");
        failedAudit.setUsername("ops_admin");
        failedAudit.setSuccess(0);
        failedAudit.setCreateTime(LocalDateTime.of(2026, 4, 15, 19, 0, 0));
        failedAudit.setDetail("""
                {
                  "tenantId":"TENANT_A",
                  "tenantName":"演示租户",
                  "requestedChannels":["dingtalk"],
                  "success":false,
                  "summary":"钉钉失败",
                  "channelResults":[
                    {"channel":"dingtalk","success":false,"statusCode":500,"detail":"robot blocked"}
                  ]
                }
                """);
        AuditLogView successAudit = new AuditLogView();
        successAudit.setId(302L);
        successAudit.setTenantId("TENANT_A");
        successAudit.setUsername("sys_admin");
        successAudit.setSuccess(1);
        successAudit.setCreateTime(LocalDateTime.of(2026, 4, 15, 18, 0, 0));
        successAudit.setDetail("""
                {
                  "tenantId":"TENANT_A",
                  "tenantName":"演示租户",
                  "requestedChannels":["webhook"],
                  "success":true,
                  "summary":"Webhook 成功"
                }
                """);

        when(auditLogService.query(Mockito.any(AuditLogQuery.class), Mockito.eq(1), Mockito.eq(200)))
                .thenReturn(new PageResult<>(List.of(failedAudit, successAudit), 2, 1, 200));

        List<TenantNotificationChannelRecentTestView> result = service.listRecentTests("TENANT_A", 0, "dingtalk", "ops", null, null, 10);

        assertEquals(1, result.size());
        assertEquals(301L, result.getFirst().getAuditLogId());
        assertFalse(Boolean.TRUE.equals(result.getFirst().getSuccess()));
    }

    @Test
    void shouldPassTimeRangeToAuditQuery() {
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        TenantNotificationChannelHistoryService service = new TenantNotificationChannelHistoryService(auditLogService, new ObjectMapper());
        LocalDateTime startTime = LocalDateTime.of(2026, 4, 10, 0, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 4, 15, 23, 59, 59);

        when(auditLogService.query(Mockito.any(AuditLogQuery.class), Mockito.eq(1), Mockito.eq(10)))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 10));

        service.listRecentTests("TENANT_A", null, null, null, startTime, endTime, 10);

        ArgumentCaptor<AuditLogQuery> queryCaptor = ArgumentCaptor.forClass(AuditLogQuery.class);
        verify(auditLogService).query(queryCaptor.capture(), Mockito.eq(1), Mockito.eq(10));
        assertEquals(startTime, queryCaptor.getValue().getStartTime());
        assertEquals(endTime, queryCaptor.getValue().getEndTime());
    }
}
