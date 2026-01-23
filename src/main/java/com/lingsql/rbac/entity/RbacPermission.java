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
 * 权限实体。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_permission")
public class RbacPermission {

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
     * 权限编码。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "perm_code", nullable = false)
    private String permCode;

    /**
     * 权限名称。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "perm_name", nullable = false)
    private String permName;

    /**
     * 权限类型: MENU/API/BUTTON/DATA。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "perm_type", nullable = false)
    private String permType;

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
