package com.lingsql.rbac.repository;

import com.lingsql.rbac.entity.RbacMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * 菜单仓库。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
public interface RbacMenuRepository extends JpaRepository<RbacMenu, Long> {

    /**
     * 查询租户覆盖菜单。
     *
     * @param tenantId 租户ID
     * @return 菜单列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    List<RbacMenu> findByTenantId(String tenantId);

    /**
     * 查询全局菜单。
     *
     * @return 菜单列表
     * @author 余炘洋
     * @date 2026-01-27
     */
    List<RbacMenu> findByTenantIdIsNull();

    /**
     * 按菜单编码查询全局菜单。
     *
     * @param menuCode 菜单编码
     * @return 菜单信息
     * @author 余炘洋
     * @date 2026-01-27
     */
    java.util.Optional<RbacMenu> findByTenantIdIsNullAndMenuCode(String menuCode);

    /**
     * 按租户和菜单编码查询菜单。
     *
     * @param tenantId 租户ID
     * @param menuCode 菜单编码
     * @return 菜单信息
     * @author 余炘洋
     * @date 2026-04-12
     */
    java.util.Optional<RbacMenu> findByTenantIdAndMenuCode(String tenantId, String menuCode);

    /**
     * 删除租户菜单覆盖。
     *
     * @param tenantId 租户ID
     * @param menuCode 菜单编码
     * @author 余炘洋
     * @date 2026-04-12
     */
    void deleteByTenantIdAndMenuCode(String tenantId, String menuCode);
}
