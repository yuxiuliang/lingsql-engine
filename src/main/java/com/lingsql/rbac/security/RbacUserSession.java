package com.lingsql.rbac.security;

import com.lingsql.rbac.model.DataScopeRule;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 登录用户会话模型。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
public class RbacUserSession implements Serializable {

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
     * 角色编码列表。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private List<String> roles = new ArrayList<>();

    /**
     * 权限编码列表。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private List<String> permissions = new ArrayList<>();

    /**
     * 数据权限规则。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private DataScopeRule dataScope;

    /**
     * 菜单白名单(体验账号使用)。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private List<String> menuWhitelist = new ArrayList<>();
}
