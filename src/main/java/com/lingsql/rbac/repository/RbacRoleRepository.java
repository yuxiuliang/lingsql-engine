package com.lingsql.rbac.repository;

import com.lingsql.rbac.entity.RbacRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

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

    /**
     * 按租户查询角色。
     *
     * @param tenantId 租户ID
     * @return 角色列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    List<RbacRole> findByTenantId(String tenantId);

    /**
     * 按租户与编码查询角色。
     *
     * @param tenantId 租户ID
     * @param roleCode 角色编码
     * @return 角色信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    Optional<RbacRole> findByTenantIdAndRoleCode(String tenantId, String roleCode);

    /**
     * 按编码查询全局角色。
     *
     * @param roleCode 角色编码
     * @return 角色信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    Optional<RbacRole> findByTenantIdIsNullAndRoleCode(String roleCode);
}
