package com.lingsql.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 计费对账日志。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_billing_reconcile_log")
public class BillingReconcileLog {

    /**
     * 主键ID。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "tenant_id")
    private String tenantId;

    /**
     * 请求ID。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "request_id", nullable = false)
    private String requestId;

    /**
     * 预扣总额。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "reserve_total")
    private Long reserveTotal;

    /**
     * CLOSE 总额。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "close_total")
    private Long closeTotal;

    /**
     * 差异金额。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "diff_amount")
    private Long diffAmount;

    /**
     * 状态: 0-未处理, 1-已处理。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 处理备注。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "resolve_note")
    private String resolveNote;

    /**
     * 处理人ID。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "resolve_user_id")
    private Long resolveUserId;

    /**
     * 处理时间。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "resolve_time")
    private LocalDateTime resolveTime;

    /**
     * 最新账本时间。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "latest_time")
    private LocalDateTime latestTime;

    /**
     * 创建时间。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
