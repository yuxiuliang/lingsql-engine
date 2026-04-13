package com.lingsql.rbac.repository;

import com.lingsql.rbac.entity.UserScopeReleaseRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 用户数据权限发布单仓库。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
public interface UserScopeReleaseRecordRepository extends JpaRepository<UserScopeReleaseRecord, Long> {

    /**
     * 按租户查询发布单。
     *
     * @param tenantId 租户ID
     * @return 发布单列表
     * @author 余炘洋
     * @date 2026-04-13
     */
    List<UserScopeReleaseRecord> findByTenantIdOrderByCreateTimeDescIdDesc(String tenantId);

    /**
     * 按租户与状态查询发布单。
     *
     * @param tenantId 租户ID
     * @param releaseStatus 发布状态
     * @return 发布单列表
     * @author 余炘洋
     * @date 2026-04-13
     */
    List<UserScopeReleaseRecord> findByTenantIdAndReleaseStatusOrderByCreateTimeDescIdDesc(String tenantId, String releaseStatus);
}
