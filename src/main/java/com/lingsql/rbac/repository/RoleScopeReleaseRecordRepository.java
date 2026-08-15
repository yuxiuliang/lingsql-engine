package com.lingsql.rbac.repository;

import com.lingsql.rbac.entity.RoleScopeReleaseRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 角色数据权限发布单仓库。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
public interface RoleScopeReleaseRecordRepository extends JpaRepository<RoleScopeReleaseRecord, Long> {

    /**
     * 按租户查询发布单。
     *
     * @param tenantId 租户ID
     * @return 发布单列表
     * @author 余炘洋
     * @date 2026-04-13
     */
    List<RoleScopeReleaseRecord> findByTenantIdOrderByCreateTimeDescIdDesc(String tenantId);

    /**
     * 按租户与状态查询发布单。
     *
     * @param tenantId 租户ID
     * @param releaseStatus 发布状态
     * @return 发布单列表
     * @author 余炘洋
     * @date 2026-04-13
     */
    List<RoleScopeReleaseRecord> findByTenantIdAndReleaseStatusOrderByCreateTimeDescIdDesc(String tenantId, String releaseStatus);
}
