package com.lingsql.rbac.repository;

import com.lingsql.rbac.entity.RbacUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 用户角色关系仓库。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
public interface RbacUserRoleRepository extends JpaRepository<RbacUserRole, Long> {

    /**
     * 查询用户绑定的角色ID列表。
     *
     * @param userId 用户ID
     * @return 角色ID列表
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Query("select ur.roleId from RbacUserRole ur where ur.userId = :userId")
    List<Long> findRoleIdsByUserId(@Param("userId") Long userId);

    /**
     * 查询角色绑定的用户ID列表。
     *
     * @param roleId 角色ID
     * @return 用户ID列表
     * @author 余炘洋
     * @date 2026-01-28
     */
    @Query("select ur.userId from RbacUserRole ur where ur.roleId = :roleId")
    List<Long> findUserIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 删除用户角色关系。
     *
     * @param userId 用户ID
     * @author 余炘洋
     * @date 2026-01-26
     */
    void deleteByUserId(Long userId);
}
