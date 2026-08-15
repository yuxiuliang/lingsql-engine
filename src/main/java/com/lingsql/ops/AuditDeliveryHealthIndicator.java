package com.lingsql.ops;

import com.lingsql.audit.repository.AuditDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 审计持久投递箱健康指示器。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Component("auditDelivery")
@RequiredArgsConstructor
public class AuditDeliveryHealthIndicator implements HealthIndicator {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_DEAD = "DEAD";
    private static final String OUTCOME_PREPARED = "PREPARED";
    private static final String OUTCOME_UNKNOWN = "UNKNOWN";

    private final AuditDeliveryRepository repository;

    @Override
    public Health health() {
        try {
            long pending = repository.countByDeliveryStatus(STATUS_PENDING);
            long dead = repository.countByDeliveryStatus(STATUS_DEAD);
            long prepared = repository.countByOutcome(OUTCOME_PREPARED);
            long unknown = repository.countByOutcome(OUTCOME_UNKNOWN);
            Health.Builder builder = dead > 0 || unknown > 0 ? Health.down() : Health.up();
            return builder
                    .withDetail("pending", pending)
                    .withDetail("prepared", prepared)
                    .withDetail("unknown", unknown)
                    .withDetail("dead", dead)
                    .build();
        } catch (Exception ex) {
            return Health.down(ex).build();
        }
    }
}
