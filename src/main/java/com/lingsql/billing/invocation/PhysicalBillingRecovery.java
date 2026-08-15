package com.lingsql.billing.invocation;

import com.lingsql.config.LingSqlProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 逐物理调用超时状态恢复任务。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Component
@RequiredArgsConstructor
class PhysicalBillingRecovery {

    private static final Duration DEFAULT_RESERVED_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration DEFAULT_DISPATCHED_TIMEOUT = Duration.ofMinutes(10);
    private static final int DEFAULT_BATCH_SIZE = 100;

    private final PhysicalModelBillingImpl billing;
    private final LingSqlProperties properties;

    @Scheduled(fixedDelayString = "${lingsql.billing.invocation-recovery-interval-ms:60000}")
    public void recover() {
        LingSqlProperties.Billing config = properties.getBilling();
        Duration reservedTimeout = config == null || config.getInvocationReservedTimeout() == null
                ? DEFAULT_RESERVED_TIMEOUT
                : config.getInvocationReservedTimeout();
        Duration dispatchedTimeout = config == null || config.getInvocationDispatchedTimeout() == null
                ? DEFAULT_DISPATCHED_TIMEOUT
                : config.getInvocationDispatchedTimeout();
        int batchSize = config == null || config.getInvocationRecoveryBatchSize() <= 0
                ? DEFAULT_BATCH_SIZE
                : config.getInvocationRecoveryBatchSize();
        LocalDateTime now = LocalDateTime.now();
        billing.recoverStale(now.minus(reservedTimeout), now.minus(dispatchedTimeout), batchSize);
    }
}
