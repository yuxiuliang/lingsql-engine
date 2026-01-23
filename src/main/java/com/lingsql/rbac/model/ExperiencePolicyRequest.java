package com.lingsql.rbac.model;

import lombok.Data;

import java.util.List;

/**
 * 体验账号策略请求。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
public class ExperiencePolicyRequest {

    /**
     * 菜单白名单。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private List<String> menuWhitelist;

    /**
     * 数据权限覆盖配置。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private DataScopeRule dataScopeOverride;

    /**
     * 是否启用。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private Boolean enabled;
}
