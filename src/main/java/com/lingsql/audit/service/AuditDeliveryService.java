package com.lingsql.audit.service;

import com.lingsql.audit.entity.AuditDelivery;
import com.lingsql.audit.model.AuditLogCommand;
import com.lingsql.audit.repository.AuditDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 审计投递箱状态服务。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Service
@RequiredArgsConstructor
public class AuditDeliveryService {

    static final String OUTCOME_PREPARED = "PREPARED";
    static final String OUTCOME_SUCCESS = "SUCCESS";
    static final String OUTCOME_FAILED = "FAILED";
    static final String OUTCOME_UNKNOWN = "UNKNOWN";
    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_DELIVERED = "DELIVERED";
    static final String STATUS_DEAD = "DEAD";

    private final AuditDeliveryRepository repository;
    private final AuditDetailSanitizer detailSanitizer;
    private final ObjectMapper objectMapper;

    /**
     * 在受审计操作开始前持久化操作意图。失败时调用方必须停止执行业务。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String prepare(AuditLogCommand command) {
        if (command == null) {
            throw new AuditPersistenceException("审计操作意图不能为空", null);
        }
        String eventId = StringUtils.hasText(command.getEventId())
                ? command.getEventId().trim()
                : UUID.randomUUID().toString().replace("-", "");
        command.setEventId(eventId);
        command.setDetail(detailSanitizer.sanitize(command.getDetail()));
        LocalDateTime now = LocalDateTime.now();
        AuditDelivery delivery = new AuditDelivery();
        delivery.setEventId(eventId);
        delivery.setTenantId(command.getTenantId());
        delivery.setAction(command.getAction());
        delivery.setResource(command.getResource());
        delivery.setPayloadJson(serialize(command));
        delivery.setOutcome(OUTCOME_PREPARED);
        delivery.setDeliveryStatus(STATUS_PENDING);
        delivery.setAttemptCount(0);
        delivery.setNextAttemptTime(now);
        delivery.setCreateTime(now);
        delivery.setUpdateTime(now);
        repository.saveAndFlush(delivery);
        return eventId;
    }

    /**
     * 持久化业务执行结果，后续由发布器幂等写入最终审计表。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeOutcome(String eventId, String detail, boolean success, String errorMessage) {
        AuditDelivery delivery = repository.findByEventId(eventId)
                .orElseThrow(() -> new AuditPersistenceException("审计投递记录不存在", null));
        AuditLogCommand command = deserialize(delivery.getPayloadJson());
        if (detail != null) {
            command.setDetail(detailSanitizer.sanitize(detail));
        }
        command.setSuccess(success);
        command.setErrorMessage(detailSanitizer.sanitizeText(errorMessage));
        LocalDateTime now = LocalDateTime.now();
        delivery.setPayloadJson(serialize(command));
        delivery.setOutcome(success ? OUTCOME_SUCCESS : OUTCOME_FAILED);
        delivery.setFinalizeTime(now);
        delivery.setNextAttemptTime(now);
        delivery.setUpdateTime(now);
        repository.saveAndFlush(delivery);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStalePrepared(Duration timeout, int batchSize) {
        Duration resolvedTimeout = timeout == null ? Duration.ofMinutes(5) : timeout;
        List<AuditDelivery> stale = repository.findStalePrepared(
                OUTCOME_PREPARED,
                LocalDateTime.now().minus(resolvedTimeout),
                PageRequest.of(0, Math.max(1, batchSize))
        );
        for (AuditDelivery delivery : stale) {
            AuditLogCommand command = deserialize(delivery.getPayloadJson());
            command.setSuccess(false);
            command.setErrorMessage("操作结果未知，需要人工核对");
            LocalDateTime now = LocalDateTime.now();
            delivery.setPayloadJson(serialize(command));
            delivery.setOutcome(OUTCOME_UNKNOWN);
            delivery.setFinalizeTime(now);
            delivery.setNextAttemptTime(now);
            delivery.setUpdateTime(now);
        }
        repository.saveAll(stale);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordDeliveryFailure(String eventId,
                                         int maxAttempts,
                                         long retryDelayMs,
                                         Throwable failure) {
        AuditDelivery delivery = repository.findByEventId(eventId)
                .orElseThrow(() -> new AuditPersistenceException("审计投递记录不存在", failure));
        int attempts = (delivery.getAttemptCount() == null ? 0 : delivery.getAttemptCount()) + 1;
        delivery.setAttemptCount(attempts);
        delivery.setLastError(failure == null ? "UNKNOWN" : failure.getClass().getSimpleName());
        delivery.setUpdateTime(LocalDateTime.now());
        boolean dead = attempts >= Math.max(1, maxAttempts);
        if (dead) {
            delivery.setDeliveryStatus(STATUS_DEAD);
        } else {
            long delay = Math.min(Math.max(1000L, retryDelayMs) * attempts, Duration.ofMinutes(10).toMillis());
            delivery.setNextAttemptTime(LocalDateTime.now().plusNanos(delay * 1_000_000L));
        }
        repository.saveAndFlush(delivery);
        return dead;
    }

    AuditLogCommand deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, AuditLogCommand.class);
        } catch (Exception ex) {
            throw new AuditPersistenceException("审计投递载荷无法解析", ex);
        }
    }

    String serialize(AuditLogCommand command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (Exception ex) {
            throw new AuditPersistenceException("审计投递载荷无法序列化", ex);
        }
    }
}
