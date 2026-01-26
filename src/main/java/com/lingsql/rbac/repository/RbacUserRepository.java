package com.lingsql.rbac.repository;

import com.lingsql.rbac.entity.RbacUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户仓库。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
public interface RbacUserRepository extends JpaRepository<RbacUser, Long> {

    /**
     * 按租户与账号查询用户。
     *
     * @param tenantId 租户ID
     * @param username 账号
     * @return 用户信息
     * @author 余炘洋
     * @date 2026-01-23
     */
    Optional<RbacUser> findByTenantIdAndUsername(String tenantId, String username);

    /**
     * 按租户与关键词分页查询用户。
     *
     * @param tenantId 租户ID
     * @param keyword  关键词
     * @param pageable 分页参数
     * @return 用户分页
     * @author 余炘洋
     * @date 2026-01-26
     */
    Page<RbacUser> findByTenantIdAndUsernameContaining(String tenantId, String keyword, Pageable pageable);

    /**
     * 按租户分页查询用户。
     *
     * @param tenantId 租户ID
     * @param pageable 分页参数
     * @return 用户分页
     * @author 余炘洋
     * @date 2026-01-26
     */
    Page<RbacUser> findByTenantId(String tenantId, Pageable pageable);
}
