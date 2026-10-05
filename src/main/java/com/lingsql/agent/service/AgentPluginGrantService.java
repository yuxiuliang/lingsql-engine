package com.lingsql.agent.service;

import com.lingsql.agent.entity.AgentPluginGrant;
import com.lingsql.agent.repository.AgentPluginGrantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 租户插件授权服务。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Service
@RequiredArgsConstructor
public class AgentPluginGrantService {

    private final AgentPluginGrantRepository repository;

    /**
     * 判断租户是否授权某插件。
     */
    public boolean isGranted(String tenantId, String pluginId) {
        return repository.findByTenantIdAndPluginId(tenantId, pluginId)
                .map(AgentPluginGrant::getGranted)
                .orElse(false);
    }

    /**
     * 设置授权。
     */
    public void grant(String tenantId, String pluginId, boolean granted) {
        AgentPluginGrant grant = repository.findByTenantIdAndPluginId(tenantId, pluginId)
                .orElseGet(AgentPluginGrant::new);
        grant.setTenantId(tenantId);
        grant.setPluginId(pluginId);
        grant.setGranted(granted);
        if (grant.getCreatedAt() == null) {
            grant.setCreatedAt(LocalDateTime.now());
        }
        repository.save(grant);
    }
}
