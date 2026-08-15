package com.lingsql.billing;

import com.lingsql.billing.repository.BillingLedgerEventRepository;
import com.lingsql.config.LingSqlProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 计费账本对账服务。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingLedgerReconcileService {

    private final BillingLedgerEventRepository repository;
    private final LingSqlProperties properties;
    private final BillingReconcileLogService reconcileLogService;

    /**
     * 定时对账，检测预扣与 CLOSE 差异。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Scheduled(fixedDelayString = "${lingsql.billing.reconcile-interval-ms}")
    public void reconcileLedger() {
        int batchSize = resolveBatchSize();
        Duration window = resolveWindow();
        LocalDateTime since = LocalDateTime.now().minus(window);
        List<BillingLedgerEventRepository.LedgerReconcileProjection> records =
                repository.findReconcileCandidates(since, PageRequest.of(0, batchSize));
        if (records == null || records.isEmpty()) {
            return;
        }
        long threshold = resolveThreshold();
        for (BillingLedgerEventRepository.LedgerReconcileProjection record : records) {
            long reserve = valueOrZero(record.getReserveTotal());
            long close = valueOrZero(record.getCloseTotal());
            long diff = Math.abs(reserve - close);
            if (diff <= threshold) {
                continue;
            }
            log.warn("账本对账异常: requestId={}, reserveTotal={}, closeTotal={}, diff={}, latestTime={}",
                    record.getRequestId(), reserve, close, diff, record.getLatestTime());
            reconcileLogService.recordIfNeeded(
                    record.getTenantId(),
                    record.getRequestId(),
                    reserve,
                    close,
                    diff,
                    record.getLatestTime()
            );
        }
    }

    private int resolveBatchSize() {
        LingSqlProperties.Billing billing = properties.getBilling();
        if (billing == null || billing.getReconcileBatchSize() <= 0) {
            return 200;
        }
        return billing.getReconcileBatchSize();
    }

    private Duration resolveWindow() {
        LingSqlProperties.Billing billing = properties.getBilling();
        if (billing == null || billing.getReconcileWindow() == null) {
            return Duration.ofHours(24);
        }
        return billing.getReconcileWindow();
    }

    private long resolveThreshold() {
        LingSqlProperties.Billing billing = properties.getBilling();
        if (billing == null) {
            return 0L;
        }
        return Math.max(0L, billing.getReconcileDiffThreshold());
    }

    private long valueOrZero(Long value) {
        if (value == null || value < 0) {
            return 0L;
        }
        return value;
    }
}
