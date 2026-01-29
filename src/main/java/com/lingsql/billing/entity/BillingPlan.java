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
 * 套餐定义。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_billing_plan")
public class BillingPlan {

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
     * 套餐编码。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "plan_code", nullable = false, unique = true)
    private String planCode;

    /**
     * 套餐名称。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "plan_name", nullable = false)
    private String planName;

    /**
     * 状态: 1-启用, 0-停用。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 服务等级。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "service_level")
    private String serviceLevel;

    /**
     * 租户类型。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "tenant_type")
    private String tenantType;

    /**
     * 默认余额。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "default_quota_balance")
    private Long defaultQuotaBalance;

    /**
     * 备注。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "remark")
    private String remark;

    /**
     * 是否删除: 1-已删除, 0-正常。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Column(name = "is_deleted")
    private Integer isDeleted;

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
