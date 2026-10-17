package com.lingsql.agent.model;

/**
 * 人工审批续跑结果视图。
 *
 * @param approvalId 审批节点主键
 * @param executionId 执行标识
 * @param status 续跑后的运行状态
 * @param answer 面向用户的答复
 * @param sql 最终 SQL 或公开失败标识
 * @param durationMs 续跑耗时
 * @author 余炘洋
 * @date 2026-10-17
 */
public record AgentApprovalResumeView(
        Long approvalId,
        String executionId,
        String status,
        String answer,
        String sql,
        long durationMs
) {
}
