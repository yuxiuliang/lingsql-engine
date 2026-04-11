package com.lingsql.rbac.repository;

import com.lingsql.rbac.entity.RbacRoleDataScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 角色数据权限仓库。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
public interface RbacRoleDataScopeRepository extends JpaRepository<RbacRoleDataScope, Long> {

    /**
     * 查询角色数据权限配置。
     *
     * @param roleIds 角色ID列表
     * @return 数据权限列表
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Query("select ds from RbacRoleDataScope ds where ds.roleId in :roleIds and ds.status = 1")
    List<RbacRoleDataScope> findActiveByRoleIds(@Param("roleIds") List<Long> roleIds);

    /**
     * 按角色删除数据权限配置。
     *
     * @param roleId 角色ID
     * @author 余炘洋
     * @date 2026-04-11
     */
    void deleteByRoleId(Long roleId);
}
