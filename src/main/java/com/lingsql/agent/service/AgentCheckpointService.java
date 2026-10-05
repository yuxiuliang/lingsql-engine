package com.lingsql.agent.service;

import com.lingsql.agent.entity.AgentCheckpoint;
import com.lingsql.agent.repository.AgentCheckpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Agent 检查点服务：负责阶段性落库与安全恢复查询。
 *
 * <p>恢复只能从安全阶段之前重新进入；本服务只读写检查点，不负责驱动编排。</p>
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Service
@RequiredArgsConstructor
public class AgentCheckpointService {

    private final AgentCheckpointRepository repository;

    /**
     * 保存检查点。
     *
     * @param tenantId       租户标识
     * @param executionId    执行标识
     * @param conversationId 会话标识
     * @param stage          阶段
     * @param status         状态
     * @param payloadJson    已脱敏载荷
     */
    public void save(String tenantId, String executionId, String conversationId,
                     String stage, String status, String payloadJson) {
        AgentCheckpoint checkpoint = new AgentCheckpoint();
        checkpoint.setTenantId(tenantId);
        checkpoint.setExecutionId(executionId);
        checkpoint.setConversationId(conversationId);
        checkpoint.setStage(stage);
        checkpoint.setStatus(status);
        checkpoint.setPayloadJson(payloadJson);
        checkpoint.setCreatedAt(LocalDateTime.now());
        repository.save(checkpoint);
    }

    /**
     * 读取最新检查点。
     *
     * @param tenantId    租户标识
     * @param executionId 执行标识
     * @return 最新检查点
     */
    public Optional<AgentCheckpoint> latest(String tenantId, String executionId) {
        return repository.findTopByTenantIdAndExecutionIdOrderByCreatedAtDesc(tenantId, executionId);
    }
}
