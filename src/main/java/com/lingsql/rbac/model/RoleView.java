package com.lingsql.rbac.model;

import lombok.Data;

import java.util.List;

/**
 * 角色视图。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class RoleView {

    /**
     * 角色ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Long id;

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String tenantId;

    /**
     * 角色编码。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String roleCode;

    /**
     * 角色名称。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String roleName;

    /**
     * 状态。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Integer status;

    /**
     * 权限ID列表。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private List<Long> permIds;

    /**
     * 数据权限配置。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private DataScopeRule dataScope;
}
