package com.lingsql.tenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户实体。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_tenant")
public class TenantInfo {

    /**
     * 主键ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "tenant_id", nullable = false, unique = true)
    private String tenantId;

    /**
     * 租户名称。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "tenant_name", nullable = false)
    private String tenantName;

    /**
     * 状态: 1-启用, 0-停用。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 备注。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "remark")
    private String remark;

    /**
     * 联系人姓名。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "contact_name")
    private String contactName;

    /**
     * 联系电话。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "contact_phone")
    private String contactPhone;

    /**
     * 联系邮箱。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "contact_email")
    private String contactEmail;

    /**
     * 到期时间。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "expire_time")
    private LocalDateTime expireTime;

    /**
     * 套餐名称。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "plan_name")
    private String planName;

    /**
     * 余额。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "quota_balance")
    private Long quotaBalance;

    /**
     * 服务等级。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "service_level")
    private String serviceLevel;

    /**
     * 租户类型。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "tenant_type")
    private String tenantType;

    /**
     * 是否删除: 1-已删除, 0-正常。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "is_deleted")
    private Integer isDeleted;

    /**
     * 创建时间。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
