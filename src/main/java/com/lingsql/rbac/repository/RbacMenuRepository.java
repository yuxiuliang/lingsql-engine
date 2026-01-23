package com.lingsql.rbac.repository;

import com.lingsql.rbac.entity.RbacMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 菜单仓库。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
public interface RbacMenuRepository extends JpaRepository<RbacMenu, Long> {

    /**
     * 查询租户可用菜单(含全局)。
     *
     * @param tenantId 租户ID
     * @return 菜单列表
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Query("select m from RbacMenu m where (m.tenantId = :tenantId or m.tenantId is null) and m.status = 1")
    List<RbacMenu> findActiveMenus(@Param("tenantId") String tenantId);
}
