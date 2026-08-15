package com.lingsql.agent.orchestration;

/**
 * Agent 运行状态。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public enum AgentRunStatus {
    /** 全部受控阶段成功。 */
    SUCCEEDED,
    /** 核心查询成功，非核心分析阶段使用了降级结果。 */
    SUCCEEDED_WITH_DEGRADATION,
    /** 核心查询未成功。 */
    FAILED
}
