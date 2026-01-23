package com.lingsql.rbac.repository;

import com.lingsql.rbac.entity.RbacRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 角色仓库。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
public interface RbacRoleRepository extends JpaRepository<RbacRole, Long> {

    /**
     * 按角色ID列表查询角色。
     *
     * @param ids 角色ID列表
     * @return 角色列表
     * @author 余炘洋
     * @date 2026-01-23
     */
    List<RbacRole> findByIdIn(List<Long> ids);
}
