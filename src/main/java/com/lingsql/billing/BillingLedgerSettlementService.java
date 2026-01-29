package com.lingsql.billing;

import com.lingsql.billing.entity.BillingLedgerEvent;
import com.lingsql.billing.repository.BillingLedgerEventRepository;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 计费账本结算服务。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingLedgerSettlementService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_APPLIED = 1;
    private static final int STATUS_FAILED = 2;
    private static final int NOT_DELETED = 0;

    private final BillingLedgerEventRepository ledgerRepository;
    private final TenantRepository tenantRepository;
    private final LingSqlProperties properties;

    /**
     * 定时结算账本事件，更新数据库余额。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Scheduled(fixedDelayString = "#{@lingSqlProperties.billing.ledgerSettleIntervalMs}")
    @Transactional
    public void settleLedger() {
        int batchSize = resolveBatchSize();
        List<BillingLedgerEvent> events = ledgerRepository.findByStatusOrderByIdAsc(
                STATUS_PENDING,
                PageRequest.of(0, batchSize)
        );
        if (events == null || events.isEmpty()) {
            return;
        }
        Map<String, Long> costByTenant = new HashMap<>();
        List<Long> invalidIds = new java.util.ArrayList<>();
        for (BillingLedgerEvent event : events) {
            if (event.getTenantId() == null || event.getCostAmount() == null) {
                if (event.getId() != null) {
                    invalidIds.add(event.getId());
                }
                continue;
            }
            costByTenant.merge(event.getTenantId(), event.getCostAmount(), Long::sum);
        }
        LocalDateTime now = LocalDateTime.now();
        if (!invalidIds.isEmpty()) {
            ledgerRepository.updateStatus(invalidIds, STATUS_FAILED, STATUS_PENDING, now);
        }
        for (Map.Entry<String, Long> entry : costByTenant.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            int updated = tenantRepository.decreaseBalance(entry.getKey(), entry.getValue(), NOT_DELETED, now);
            if (updated <= 0) {
                log.warn("账本结算失败，租户不存在或不可更新: tenantId={}", entry.getKey());
                markEvents(events, entry.getKey(), STATUS_FAILED, now);
            }
        }
        markEvents(events, null, STATUS_APPLIED, now);
    }

    private void markEvents(List<BillingLedgerEvent> events, String tenantId, int status, LocalDateTime now) {
        List<Long> ids = events.stream()
                .filter(event -> tenantId == null || tenantId.equals(event.getTenantId()))
                .map(BillingLedgerEvent::getId)
                .filter(id -> id != null)
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        ledgerRepository.updateStatus(ids, status, STATUS_PENDING, now);
    }

    private int resolveBatchSize() {
        LingSqlProperties.Billing billing = properties.getBilling();
        if (billing == null || billing.getLedgerBatchSize() <= 0) {
            return 200;
        }
        return billing.getLedgerBatchSize();
    }
}
