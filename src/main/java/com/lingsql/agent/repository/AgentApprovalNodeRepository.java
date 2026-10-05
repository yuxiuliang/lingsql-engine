package com.lingsql.agent.repository;

import com.lingsql.agent.entity.AgentApprovalNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentApprovalNodeRepository extends JpaRepository<AgentApprovalNode, Long> {

    Optional<AgentApprovalNode> findTopByTenantIdAndExecutionIdOrderByCreatedAtDesc(String tenantId, String executionId);
}
