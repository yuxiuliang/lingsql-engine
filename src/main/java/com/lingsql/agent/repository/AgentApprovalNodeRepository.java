package com.lingsql.agent.repository;

import com.lingsql.agent.entity.AgentApprovalNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 人工审批节点仓储。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
public interface AgentApprovalNodeRepository extends JpaRepository<AgentApprovalNode, Long> {

    /**
     * 最新审批节点。
     *
     * @param tenantId    租户标识
     * @param executionId 执行标识
     * @return 最新审批节点
     */
    Optional<AgentApprovalNode> findTopByTenantIdAndExecutionIdOrderByCreatedAtDesc(String tenantId, String executionId);

    List<AgentApprovalNode> findTop100ByOrderByCreatedAtDesc();

    List<AgentApprovalNode> findTop100ByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<AgentApprovalNode> findTop100ByStatusOrderByCreatedAtDesc(String status);

    List<AgentApprovalNode> findTop100ByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, String status);
}
