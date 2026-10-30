package com.lingsql.rbac.repository;

import com.lingsql.rbac.entity.RbacUserDataScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    @Modifying(flushAutomatically = true)
    @Query("delete from RbacUserDataScope e where e.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
