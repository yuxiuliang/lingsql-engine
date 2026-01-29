package com.lingsql.billing.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 计费对账日志查询。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
@Data
public class BillingReconcileLogQuery {

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
     * 状态: 0-未处理, 1-已处理。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private Integer status;

    /**
     * 开始时间。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private LocalDateTime startTime;

    /**
     * 结束时间。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private LocalDateTime endTime;
}
