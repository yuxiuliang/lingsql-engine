package com.lingsql.rbac.repository;

import com.lingsql.rbac.entity.RbacPermission;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
