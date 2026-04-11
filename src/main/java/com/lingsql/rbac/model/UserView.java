package com.lingsql.rbac.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class UserView {

    /**
     * 用户ID。
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
     * 账号。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String username;

    /**
     * 昵称。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String nickname;

    /**
     * 状态。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Integer status;

    /**
     * 是否体验账号。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Integer isExperience;

    /**
     * 最近登录时间。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private LocalDateTime lastLoginTime;

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
     * 组织名称列表。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private List<String> orgUnitNames;

    /**
     * 数据权限覆盖配置。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private DataScopeRule dataScope;

    /**
     * 当前生效的数据权限。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private DataScopeRule effectiveDataScope;

    /**
     * 当前生效数据权限来源。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String effectiveDataScopeSource;
}
