package com.lingsql.agent.repository;

import com.lingsql.agent.entity.AgentPluginGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 租户插件授权仓储。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
public interface AgentPluginGrantRepository extends JpaRepository<AgentPluginGrant, Long> {

    Optional<AgentPluginGrant> findByTenantIdAndPluginId(String tenantId, String pluginId);

    /**
     * 查询租户全部授权记录，按插件标识稳定排序。
     *
     * @param tenantId 租户标识
     * @return 授权记录列表
     * @author 余炘洋
     * @date 2026-10-17
     */
    List<AgentPluginGrant> findByTenantIdOrderByPluginIdAsc(String tenantId);
}
