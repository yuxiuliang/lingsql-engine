package com.lingsql.agent.repository;

import com.lingsql.agent.model.TenantModelRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 租户模型授权路由仓库。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
public interface TenantModelRouteRepository extends JpaRepository<TenantModelRoute, Long> {

    List<TenantModelRoute> findByTenantIdAndStatusOrderByRoutePriorityAscIdAsc(String tenantId, Integer status);

    List<TenantModelRoute> findByTenantIdOrderByRoutePriorityAscIdAsc(String tenantId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM TenantModelRoute route WHERE route.tenantId = :tenantId")
    int deleteByTenantId(@Param("tenantId") String tenantId);

    boolean existsByModelConfigId(Long modelConfigId);
}
