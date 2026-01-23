package com.lingsql.rbac.model;

import lombok.Data;

import java.util.List;

/**
 * 体验账号策略响应。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
public class ExperiencePolicyView {

    /**
     * 用户ID。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private Long userId;

    /**
     * 菜单白名单。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private List<String> menuWhitelist;

    /**
     * 数据权限覆盖。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private DataScopeRule dataScopeOverride;

    /**
     * 状态: 1-启用, 0-禁用。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private Integer status;
}
