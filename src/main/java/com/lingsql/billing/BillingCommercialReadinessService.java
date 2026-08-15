package com.lingsql.billing;

import com.lingsql.billing.entity.BillingPlan;
import com.lingsql.billing.model.BillingCommercialReadinessView;
import com.lingsql.billing.model.BillingReadinessCheckItem;
import com.lingsql.billing.repository.BillingLedgerEventRepository;
import com.lingsql.billing.repository.BillingPlanRepository;
import com.lingsql.billing.repository.BillingReconcileLogRepository;
import com.lingsql.config.LingSqlProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 计费商用就绪度服务。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Service
@RequiredArgsConstructor
public class BillingCommercialReadinessService {

    private static final int LEDGER_STATUS_FAILED = 2;
    private static final int LEDGER_STATUS_PENDING = 0;
    private static final int RECONCILE_STATUS_OPEN = 0;
    private static final int PLAN_STATUS_ACTIVE = 1;
    private static final int NOT_DELETED = 0;

    private final BillingLedgerEventRepository ledgerEventRepository;
    private final BillingReconcileLogRepository reconcileLogRepository;
    private final BillingPlanRepository billingPlanRepository;
    private final LingSqlProperties properties;

    /**
     * 生成计费商用验收快照。
     *
     * @param overrideWindowHours 自定义统计窗口（小时）
     * @return 商用就绪快照
     * @author 余炘洋
     * @date 2026-02-27
     */
    public BillingCommercialReadinessView snapshot(Integer overrideWindowHours) {
        LingSqlProperties.Billing billing = properties.getBilling();
        int windowHours = normalizeWindowHours(
                overrideWindowHours,
                billing == null ? null : billing.getReadinessWindowHours()
        );
        double failedRateThreshold = normalizeFailedRateThreshold(
                billing == null ? null : billing.getReadinessFailedRateThreshold()
        );
        int maxOpenReconcile = normalizeMaxOpenReconcile(
                billing == null ? null : billing.getReadinessMaxOpenReconcile()
        );
        int minLedgerEvents = billing == null ? 2 : Math.max(1, billing.getReadinessMinLedgerEvents());
        java.time.Duration pendingTimeout = billing == null || billing.getPendingTimeout() == null
                ? java.time.Duration.ofMinutes(10)
                : billing.getPendingTimeout();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusHours(windowHours);
        long ledgerTotal = ledgerEventRepository.countByCreateTimeAfter(since);
        long ledgerFailed = ledgerEventRepository.countByCreateTimeAfterAndStatus(since, LEDGER_STATUS_FAILED);
        long stalePending = ledgerEventRepository.countByStatusAndCreateTimeBefore(
                LEDGER_STATUS_PENDING,
                now.minus(pendingTimeout)
        );
        double failedRate = ledgerTotal <= 0 ? 0D : ((double) ledgerFailed / (double) ledgerTotal);
        long openReconcile = reconcileLogRepository.countByStatus(RECONCILE_STATUS_OPEN);

        List<BillingPlan> plans = billingPlanRepository.findByIsDeleted(NOT_DELETED);
        long activePlanCount = plans.stream().filter(plan -> safeStatus(plan) == PLAN_STATUS_ACTIVE).count();
        long invalidPlanCount = plans.stream().filter(this::isInvalidPlan).count();

        List<BillingReadinessCheckItem> checks = new ArrayList<>();
        checks.add(buildCheck(
                "真实计费流量",
                ledgerTotal >= minLedgerEvents,
                "窗口内账本事件 %d 条，最低要求 %d 条".formatted(ledgerTotal, minLedgerEvents)
        ));
        checks.add(buildCheck(
                "账本失败率",
                failedRate <= failedRateThreshold,
                "窗口内失败率 %.4f，阈值 %.4f".formatted(failedRate, failedRateThreshold)
        ));
        checks.add(buildCheck(
                "超时待结算账本",
                stalePending == 0,
                "超过 %s 的待结算事件 %d 条".formatted(pendingTimeout, stalePending)
        ));
        checks.add(buildCheck(
                "未处理对账积压",
                openReconcile <= maxOpenReconcile,
                "未处理对账 %d 条，阈值 %d 条".formatted(openReconcile, maxOpenReconcile)
        ));
        checks.add(buildCheck(
                "可售套餐可用性",
                activePlanCount > 0,
                "启用套餐数量 %d".formatted(activePlanCount)
        ));
        checks.add(buildCheck(
                "套餐余额合法性",
                invalidPlanCount == 0,
                "异常套餐数量 %d（默认余额为空或负数）".formatted(invalidPlanCount)
        ));

        BillingCommercialReadinessView view = new BillingCommercialReadinessView();
        view.setGeneratedAt(now);
        view.setWindowHours(windowHours);
        view.setLedgerTotal(ledgerTotal);
        view.setLedgerFailed(ledgerFailed);
        view.setStalePendingCount(stalePending);
        view.setLedgerFailedRate(failedRate);
        view.setOpenReconcileCount(openReconcile);
        view.setActivePlanCount(activePlanCount);
        view.setInvalidPlanCount(invalidPlanCount);
        view.setChecks(checks);
        view.setReady(checks.stream().allMatch(BillingReadinessCheckItem::isPassed));
        return view;
    }

    private BillingReadinessCheckItem buildCheck(String name, boolean passed, String message) {
        BillingReadinessCheckItem checkItem = new BillingReadinessCheckItem();
        checkItem.setName(name);
        checkItem.setPassed(passed);
        checkItem.setMessage(message);
        return checkItem;
    }

    private int normalizeWindowHours(Integer overrideWindowHours, Integer configWindowHours) {
        if (overrideWindowHours != null && overrideWindowHours > 0) {
            return overrideWindowHours;
        }
        if (configWindowHours != null && configWindowHours > 0) {
            return configWindowHours;
        }
        return 24;
    }

    private double normalizeFailedRateThreshold(Double threshold) {
        if (threshold == null || threshold < 0D) {
            return 0.01D;
        }
        return Math.min(threshold, 1D);
    }

    private int normalizeMaxOpenReconcile(Integer value) {
        if (value == null || value < 0) {
            return 0;
        }
        return value;
    }

    private int safeStatus(BillingPlan plan) {
        if (plan == null || plan.getStatus() == null) {
            return 0;
        }
        return plan.getStatus();
    }

    private boolean isInvalidPlan(BillingPlan plan) {
        if (plan == null) {
            return true;
        }
        Long defaultQuotaBalance = plan.getDefaultQuotaBalance();
        return defaultQuotaBalance == null || defaultQuotaBalance < 0L;
    }
}
