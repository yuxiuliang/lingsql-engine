package com.lingsql.rbac.repository;

import com.lingsql.rbac.entity.RbacUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
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
     * 按账号查询最多两条用户，用于判断账号唯一性。
     *
     * @param username 账号
     * @return 用户列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    List<RbacUser> findTop2ByUsername(String username);

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

    /**
     * 按租户查询全部用户（按 ID 升序）。
     *
     * @param tenantId 租户ID
     * @return 用户列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    List<RbacUser> findByTenantIdOrderByIdAsc(String tenantId);

    /**
     * 统计租户下指定状态用户数量。
     *
     * @param tenantId 租户ID
     * @param status   状态
     * @return 用户数量
     * @author 余炘洋
     * @date 2026-01-26
     */
    long countByTenantIdAndStatus(String tenantId, Integer status);

    /**
     * 统计当前可登录且绑定指定有效角色的租户账号。
     *
     * @param tenantId 租户标识
     * @param roleCode 角色编码
     * @param activeStatus 启用状态
     * @param now 当前时间
     * @return 可登录账号数量
     */
    @Query("""
            SELECT COUNT(u)
              FROM RbacUser u, RbacUserRole ur, RbacRole r
             WHERE u.tenantId = :tenantId
               AND u.status = :activeStatus
               AND (u.lockUntil IS NULL OR u.lockUntil <= :now)
               AND ur.userId = u.id
               AND r.id = ur.roleId
               AND r.tenantId = :tenantId
               AND r.roleCode = :roleCode
               AND r.status = :activeStatus
            """)
    long countLoginReadyUsersByTenantAndRole(@Param("tenantId") String tenantId,
                                              @Param("roleCode") String roleCode,
                                              @Param("activeStatus") Integer activeStatus,
                                              @Param("now") LocalDateTime now);
}
