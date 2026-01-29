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
 * 计费账本事件。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_billing_ledger")
public class BillingLedgerEvent {

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
     * 请求ID。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "request_id", nullable = false)
    private String requestId;

    /**
     * 事件序号。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "event_seq", nullable = false)
    private Integer eventSeq;

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /**
     * 模型名称。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "model_name")
    private String modelName;

    /**
     * Token 数量。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "token_count")
    private Integer tokenCount;

    /**
     * 单价(点/Token)。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "unit_cost")
    private Long unitCost;

    /**
     * 计费成本。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "cost_amount")
    private Long costAmount;

    /**
     * 计费版本。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "billing_version")
    private String billingVersion;

    /**
     * 事件类型: 0-预扣, 1-退款, 2-CLOSE(对账)。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "event_type")
    private Integer eventType;

    /**
     * 状态: 0-待结算, 1-已结算, 2-失败。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 创建时间。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /**
     * 结算时间。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "apply_time")
    private LocalDateTime applyTime;
}
