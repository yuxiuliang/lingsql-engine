package com.lingsql.rbac.repository;

import com.lingsql.rbac.entity.RbacRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 角色权限关系仓库。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
public interface RbacRolePermissionRepository extends JpaRepository<RbacRolePermission, Long> {

    /**
     * 查询角色绑定的权限ID列表。
     *
     * @param roleIds 角色ID列表
     * @return 权限ID列表
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Query("select rp.permId from RbacRolePermission rp where rp.roleId in :roleIds")
    List<Long> findPermIdsByRoleIds(@Param("roleIds") List<Long> roleIds);

    /**
     * 删除角色权限关系。
     *
     * @param roleId 角色ID
     * @author 余炘洋
     * @date 2026-01-26
     */
    void deleteByRoleId(Long roleId);

    /**
     * 按角色ID列表查询角色权限关系。
     *
     * @param roleIds 角色ID列表
     * @return 关系列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    List<com.lingsql.rbac.entity.RbacRolePermission> findByRoleIdIn(List<Long> roleIds);
}
