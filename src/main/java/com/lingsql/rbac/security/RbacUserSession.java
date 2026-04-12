package com.lingsql.rbac.security;

import com.lingsql.rbac.model.DataScopeRule;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
     * 组织节点ID列表。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private List<Long> orgUnitIds = new ArrayList<>();

    /**
     * 直接归属组织编码列表。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> memberOrgUnitCodes = new ArrayList<>();

    /**
     * 直接归属组织类型编码列表。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> memberOrgTypeCodes = new ArrayList<>();

    /**
     * 直接归属组织标签列表。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> memberOrgTags = new ArrayList<>();

    /**
     * 直属组织及下级编码列表。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> memberOrgSubtreeCodes = new ArrayList<>();

    /**
     * 当前用户可见的组织编码列表（含关系扩展）。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private List<String> orgUnitCodes = new ArrayList<>();

    /**
     * 当前用户可见的组织及下级编码列表（含关系扩展）。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private List<String> orgSubtreeCodes = new ArrayList<>();

    /**
     * 按关系类型归集的组织可见范围。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Map<String, List<String>> relationScopes = new LinkedHashMap<>();

    /**
     * 按关系类型归集的目标组织直属编码范围。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private Map<String, List<String>> relationDirectScopes = new LinkedHashMap<>();

    /**
     * 菜单白名单(体验账号使用)。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private List<String> menuWhitelist = new ArrayList<>();
}
