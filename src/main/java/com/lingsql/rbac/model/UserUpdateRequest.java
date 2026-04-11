package com.lingsql.rbac.model;

import lombok.Data;

import java.util.List;

/**
 * 用户更新请求。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class UserUpdateRequest {

    /**
     * 昵称。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String nickname;

    /**
     * 状态: 1-启用, 0-禁用。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Integer status;

    /**
     * 是否体验账号: 1-是, 0-否。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Integer isExperience;

    /**
     * 角色ID列表。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private List<Long> roleIds;

    /**
     * 组织节点ID列表。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private List<Long> orgUnitIds;

    /**
     * 主归属组织节点ID。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Long primaryOrgUnitId;

    /**
     * 数据权限覆盖配置。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private DataScopeRule dataScope;
}
