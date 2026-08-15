package com.lingsql.agent.orchestration;

/**
 * Agent 工作流调用来源。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public enum AgentCallerType {
    /** 平台内建且随主仓库发布的系统工作流。 */
    SYSTEM,
    /** 经租户策略授权的普通编排工作流。 */
    ORCHESTRATED
}
