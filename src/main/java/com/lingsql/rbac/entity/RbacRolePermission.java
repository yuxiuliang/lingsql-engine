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
 * 角色权限关系实体。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_role_permission")
public class RbacRolePermission {

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
     * 角色ID。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    /**
     * 权限ID。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "perm_id", nullable = false)
    private Long permId;

    /**
     * 创建时间。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;
}
