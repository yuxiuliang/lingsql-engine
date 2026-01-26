package com.lingsql.rbac.model;

import lombok.Data;

import java.util.List;

/**
 * 登录响应。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
public class LoginResponse {

    /**
     * 访问令牌。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String token;

    /**
     * 过期时间(秒)。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private long expiresIn;

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
     * 租户名称。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String tenantName;

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
     * 角色编码列表。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private List<String> roles;

    /**
     * 权限编码列表。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private List<String> permissions;
}
