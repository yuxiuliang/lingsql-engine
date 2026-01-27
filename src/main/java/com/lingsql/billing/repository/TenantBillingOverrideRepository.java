package com.lingsql.billing.repository;

import com.lingsql.billing.entity.TenantBillingOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 租户计费覆盖仓库。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
public interface TenantBillingOverrideRepository extends JpaRepository<TenantBillingOverride, Long> {

    /**
     * 查询生效的租户覆盖。
     *
     * @param tenantId 租户ID
     * @param status   状态
     * @return 覆盖配置
     * @author 余炘洋
     * @date 2026-01-27
     */
    Optional<TenantBillingOverride> findByTenantIdAndStatus(String tenantId, Integer status);

    /**
     * 按租户ID查询覆盖配置。
     *
     * @param tenantId 租户ID
     * @return 覆盖配置
     * @author 余炘洋
     * @date 2026-01-27
     */
    Optional<TenantBillingOverride> findByTenantId(String tenantId);
}
