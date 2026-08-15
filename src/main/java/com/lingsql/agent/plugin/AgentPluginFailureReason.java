package com.lingsql.agent.plugin;

/**
 * 插件网关稳定失败分类。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public enum AgentPluginFailureReason {
    POLICY_DENIED,
    BUDGET_EXHAUSTED,
    DEADLINE_EXCEEDED,
    CONTRACT_VIOLATION,
    PLUGIN_FAILED
}
