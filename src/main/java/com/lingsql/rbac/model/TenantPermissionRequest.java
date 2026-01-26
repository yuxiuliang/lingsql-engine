package com.lingsql.rbac.model;

import lombok.Data;

import java.util.List;

/**
 * 租户权限池配置请求。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class TenantPermissionRequest {

    /**
     * 权限ID列表。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private List<Long> permIds;
}
