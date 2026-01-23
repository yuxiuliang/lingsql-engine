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
}
