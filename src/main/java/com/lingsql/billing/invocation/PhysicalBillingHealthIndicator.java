package com.lingsql.billing.invocation;

import com.lingsql.config.LingSqlProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 逐物理模型调用计费健康指示器。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Component("physicalModelBilling")
@RequiredArgsConstructor
public class PhysicalBillingHealthIndicator implements HealthIndicator {

    private final BillingInvocationRepository repository;
    private final LingSqlProperties properties;

    @Override
    public Health health() {
        try {
            LingSqlProperties.Billing config = properties.getBilling();
            Duration reservedTimeout = config == null || config.getInvocationReservedTimeout() == null
                    ? Duration.ofMinutes(5)
                    : config.getInvocationReservedTimeout();
            Duration dispatchedTimeout = config == null || config.getInvocationDispatchedTimeout() == null
                    ? Duration.ofMinutes(10)
                    : config.getInvocationDispatchedTimeout();
            LocalDateTime now = LocalDateTime.now();
            long unknown = repository.countByState(BillingInvocationState.UNKNOWN.name());
            long staleReserved = repository.countByStateAndReservedAtBefore(
                    BillingInvocationState.RESERVED.name(), now.minus(reservedTimeout));
            long staleDispatched = repository.countByStateAndDispatchedAtBefore(
                    BillingInvocationState.DISPATCHED.name(), now.minus(dispatchedTimeout));
            Health.Builder builder = unknown > 0 || staleReserved > 0 || staleDispatched > 0
                    ? Health.down()
                    : Health.up();
            return builder
                    .withDetail("unknown", unknown)
                    .withDetail("staleReserved", staleReserved)
                    .withDetail("staleDispatched", staleDispatched)
                    .build();
        } catch (RuntimeException ex) {
            return Health.down(ex).build();
        }
    }
}
