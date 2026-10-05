package com.lingsql.agent.repository;

import com.lingsql.agent.entity.AgentCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Agent 检查点仓储。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
public interface AgentCheckpointRepository extends JpaRepository<AgentCheckpoint, Long> {

    Optional<AgentCheckpoint> findTopByTenantIdAndExecutionIdOrderByCreatedAtDesc(String tenantId, String executionId);
}
