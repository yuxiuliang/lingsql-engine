package com.lingsql.organization.repository;

import com.lingsql.organization.entity.UserOrgMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 用户组织归属仓库。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
public interface UserOrgMembershipRepository extends JpaRepository<UserOrgMembership, Long> {

    /**
     * 按用户查询组织归属。
     *
     * @param userId 用户ID
     * @return 归属列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    List<UserOrgMembership> findByUserIdOrderByIsPrimaryDescIdAsc(Long userId);

    /**
     * 按用户列表查询组织归属。
     *
     * @param userIds 用户ID列表
     * @return 归属列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    List<UserOrgMembership> findByUserIdIn(List<Long> userIds);

    /**
     * 按租户查询全部组织归属。
     *
     * @param tenantId 租户ID
     * @return 归属列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    List<UserOrgMembership> findByTenantId(String tenantId);

    /**
     * 删除用户的全部组织归属。
     *
     * @param userId 用户ID
     * @author 余炘洋
     * @date 2026-04-11
     */
    void deleteByUserId(Long userId);

    /**
     * 判断组织节点是否已关联用户。
     *
     * @param orgUnitId 组织节点ID
     * @return 是否存在关联
     * @author 余炘洋
     * @date 2026-04-11
     */
    boolean existsByOrgUnitId(Long orgUnitId);
}
