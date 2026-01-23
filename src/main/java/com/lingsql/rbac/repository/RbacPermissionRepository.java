package com.lingsql.rbac.repository;

import com.lingsql.rbac.entity.RbacPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

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
}
