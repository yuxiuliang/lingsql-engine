package com.lingsql.rbac.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单视图模型。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
public class MenuView {

    /**
     * 菜单ID。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private Long id;

    /**
     * 菜单编码。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String menuCode;

    /**
     * 菜单名称。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String menuName;

    /**
     * 路由路径。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String path;

    /**
     * 前端组件。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String component;

    /**
     * 图标。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String icon;

    /**
     * 排序。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private Integer sort;

    /**
     * 是否可见。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private Integer visible;

    /**
     * 子菜单列表。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private List<MenuView> children = new ArrayList<>();
}
