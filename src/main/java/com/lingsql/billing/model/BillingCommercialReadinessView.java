package com.lingsql.billing.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 计费商用验收快照。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Data
public class BillingCommercialReadinessView {

    /**
     * 报告生成时间。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private LocalDateTime generatedAt;

    /**
     * 统计窗口小时数。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private int windowHours;

    /**
     * 窗口内账本总事件数。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private long ledgerTotal;

    /**
     * 窗口内账本失败事件数。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private long ledgerFailed;

    /**
     * 超时未结算的遗留账本事件数量。
     *
     * @author 余炘洋
     * @date 2026-08-15
     */
    private long stalePendingCount;

    /**
     * 账本失败率，范围 [0,1]。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private double ledgerFailedRate;

    /**
     * 未处理对账日志数量。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private long openReconcileCount;

    /**
     * 启用套餐数量。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private long activePlanCount;

    /**
     * 异常套餐数量（余额为空或负数）。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private long invalidPlanCount;

    /**
     * 计费商用验收是否通过。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private boolean ready;

    /**
     * 逐项检查结果。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private List<BillingReadinessCheckItem> checks = new ArrayList<>();
}
