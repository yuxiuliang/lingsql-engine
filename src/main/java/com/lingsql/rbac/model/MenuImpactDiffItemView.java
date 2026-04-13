package com.lingsql.rbac.model;

import lombok.Data;

/**
 * 菜单影响差异项。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Data
public class MenuImpactDiffItemView {

    /**
     * 菜单编码。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String menuCode;

    /**
     * 菜单名称。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String menuName;

    /**
     * 路由路径。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String path;
}
