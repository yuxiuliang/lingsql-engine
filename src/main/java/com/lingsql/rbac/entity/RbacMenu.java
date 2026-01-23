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
 * 菜单实体。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_menu")
public class RbacMenu {

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
     * 租户ID，NULL 表示全局。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "tenant_id")
    private String tenantId;

    /**
     * 菜单编码。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "menu_code", nullable = false)
    private String menuCode;

    /**
     * 菜单名称。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "menu_name", nullable = false)
    private String menuName;

    /**
     * 父级菜单ID。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "parent_id")
    private Long parentId;

    /**
     * 路由路径。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "path")
    private String path;

    /**
     * 前端组件。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "component")
    private String component;

    /**
     * 菜单图标。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "icon")
    private String icon;

    /**
     * 排序。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "sort")
    private Integer sort;

    /**
     * 是否可见: 1-可见, 0-隐藏。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "visible")
    private Integer visible;

    /**
     * 状态: 1-启用, 0-禁用。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 关联权限编码。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "perm_code")
    private String permCode;

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
