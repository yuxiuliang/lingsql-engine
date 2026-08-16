package com.lingsql.rbac.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 登录响应。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
public class LoginResponse {

    /**
     * 客户端下一步必须执行的认证流程。
     */
    private AuthFlow authFlow = AuthFlow.AUTHENTICATED;

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
     * Refresh Token。
     * 启用 HttpOnly Cookie 承载时，响应体不会回传该字段。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String refreshToken;

    /**
     * Refresh Token 过期时间(秒)。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private long refreshExpiresIn;

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

    /**
     * 按关系类型归集的组织可见范围。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Map<String, List<String>> relationScopes = new LinkedHashMap<>();
}
