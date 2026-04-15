package com.lingsql.tenant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.audit.model.AuditLogQuery;
import com.lingsql.audit.model.AuditLogView;
import com.lingsql.audit.service.AuditLogService;
import com.lingsql.common.result.PageResult;
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

        List<TenantNotificationChannelRecentTestView> result = service.listRecentTests(null, 10);

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
}
