package com.lingsql.rbac.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 当前登录用户信息视图。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
public class UserProfileView {

    /**
     * 用户ID。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private Long userId;

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String tenantId;

    /**
     * 账号。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String username;

    /**
     * 昵称。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String nickname;

    /**
     * 是否体验账号。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private boolean experience;

    /**
     * 角色列表。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private List<String> roles;

    /**
     * 权限列表。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private List<String> permissions;

    /**
     * 数据权限覆盖。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private DataScopeRule dataScope;

    /**
     * 按关系类型归集的组织可见范围。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Map<String, List<String>> relationScopes = new LinkedHashMap<>();
}
