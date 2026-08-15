package com.lingsql.agent.orchestration;

import com.lingsql.chat.model.ChatTraceStep;

import java.util.List;
import java.util.Map;

/**
 * 受控 Agent 工作流运行结果。
 *
 * @param executionId 执行标识
 * @param workflowVersion 工作流版本
 * @param status 运行状态
 * @param answer 面向用户的答复
 * @param sql 最终 SQL 或公开失败标识
 * @param data 查询结果
 * @param trace 结构化公开轨迹
 * @param failureCode 失败分类，成功时为空
 * @param durationMs 总耗时
 * @author 余炘洋
 * @date 2026-08-15
 */
public record AgentRunResult(
        String executionId,
        String workflowVersion,
        AgentRunStatus status,
        String answer,
        String sql,
        List<Map<String, Object>> data,
        List<ChatTraceStep> trace,
        AgentFailureCode failureCode,
        long durationMs
) {

    public AgentRunResult {
        data = data == null ? List.of() : List.copyOf(data);
        trace = trace == null ? List.of() : List.copyOf(trace);
    }

    /**
     * 核心查询是否成功。
     *
     * @return 是否成功
     * @author 余炘洋
     * @date 2026-08-15
     */
    public boolean querySucceeded() {
        return status == AgentRunStatus.SUCCEEDED || status == AgentRunStatus.SUCCEEDED_WITH_DEGRADATION;
    }
}
