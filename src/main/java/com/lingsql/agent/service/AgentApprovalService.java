package com.lingsql.agent.service;

import com.lingsql.agent.entity.AgentApprovalNode;
import com.lingsql.agent.repository.AgentApprovalNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 人工审批服务：记录审批节点并只允许从安全阶段之前恢复。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Service
@RequiredArgsConstructor
public class AgentApprovalService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final AgentApprovalNodeRepository repository;

    /**
     * 发起人工审批节点。
     */
    public AgentApprovalNode requestApproval(String tenantId, String executionId, String stage) {
        AgentApprovalNode node = new AgentApprovalNode();
        node.setTenantId(tenantId);
        node.setExecutionId(executionId);
        node.setStage(stage);
        node.setStatus(STATUS_PENDING);
        node.setCreatedAt(LocalDateTime.now());
        return repository.save(node);
    }

    /**
     * 审批决策。
     */
    public void decide(String tenantId, String executionId, boolean approved, String approverId, String comment) {
        AgentApprovalNode node = repository
                .findTopByTenantIdAndExecutionIdOrderByCreatedAtDesc(tenantId, executionId)
                .orElseThrow(() -> new IllegalStateException("未找到待审批节点"));
        node.setStatus(approved ? STATUS_APPROVED : STATUS_REJECTED);
        node.setApproverId(approverId);
        node.setDecidedAt(LocalDateTime.now());
        node.setComment(comment);
        repository.save(node);
    }

    /**
     * 最新审批状态。
     */
    public Optional<AgentApprovalNode> latest(String tenantId, String executionId) {
        return repository.findTopByTenantIdAndExecutionIdOrderByCreatedAtDesc(tenantId, executionId);
    }
}
