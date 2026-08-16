package com.lingsql.agent.repository;

import com.lingsql.agent.model.TenantModelRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    /**
     * 统计租户当前可用于真实调用的授权模型。
     *
     * @param tenantId 租户标识
     * @param activeStatus 启用状态
     * @param probeStatus 真实探测状态
     * @param probeCutoff 探测结果最早有效时间
     * @return 可用授权数量
     */
    @Query("""
            SELECT COUNT(route)
              FROM TenantModelRoute route, AiModelConfig model
             WHERE route.tenantId = :tenantId
               AND route.status = :activeStatus
               AND model.id = route.modelConfigId
               AND model.isActive = :activeStatus
               AND model.lastProbeStatus = :probeStatus
               AND model.lastProbeTime IS NOT NULL
               AND model.lastProbeTime >= :probeCutoff
            """)
    long countUsableRoutes(@Param("tenantId") String tenantId,
                           @Param("activeStatus") Integer activeStatus,
                           @Param("probeStatus") String probeStatus,
                           @Param("probeCutoff") LocalDateTime probeCutoff);
}
