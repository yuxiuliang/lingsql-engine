package com.lingsql.tenant.repository;

import com.lingsql.tenant.entity.TenantNotificationGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 租户通知分组仓库。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
public interface TenantNotificationGroupRepository extends JpaRepository<TenantNotificationGroup, Long> {

    List<TenantNotificationGroup> findByIsDeleted(Integer isDeleted);

    List<TenantNotificationGroup> findByTenantIdAndIsDeleted(String tenantId, Integer isDeleted);

    Optional<TenantNotificationGroup> findByIdAndIsDeleted(Long id, Integer isDeleted);

    Optional<TenantNotificationGroup> findByTenantIdAndGroupCodeAndIsDeleted(String tenantId, String groupCode, Integer isDeleted);

    boolean existsByTenantIdAndGroupCodeAndIsDeleted(String tenantId, String groupCode, Integer isDeleted);
}
