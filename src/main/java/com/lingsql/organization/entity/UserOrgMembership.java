package com.lingsql.organization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户组织归属实体。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_user_org")
public class UserOrgMembership {

    /**
     * 主键ID。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /**
     * 用户ID。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 组织单元ID。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "org_unit_id", nullable = false)
    private Long orgUnitId;

    /**
     * 是否主归属: 1-是, 0-否。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "is_primary")
    private Integer isPrimary;

    /**
     * 创建时间。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
