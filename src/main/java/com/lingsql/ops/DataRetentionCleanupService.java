package com.lingsql.ops;

import com.lingsql.audit.repository.AuditLogRepository;
import com.lingsql.audit.repository.AuditDeliveryRepository;
import com.lingsql.audit.repository.SqlExecuteLogRepository;
import com.lingsql.chat.repository.ChatHistoryRepository;
import com.lingsql.config.LingSqlProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据留存清理任务。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionCleanupService {

    private final AuditLogRepository auditLogRepository;
    private final AuditDeliveryRepository auditDeliveryRepository;
    private final SqlExecuteLogRepository sqlExecuteLogRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final LingSqlProperties properties;
    private final OpsAlertService opsAlertService;

    /**
     * 定时清理超期数据（审计日志、SQL日志、聊天历史）。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    @Scheduled(cron = "${lingsql.retention.cleanup-cron:0 0 3 * * ?}")
    @Transactional
    public void cleanupExpiredData() {
        LingSqlProperties.Retention retention = properties.getRetention();
        if (retention == null || !retention.isEnabled()) {
            return;
        }
        int sqlLogDays = normalizeDays(retention.getSqlLogDays(), 90);
        int auditLogDays = normalizeDays(retention.getAuditLogDays(), 180);
        int chatHistoryDays = normalizeDays(retention.getChatHistoryDays(), 30);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sqlLogCutoff = now.minusDays(sqlLogDays);
        LocalDateTime auditLogCutoff = now.minusDays(auditLogDays);
        LocalDateTime chatHistoryCutoff = now.minusDays(chatHistoryDays);

        long sqlLogCandidate = sqlExecuteLogRepository.countByCreateTimeBefore(sqlLogCutoff);
        long auditLogCandidate = auditLogRepository.countByCreateTimeBefore(auditLogCutoff);
        long auditDeliveryCandidate = auditDeliveryRepository
                .countByDeliveryStatusAndUpdateTimeBefore("DELIVERED", auditLogCutoff);
        long chatHistoryCandidate = chatHistoryRepository.countByCreateTimeBefore(chatHistoryCutoff);
        if (retention.isDryRun()) {
            log.info("留存清理演练模式: sqlLogs={}, auditLogs={}, auditDeliveries={}, chatHistory={}",
                    sqlLogCandidate, auditLogCandidate, auditDeliveryCandidate, chatHistoryCandidate);
            return;
        }

        long sqlLogDeleted = sqlExecuteLogRepository.deleteByCreateTimeBefore(sqlLogCutoff);
        long auditLogDeleted = auditLogRepository.deleteByCreateTimeBefore(auditLogCutoff);
        long auditDeliveryDeleted = auditDeliveryRepository
                .deleteByDeliveryStatusAndUpdateTimeBefore("DELIVERED", auditLogCutoff);
        long chatHistoryDeleted = chatHistoryRepository.deleteByCreateTimeBefore(chatHistoryCutoff);
        long totalDeleted = sqlLogDeleted + auditLogDeleted + auditDeliveryDeleted + chatHistoryDeleted;
        log.info("留存清理完成: sqlLogs={}, auditLogs={}, auditDeliveries={}, chatHistory={}, total={}",
                sqlLogDeleted, auditLogDeleted, auditDeliveryDeleted, chatHistoryDeleted, totalDeleted);
        maybeAlert(retention, now, sqlLogDeleted, auditLogDeleted, auditDeliveryDeleted,
                chatHistoryDeleted, totalDeleted);
    }

    private void maybeAlert(LingSqlProperties.Retention retention,
                            LocalDateTime now,
                            long sqlLogDeleted,
                            long auditLogDeleted,
                            long auditDeliveryDeleted,
                            long chatHistoryDeleted,
                            long totalDeleted) {
        long threshold = Math.max(0L, retention.getAlertThreshold());
        if (threshold <= 0 || totalDeleted < threshold) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("runAt", now.toString());
        details.put("deleted.sqlLogs", sqlLogDeleted);
        details.put("deleted.auditLogs", auditLogDeleted);
        details.put("deleted.auditDeliveries", auditDeliveryDeleted);
        details.put("deleted.chatHistory", chatHistoryDeleted);
        details.put("deleted.total", totalDeleted);
        details.put("alertThreshold", threshold);
        opsAlertService.sendAlert(
                "数据留存清理阈值告警",
                "本次留存清理删除量超过阈值，请复核是否存在异常数据增长。",
                details
        );
    }

    private int normalizeDays(int days, int fallback) {
        if (days <= 0) {
            return fallback;
        }
        return days;
    }
}
