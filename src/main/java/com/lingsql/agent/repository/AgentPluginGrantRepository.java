package com.lingsql.agent.repository;

import com.lingsql.agent.entity.AgentPluginGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentPluginGrantRepository extends JpaRepository<AgentPluginGrant, Long> {

    Optional<AgentPluginGrant> findByTenantIdAndPluginId(String tenantId, String pluginId);
}
