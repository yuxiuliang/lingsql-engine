package com.lingsql.billing;

import com.lingsql.billing.entity.BillingLedgerEvent;
import com.lingsql.billing.model.BillingChargeItem;
import com.lingsql.billing.repository.BillingLedgerEventRepository;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 计费资金与账本深模块。
 *
 * <p>MySQL 租户余额和不可变账本是唯一资金事实，Redis 仅在事务提交后失效并按需重建。</p>
 *
 * @author 余炘洋
 * @date 2026-01-29
 * @modify 余炘洋 2026-08-15 将预扣余额与账本收口为单一数据库事务
 */
@Service
@RequiredArgsConstructor
public class BillingLedgerService {

    private static final int NOT_DELETED = 0;
    private static final int STATUS_APPLIED = 1;
    private static final int EVENT_TYPE_RESERVE = 0;
    private static final int EVENT_TYPE_CLOSE = 2;

    private final BillingLedgerEventRepository repository;
    private final TenantRepository tenantRepository;
    private final BillingBalanceService billingBalanceService;

    /**
     * 在同一事务内完成余额条件扣减和 RESERVE 账本写入。
     *
     * @param requestId 请求ID
     * @param tenantId 租户ID
     * @param eventSeq 事件序号
     * @param items 计费明细
     * @param fallbackBalance 调用方读取到的余额快照，仅用于结果展示
     * @return 预扣结果
     */
    @Transactional
    public BillingChargeResult reserveAppliedBalance(String requestId,
                                                      String tenantId,
                                                      int eventSeq,
                                                      List<BillingChargeItem> items,
                                                      Long fallbackBalance) {
        validateCommand(requestId, tenantId, eventSeq, items);
        List<BillingLedgerEvent> existing = repository
                .findByTenantIdAndRequestIdAndEventSeqOrderByModelNameAsc(tenantId, requestId, eventSeq);
        long totalCost = totalCost(items);
        if (!existing.isEmpty()) {
            assertIdempotentReplay(existing, tenantId, EVENT_TYPE_RESERVE, items);
            return resultSnapshot(totalCost, fallbackBalance);
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = tenantRepository.decreaseBalanceIfSufficient(
                tenantId,
                totalCost,
                NOT_DELETED,
                now
        );
        if (updated != 1) {
            throw new BizException(ErrorCode.BILLING_INVALID, "余额不足，请升级套餐");
        }
        repository.saveAllAndFlush(buildEvents(
                requestId,
                tenantId,
                eventSeq,
                EVENT_TYPE_RESERVE,
                items,
                now
        ));
        evictBalanceAfterCommit(tenantId);
        return resultSnapshot(totalCost, fallbackBalance);
    }

    /**
     * 按 RESERVE 时冻结的价格快照关闭一次请求。
     *
     * @param requestId 请求ID
     * @param tenantId 租户ID
     * @param eventSeq CLOSE 事件序号
     * @param actualUsage 实际模型 Token 用量
     * @param fallbackBalance 调用方余额快照
     * @return 结算结果
     */
    @Transactional
    public BillingChargeResult closeAppliedReservation(String requestId,
                                                        String tenantId,
                                                        int eventSeq,
                                                        Map<String, Integer> actualUsage,
                                                        Long fallbackBalance) {
        if (!StringUtils.hasText(requestId) || !StringUtils.hasText(tenantId) || eventSeq <= 0) {
            throw new BizException(ErrorCode.BILLING_INVALID, "计费结算参数不完整");
        }
        List<BillingLedgerEvent> reserveEvents = repository
                .findByTenantIdAndRequestIdAndEventTypeOrderByIdAsc(
                        tenantId,
                        requestId,
                        EVENT_TYPE_RESERVE
                );
        if (reserveEvents.isEmpty()) {
            throw new BizException(ErrorCode.BILLING_INVALID, "未找到可结算的预扣账本");
        }
        List<BillingChargeItem> reservedItems = aggregateReserveItems(reserveEvents);
        assertUsageMatches(reservedItems, actualUsage);
        recordCloseEvents(requestId, tenantId, eventSeq, reservedItems);
        return resultSnapshot(totalCost(reservedItems), fallbackBalance);
    }

    private boolean recordCloseEvents(String requestId,
                                      String tenantId,
                                      int eventSeq,
                                      List<BillingChargeItem> items) {
        validateCommand(requestId, tenantId, eventSeq, items);
        List<BillingLedgerEvent> existing = repository
                .findByTenantIdAndRequestIdAndEventSeqOrderByModelNameAsc(tenantId, requestId, eventSeq);
        if (!existing.isEmpty()) {
            assertIdempotentReplay(existing, tenantId, EVENT_TYPE_CLOSE, items);
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        repository.saveAllAndFlush(buildEvents(
                requestId,
                tenantId,
                eventSeq,
                EVENT_TYPE_CLOSE,
                items,
                now
        ));
        return true;
    }

    /**
     * 根据已应用的 RESERVE 事件恢复缺失 CLOSE。
     *
     * @param requestId 请求ID
     * @param tenantId 租户ID
     * @return 是否已恢复或原本已关闭
     */
    @Transactional
    public boolean recoverMissingClose(String requestId, String tenantId) {
        if (!StringUtils.hasText(requestId) || !StringUtils.hasText(tenantId)) {
            return false;
        }
        List<BillingLedgerEvent> closeEvents = repository
                .findByTenantIdAndRequestIdAndEventTypeOrderByIdAsc(tenantId, requestId, EVENT_TYPE_CLOSE);
        if (!closeEvents.isEmpty()) {
            return true;
        }
        List<BillingLedgerEvent> reserveEvents = repository
                .findByTenantIdAndRequestIdAndEventTypeOrderByIdAsc(tenantId, requestId, EVENT_TYPE_RESERVE);
        if (reserveEvents.isEmpty()) {
            return false;
        }
        int nextEventSeq = reserveEvents.stream()
                .map(BillingLedgerEvent::getEventSeq)
                .filter(value -> value != null && value > 0)
                .max(Integer::compareTo)
                .orElse(0) + 1;
        List<BillingChargeItem> items = aggregateReserveItems(reserveEvents);
        return recordCloseEvents(requestId, tenantId, nextEventSeq, items);
    }

    private void validateCommand(String requestId,
                                 String tenantId,
                                 int eventSeq,
                                 List<BillingChargeItem> items) {
        if (!StringUtils.hasText(requestId)
                || requestId.trim().length() > 64
                || !StringUtils.hasText(tenantId)
                || eventSeq <= 0
                || CollectionUtils.isEmpty(items)
                || items.stream().anyMatch(item -> item == null
                        || !StringUtils.hasText(item.modelName())
                        || item.tokenCount() <= 0
                        || item.costAmount() <= 0)) {
            throw new BizException(ErrorCode.BILLING_INVALID, "计费账本参数不完整");
        }
        if (totalCost(items) <= 0) {
            throw new BizException(ErrorCode.BILLING_INVALID, "计费金额必须大于零");
        }
    }

    private List<BillingLedgerEvent> buildEvents(String requestId,
                                                 String tenantId,
                                                 int eventSeq,
                                                 int eventType,
                                                 List<BillingChargeItem> items,
                                                 LocalDateTime now) {
        List<BillingLedgerEvent> events = new ArrayList<>();
        for (BillingChargeItem item : items) {
            BillingLedgerEvent event = new BillingLedgerEvent();
            event.setRequestId(requestId.trim());
            event.setEventSeq(eventSeq);
            event.setTenantId(tenantId.trim());
            event.setModelName(item.modelName());
            event.setTokenCount(item.tokenCount());
            event.setUnitCost(item.unitCost());
            event.setCostAmount(item.costAmount());
            event.setBillingVersion(item.policyVersion());
            event.setStatus(STATUS_APPLIED);
            event.setEventType(eventType);
            event.setCreateTime(now);
            event.setApplyTime(now);
            events.add(event);
        }
        return events;
    }

    private void assertIdempotentReplay(List<BillingLedgerEvent> existing,
                                        String tenantId,
                                        int eventType,
                                        List<BillingChargeItem> items) {
        boolean sameTenantAndType = existing.stream().allMatch(event ->
                tenantId.equals(event.getTenantId())
                        && Integer.valueOf(eventType).equals(event.getEventType()));
        if (!sameTenantAndType || !fingerprint(existing).equals(fingerprintItems(items))) {
            throw new BizException(ErrorCode.BILLING_INVALID, "计费幂等键已存在但内容不一致");
        }
    }

    private Map<String, String> fingerprint(List<BillingLedgerEvent> events) {
        Map<String, String> values = new TreeMap<>();
        for (BillingLedgerEvent event : events) {
            values.put(event.getModelName(), String.join(":",
                    String.valueOf(event.getTokenCount()),
                    String.valueOf(event.getUnitCost()),
                    String.valueOf(event.getCostAmount()),
                    String.valueOf(event.getBillingVersion())));
        }
        return values;
    }

    private Map<String, String> fingerprintItems(List<BillingChargeItem> items) {
        Map<String, String> values = new TreeMap<>();
        for (BillingChargeItem item : items) {
            values.put(item.modelName(), String.join(":",
                    String.valueOf(item.tokenCount()),
                    String.valueOf(item.unitCost()),
                    String.valueOf(item.costAmount()),
                    String.valueOf(item.policyVersion())));
        }
        return values;
    }

    private void assertUsageMatches(List<BillingChargeItem> reservedItems,
                                    Map<String, Integer> actualUsage) {
        Map<String, Integer> reserved = new TreeMap<>();
        for (BillingChargeItem item : reservedItems) {
            reserved.merge(item.modelName(), item.tokenCount(), Math::addExact);
        }
        Map<String, Integer> actual = new TreeMap<>();
        if (actualUsage != null) {
            actualUsage.forEach((model, tokens) -> {
                if (StringUtils.hasText(model) && tokens != null && tokens > 0) {
                    actual.merge(model.trim().toUpperCase(), tokens, Math::addExact);
                }
            });
        }
        if (!reserved.equals(actual)) {
            throw new BizException(ErrorCode.BILLING_INVALID, "实际模型用量与预扣账本不一致，请人工核对");
        }
    }

    private List<BillingChargeItem> aggregateReserveItems(List<BillingLedgerEvent> events) {
        Map<String, MutableCharge> totals = new LinkedHashMap<>();
        for (BillingLedgerEvent event : events) {
            MutableCharge total = totals.computeIfAbsent(
                    event.getModelName(),
                    ignored -> new MutableCharge(event.getUnitCost(), event.getBillingVersion())
            );
            total.tokens = Math.addExact(total.tokens, valueOrZero(event.getTokenCount()));
            total.cost = Math.addExact(total.cost, valueOrZero(event.getCostAmount()));
        }
        return totals.entrySet().stream()
                .map(entry -> new BillingChargeItem(
                        entry.getKey(),
                        entry.getValue().tokens,
                        entry.getValue().unitCost,
                        entry.getValue().cost,
                        entry.getValue().policyVersion
                ))
                .toList();
    }

    private long totalCost(List<BillingChargeItem> items) {
        try {
            long total = 0L;
            for (BillingChargeItem item : items) {
                total = Math.addExact(total, item == null ? 0L : item.costAmount());
            }
            return total;
        } catch (ArithmeticException ex) {
            throw new BizException(ErrorCode.BILLING_INVALID, "计费金额超过系统上限", ex);
        }
    }

    private BillingChargeResult resultSnapshot(long cost, Long fallbackBalance) {
        long before = fallbackBalance == null ? 0L : Math.max(0L, fallbackBalance);
        return BillingChargeResult.success(cost, before, Math.max(0L, before - cost));
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private void evictBalanceAfterCommit(String tenantId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            billingBalanceService.evictBalance(tenantId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                billingBalanceService.evictBalance(tenantId);
            }
        });
    }

    private static final class MutableCharge {
        private final Long unitCost;
        private final String policyVersion;
        private int tokens;
        private long cost;

        private MutableCharge(Long unitCost, String policyVersion) {
            this.unitCost = unitCost;
            this.policyVersion = policyVersion;
        }
    }
}
