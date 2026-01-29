package com.lingsql.billing.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 计费对账日志视图。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
@Data
public class BillingReconcileLogView {

    /**
     * 主键ID。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private Long id;

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private String tenantId;

    /**
     * 请求ID。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private String requestId;

    /**
     * 预扣总额。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private Long reserveTotal;

    /**
     * CLOSE 总额。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private Long closeTotal;

    /**
     * 差异金额。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private Long diffAmount;

    /**
     * 状态: 0-未处理, 1-已处理。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private Integer status;

    /**
     * 处理备注。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private String resolveNote;

    /**
     * 处理人ID。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private Long resolveUserId;

    /**
     * 处理时间。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private LocalDateTime resolveTime;

    /**
     * 最新账本时间。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private LocalDateTime latestTime;

    /**
     * 创建时间。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private LocalDateTime updateTime;
}
