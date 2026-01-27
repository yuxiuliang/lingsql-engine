package com.lingsql.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 租户计费覆盖配置。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_billing_tenant_override")
public class TenantBillingOverride {

    /**
     * 主键ID。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "tenant_id", nullable = false, unique = true)
    private String tenantId;

    /**
     * 加价倍率。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "price_multiplier", precision = 10, scale = 4)
    private BigDecimal priceMultiplier;

    /**
     * 状态: 1-生效, 0-停用。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 备注。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "remark")
    private String remark;

    /**
     * 创建时间。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
