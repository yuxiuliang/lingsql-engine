package com.lingsql.rbac.service;

import com.lingsql.rbac.entity.RbacPermission;
import com.lingsql.rbac.model.PermissionView;
import com.lingsql.rbac.repository.RbacPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 权限管理服务。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Service
@RequiredArgsConstructor
public class PermissionAdminService {

    private final RbacPermissionRepository permissionRepository;
    private final TenantPermissionService tenantPermissionService;

    /**
     * 查询权限列表。
     *
     * @param tenantId 租户ID
     * @param sysAdmin 是否系统管理员
     * @return 权限列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    public List<PermissionView> listPermissions(String tenantId, boolean sysAdmin) {
        List<RbacPermission> permissions;
        if (sysAdmin) {
            permissions = permissionRepository.findAll();
        } else {
            permissions = tenantPermissionService.listTenantPermissions(tenantId);
        }
        return permissions.stream().map(this::toView).toList();
    }

    /**
     * 查询租户权限池。
     *
     * @param tenantId 租户ID
     * @return 权限列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    public List<PermissionView> listTenantPool(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return List.of();
        }
        return tenantPermissionService.listTenantPermissions(tenantId.trim()).stream().map(this::toView).toList();
    }

    /**
     * 转换视图。
     *
     * @param permission 权限实体
     * @return 视图对象
     * @author 余炘洋
     * @date 2026-01-26
     */
    private PermissionView toView(RbacPermission permission) {
        PermissionView view = new PermissionView();
        view.setId(permission.getId());
        view.setPermCode(permission.getPermCode());
        view.setPermName(permission.getPermName());
        view.setPermType(permission.getPermType());
        view.setStatus(permission.getStatus());
        return view;
    }
}
