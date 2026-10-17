package com.lingsql.agent.model;

import java.time.LocalDateTime;

/**
 * 人工审批节点视图。
 *
 * @param id 审批节点主键
 * @param tenantId 租户标识
 * @param executionId 执行标识
 * @param stage 恢复点阶段
 * @param status 审批状态
 * @param approverId 审批人
 * @param comment 审批意见
 * @param createdAt 发起时间
 * @param decidedAt 裁决时间
 * @author 余炘洋
 * @date 2026-10-17
 */
public record AgentApprovalView(
        Long id,
        String tenantId,
        String executionId,
        String stage,
        String status,
        String approverId,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime decidedAt
) {
}
