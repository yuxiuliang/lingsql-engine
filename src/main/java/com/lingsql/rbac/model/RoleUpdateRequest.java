package com.lingsql.rbac.model;

import lombok.Data;

import java.util.List;

/**
 * 角色更新请求。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class RoleUpdateRequest {

    /**
     * 角色名称。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String roleName;

    /**
     * 状态: 1-启用, 0-禁用。
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
