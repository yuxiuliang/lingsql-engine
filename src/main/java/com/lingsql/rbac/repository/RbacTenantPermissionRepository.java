package com.lingsql.rbac.repository;

import com.lingsql.rbac.entity.RbacTenantPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 租户权限池仓库。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
public interface RbacTenantPermissionRepository extends JpaRepository<RbacTenantPermission, Long> {

    /**
     * 查询租户权限ID列表。
     *
     * @param tenantId 租户ID
     * @return 权限ID列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Query("select tp.permId from RbacTenantPermission tp where tp.tenantId = :tenantId")
    List<Long> findPermIdsByTenantId(@Param("tenantId") String tenantId);

    /**
     * 按租户查询权限池明细。
     *
     * @param tenantId 租户ID
     * @return 权限池列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    List<RbacTenantPermission> findByTenantId(String tenantId);

    /**
     * 删除租户权限池。
     *
     * @param tenantId 租户ID
     * @author 余炘洋
     * @date 2026-01-26
     */
    void deleteByTenantId(String tenantId);
}
