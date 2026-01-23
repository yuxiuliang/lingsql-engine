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
 * 体验账号策略实体。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_user_experience_policy")
public class RbacUserExperiencePolicy {

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
     * 用户ID。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 菜单白名单(JSON)。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "menu_whitelist", columnDefinition = "TEXT")
    private String menuWhitelist;

    /**
     * 数据权限覆盖(JSON)。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "data_scope_override", columnDefinition = "TEXT")
    private String dataScopeOverride;

    /**
     * 状态: 1-启用, 0-禁用。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "status")
    private Integer status;

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
