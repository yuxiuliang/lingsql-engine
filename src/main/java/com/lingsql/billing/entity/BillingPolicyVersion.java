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
 * 计费策略版本实体。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_billing_policy_version")
public class BillingPolicyVersion {

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
     * 版本号。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "version_code", nullable = false, unique = true)
    private String versionCode;

    /**
     * 状态: 1-生效, 0-停用。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 生效时间。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "effective_time")
    private LocalDateTime effectiveTime;

    /**
     * 模型单价配置(JSON)。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "model_unit_cost", columnDefinition = "TEXT")
    private String modelUnitCost;

    /**
     * 服务等级限额(JSON)。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "service_level_limit", columnDefinition = "TEXT")
    private String serviceLevelLimit;

    /**
     * 租户类型加权(JSON)。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "tenant_type_weight", columnDefinition = "TEXT")
    private String tenantTypeWeight;

    /**
     * 默认单价(点/Token)。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "default_unit_cost")
    private Long defaultUnitCost;

    /**
     * 默认每日调用上限。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "default_daily_limit")
    private Integer defaultDailyLimit;

    /**
     * 默认每日 Token 上限。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "default_daily_token_limit")
    private Long defaultDailyTokenLimit;

    /**
     * 默认租户权重。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "default_tenant_weight", precision = 10, scale = 4)
    private BigDecimal defaultTenantWeight;

    /**
     * 说明。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "description")
    private String description;

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
