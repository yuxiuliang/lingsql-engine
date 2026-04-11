package com.lingsql.rbac.repository;

import com.lingsql.rbac.entity.RbacUserDataScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户数据权限仓库。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
public interface RbacUserDataScopeRepository extends JpaRepository<RbacUserDataScope, Long> {

    /**
     * 查询用户数据权限覆盖。
     *
     * @param userId 用户ID
     * @return 数据权限
     * @author 余炘洋
     * @date 2026-01-23
     */
    Optional<RbacUserDataScope> findByUserId(Long userId);

    /**
     * 按用户删除数据权限覆盖。
     *
     * @param userId 用户ID
     * @author 余炘洋
     * @date 2026-04-11
     */
    void deleteByUserId(Long userId);
}
