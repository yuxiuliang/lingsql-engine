package com.lingsql.rbac.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_user")
public class RbacUser {

    /**
     * 主键ID。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /**
     * 账号。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "username", nullable = false)
    private String username;

    /**
     * 密码(BCrypt)。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "password", nullable = false)
    private String password;

    /**
     * 昵称。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "nickname")
    private String nickname;

    /**
     * 状态: 1-启用, 0-禁用。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 是否体验账号: 1-是, 0-否。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "is_experience")
    private Integer isExperience;

    /**
     * 最近登录时间。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;

    /**
     * 创建时间。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
