package com.lingsql.rbac.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单治理视图模型。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Data
public class MenuAdminView {

    /**
     * 菜单ID。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private Long id;

    /**
     * 租户ID，空表示全局菜单。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String tenantId;

    /**
     * 菜单编码。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String menuCode;

    /**
     * 菜单名称。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String menuName;

    /**
     * 父级菜单ID。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private Long parentId;

    /**
     * 父级菜单编码。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String parentMenuCode;

    /**
     * 路由路径。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String path;

    /**
     * 前端组件。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String component;

    /**
     * 图标。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String icon;

    /**
     * 排序。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private Integer sort;

    /**
     * 是否可见。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private Integer visible;

    /**
     * 状态。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private Integer status;

    /**
     * 权限编码。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private String permCode;

    /**
     * 适配的组织类型编码列表。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> orgTypeCodes = new ArrayList<>();

    /**
     * 适配的组织编码列表。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> orgUnitCodes = new ArrayList<>();

    /**
     * 适配的组织标签列表。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> orgTags = new ArrayList<>();

    /**
     * 要求具备的组织关系类型列表。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> requiredRelationTypes = new ArrayList<>();

    /**
     * 子菜单。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<MenuAdminView> children = new ArrayList<>();
}
