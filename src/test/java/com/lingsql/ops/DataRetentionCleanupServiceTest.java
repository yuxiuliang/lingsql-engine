package com.lingsql.ops;

import com.lingsql.audit.repository.AuditLogRepository;
import com.lingsql.audit.repository.AuditDeliveryRepository;
import com.lingsql.audit.repository.SqlExecuteLogRepository;
import com.lingsql.chat.repository.ChatHistoryRepository;
import com.lingsql.config.LingSqlProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataRetentionCleanupServiceTest {

    @Test
    void shouldSkipWhenRetentionDisabled() {
        SqlExecuteLogRepository sqlLogRepository = Mockito.mock(SqlExecuteLogRepository.class);
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);
        AuditDeliveryRepository auditDeliveryRepository = Mockito.mock(AuditDeliveryRepository.class);
        ChatHistoryRepository chatHistoryRepository = Mockito.mock(ChatHistoryRepository.class);
        OpsAlertService alertService = Mockito.mock(OpsAlertService.class);
        LingSqlProperties properties = new LingSqlProperties();
        properties.getRetention().setEnabled(false);
        DataRetentionCleanupService service = new DataRetentionCleanupService(
                auditLogRepository,
                auditDeliveryRepository,
                sqlLogRepository,
                chatHistoryRepository,
                properties,
                alertService
        );

        service.cleanupExpiredData();

        verify(sqlLogRepository, never()).countByCreateTimeBefore(any(LocalDateTime.class));
        verify(auditLogRepository, never()).countByCreateTimeBefore(any(LocalDateTime.class));
        verify(chatHistoryRepository, never()).countByCreateTimeBefore(any(LocalDateTime.class));
    }

    @Test
    void shouldOnlyCountInDryRunMode() {
        SqlExecuteLogRepository sqlLogRepository = Mockito.mock(SqlExecuteLogRepository.class);
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);
        AuditDeliveryRepository auditDeliveryRepository = Mockito.mock(AuditDeliveryRepository.class);
        ChatHistoryRepository chatHistoryRepository = Mockito.mock(ChatHistoryRepository.class);
        OpsAlertService alertService = Mockito.mock(OpsAlertService.class);
        LingSqlProperties properties = new LingSqlProperties();
        properties.getRetention().setEnabled(true);
        properties.getRetention().setDryRun(true);
        DataRetentionCleanupService service = new DataRetentionCleanupService(
                auditLogRepository,
                auditDeliveryRepository,
                sqlLogRepository,
                chatHistoryRepository,
                properties,
                alertService
        );
        when(sqlLogRepository.countByCreateTimeBefore(any(LocalDateTime.class))).thenReturn(11L);
        when(auditLogRepository.countByCreateTimeBefore(any(LocalDateTime.class))).thenReturn(22L);
        when(chatHistoryRepository.countByCreateTimeBefore(any(LocalDateTime.class))).thenReturn(33L);

        service.cleanupExpiredData();

        verify(sqlLogRepository, times(1)).countByCreateTimeBefore(any(LocalDateTime.class));
        verify(auditLogRepository, times(1)).countByCreateTimeBefore(any(LocalDateTime.class));
        verify(chatHistoryRepository, times(1)).countByCreateTimeBefore(any(LocalDateTime.class));
        verify(sqlLogRepository, never()).deleteByCreateTimeBefore(any(LocalDateTime.class));
        verify(auditLogRepository, never()).deleteByCreateTimeBefore(any(LocalDateTime.class));
        verify(chatHistoryRepository, never()).deleteByCreateTimeBefore(any(LocalDateTime.class));
    }

    @Test
    void shouldDeleteAndNotifyWhenThresholdReached() {
        SqlExecuteLogRepository sqlLogRepository = Mockito.mock(SqlExecuteLogRepository.class);
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);
        AuditDeliveryRepository auditDeliveryRepository = Mockito.mock(AuditDeliveryRepository.class);
        ChatHistoryRepository chatHistoryRepository = Mockito.mock(ChatHistoryRepository.class);
        OpsAlertService alertService = Mockito.mock(OpsAlertService.class);
        LingSqlProperties properties = new LingSqlProperties();
        properties.getRetention().setEnabled(true);
        properties.getRetention().setDryRun(false);
        properties.getRetention().setAlertThreshold(10L);
        DataRetentionCleanupService service = new DataRetentionCleanupService(
                auditLogRepository,
                auditDeliveryRepository,
                sqlLogRepository,
                chatHistoryRepository,
                properties,
                alertService
        );
        when(sqlLogRepository.countByCreateTimeBefore(any(LocalDateTime.class))).thenReturn(8L);
        when(auditLogRepository.countByCreateTimeBefore(any(LocalDateTime.class))).thenReturn(7L);
        when(chatHistoryRepository.countByCreateTimeBefore(any(LocalDateTime.class))).thenReturn(6L);
        when(sqlLogRepository.deleteByCreateTimeBefore(any(LocalDateTime.class))).thenReturn(8L);
        when(auditLogRepository.deleteByCreateTimeBefore(any(LocalDateTime.class))).thenReturn(7L);
        when(chatHistoryRepository.deleteByCreateTimeBefore(any(LocalDateTime.class))).thenReturn(6L);

        service.cleanupExpiredData();

        verify(sqlLogRepository, times(1)).deleteByCreateTimeBefore(any(LocalDateTime.class));
        verify(auditLogRepository, times(1)).deleteByCreateTimeBefore(any(LocalDateTime.class));
        verify(chatHistoryRepository, times(1)).deleteByCreateTimeBefore(any(LocalDateTime.class));
        verify(alertService, times(1)).sendAlert(anyString(), anyString(), anyMap());
    }
}
