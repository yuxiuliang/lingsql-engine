package com.lingsql.rbac.model;

import lombok.Data;

/**
 * 权限视图。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class PermissionView {

    /**
     * 权限ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Long id;

    /**
     * 权限编码。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String permCode;

    /**
     * 权限名称。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String permName;

    /**
     * 权限类型。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String permType;

    /**
     * 状态。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Integer status;
}
