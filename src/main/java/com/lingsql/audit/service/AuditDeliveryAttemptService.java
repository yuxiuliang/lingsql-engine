package com.lingsql.audit.service;

import com.lingsql.audit.entity.AuditDelivery;
import com.lingsql.audit.model.AuditLogCommand;
import com.lingsql.audit.repository.AuditDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 单条审计投递事务，确保最终日志与 DELIVERED 状态原子提交。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Service
@RequiredArgsConstructor
public class AuditDeliveryAttemptService {

    private final AuditDeliveryRepository repository;
    private final AuditDeliveryService deliveryService;
    private final AuditLogService auditLogService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(String eventId) {
        AuditDelivery delivery = repository.findByEventId(eventId)
                .orElseThrow(() -> new AuditPersistenceException("审计投递记录不存在", null));
        if (!AuditDeliveryService.STATUS_PENDING.equals(delivery.getDeliveryStatus())
                || AuditDeliveryService.OUTCOME_PREPARED.equals(delivery.getOutcome())) {
            return;
        }
        AuditLogCommand command = deliveryService.deserialize(delivery.getPayloadJson());
        auditLogService.persistIdempotently(command);
        delivery.setDeliveryStatus(AuditDeliveryService.STATUS_DELIVERED);
        delivery.setLastError(null);
        delivery.setUpdateTime(LocalDateTime.now());
        repository.saveAndFlush(delivery);
    }
}
