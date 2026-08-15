package com.lingsql.audit.service;

import com.lingsql.audit.entity.AuditDelivery;
import com.lingsql.audit.repository.AuditDeliveryRepository;
import com.lingsql.config.LingSqlProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计投递箱发布与异常恢复服务。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditDeliveryPublisher {

    private final AuditDeliveryRepository repository;
    private final AuditDeliveryService deliveryService;
    private final AuditDeliveryAttemptService attemptService;
    private final LingSqlProperties properties;

    @Scheduled(fixedDelayString = "${lingsql.audit.delivery-interval-ms:5000}")
    public void publishPending() {
        LingSqlProperties.Audit audit = properties.getAudit();
        int batchSize = audit == null ? 100 : Math.max(1, audit.getDeliveryBatchSize());
        deliveryService.markStalePrepared(audit == null ? null : audit.getPreparedTimeout(), batchSize);
        List<AuditDelivery> deliveries = repository.findDeliverable(
                AuditDeliveryService.STATUS_PENDING,
                AuditDeliveryService.OUTCOME_PREPARED,
                LocalDateTime.now(),
                PageRequest.of(0, batchSize)
        );
        for (AuditDelivery delivery : deliveries) {
            publishNow(delivery.getEventId());
        }
    }

    public void publishNow(String eventId) {
        LingSqlProperties.Audit audit = properties.getAudit();
        try {
            attemptService.publish(eventId);
        } catch (Exception ex) {
            int maxAttempts = audit == null ? 10 : Math.max(1, audit.getDeliveryMaxAttempts());
            long retryDelay = audit == null ? 5000L : audit.getDeliveryRetryDelayMs();
            boolean dead = deliveryService.recordDeliveryFailure(eventId, maxAttempts, retryDelay, ex);
            if (dead) {
                log.error("审计投递进入死信状态: eventId={}", eventId, ex);
            } else {
                log.error("审计投递失败，已登记持久补偿: eventId={}", eventId, ex);
            }
        }
    }
}
