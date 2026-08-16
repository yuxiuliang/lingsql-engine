package com.lingsql.rbac.repository;

import com.lingsql.rbac.entity.RbacPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 权限仓库。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
public interface RbacPermissionRepository extends JpaRepository<RbacPermission, Long> {

    /**
     * 按权限ID列表查询权限。
     *
     * @param ids 权限ID列表
     * @return 权限列表
     * @author 余炘洋
     * @date 2026-01-23
     */
    List<RbacPermission> findByIdIn(List<Long> ids);

    /**
     * 按权限编码查询。
     *
     * @param permCode 权限编码
     * @return 权限信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    Optional<RbacPermission> findByPermCode(String permCode);

    /**
     * 按权限编码列表查询。
     *
     * @param permCodes 权限编码列表
     * @return 权限列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    List<RbacPermission> findByPermCodeIn(List<String> permCodes);

    /**
     * 查询指定租户角色在角色授权与租户权限池中同时生效的权限编码。
     *
     * @param tenantId 租户标识
     * @param roleCode 角色编码
     * @param activeStatus 启用状态
     * @param permissionCodes 待核验权限编码
     * @return 同时生效的权限编码
     */
    @Query("""
            SELECT DISTINCT permission.permCode
              FROM RbacPermission permission,
                   RbacRole role,
                   RbacRolePermission rolePermission,
                   RbacTenantPermission tenantPermission
             WHERE role.tenantId = :tenantId
               AND role.roleCode = :roleCode
               AND role.status = :activeStatus
               AND rolePermission.roleId = role.id
               AND rolePermission.permId = permission.id
               AND tenantPermission.tenantId = :tenantId
               AND tenantPermission.permId = permission.id
               AND permission.status = :activeStatus
               AND permission.permCode IN :permissionCodes
            """)
    List<String> findEffectiveTenantRolePermissionCodes(
            @Param("tenantId") String tenantId,
            @Param("roleCode") String roleCode,
            @Param("activeStatus") Integer activeStatus,
            @Param("permissionCodes") List<String> permissionCodes
    );
}
