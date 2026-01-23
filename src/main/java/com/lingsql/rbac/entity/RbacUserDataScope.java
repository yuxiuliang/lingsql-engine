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
 * 用户数据权限覆盖实体。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_user_data_scope")
public class RbacUserDataScope {

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
     * 范围类型: ALL/TENANT/SELF/CUSTOM。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "scope_type", nullable = false)
    private String scopeType;

    /**
     * SELF 默认字段名。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "scope_column")
    private String scopeColumn;

    /**
     * CUSTOM SQL 条件。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "scope_rule", columnDefinition = "TEXT")
    private String scopeRule;

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
