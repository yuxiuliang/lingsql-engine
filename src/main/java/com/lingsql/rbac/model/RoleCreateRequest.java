package com.lingsql.rbac.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 角色创建请求。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class RoleCreateRequest {

    /**
     * 租户ID（系统管理员可指定，NULL 表示全局）。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String tenantId;

    /**
     * 角色编码。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @NotBlank(message = "角色编码不能为空")
    private String roleCode;

    /**
     * 角色名称。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @NotBlank(message = "角色名称不能为空")
    private String roleName;

    /**
     * 状态: 1-启用, 0-禁用。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Integer status;

    /**
     * 权限ID列表。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private List<Long> permIds;

    /**
     * 数据权限配置。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private DataScopeRule dataScope;
}
