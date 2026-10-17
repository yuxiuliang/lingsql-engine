package com.lingsql.agent.orchestration;

/**
 * 人工审批通过后的续跑命令。
 *
 * <p>续跑从安全查询模块之前重新进入，不允许从审计完成后或 JDBC 执行前恢复。</p>
 *
 * @param tenantId 租户标识
 * @param executionId 原始执行标识
 * @param approverId 审批人标识
 * @author 余炘洋
 * @date 2026-10-17
 */
public record AgentResumeCommand(
        String tenantId,
        String executionId,
        String approverId
) {
}
