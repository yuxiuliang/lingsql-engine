package com.lingsql.agent.orchestration;

import java.util.Set;

/**
 * 单次 Agent 运行的不可变策略边界。
 *
 * @param allowedPluginIds 允许调用的插件
 * @param maxPluginInvocations 最大插件调用次数
 * @param callerType 调用来源
 * @param maxPluginCostUnits 最大插件成本单位
 * @param maxSqlAttempts 最大 SQL 尝试轮次
 * @param maxDurationMs 最大运行时长
 * @author 余炘洋
 * @date 2026-08-15
 */
public record AgentRunPolicy(
        Set<String> allowedPluginIds,
        int maxPluginInvocations,
        AgentCallerType callerType,
        int maxPluginCostUnits,
        int maxSqlAttempts,
        long maxDurationMs,
        long maxTokenBudget,
        long maxCostBudget
) {
    public AgentRunPolicy {
        allowedPluginIds = allowedPluginIds == null ? Set.of() : Set.copyOf(allowedPluginIds);
        if (callerType == null
                || maxPluginInvocations <= 0
                || maxPluginCostUnits <= 0
                || maxSqlAttempts <= 0
                || maxDurationMs <= 0) {
            throw new IllegalArgumentException("Agent 运行策略必须使用正数限制");
        }
        if (maxTokenBudget <= 0) {
            maxTokenBudget = Long.MAX_VALUE;
        }
        if (maxCostBudget <= 0) {
            maxCostBudget = Long.MAX_VALUE;
        }
    }

    /**
     * 兼容旧构造：不限制 Token 与费用预算。
     */
    public AgentRunPolicy(Set<String> allowedPluginIds, int maxPluginInvocations,
                          AgentCallerType callerType, int maxPluginCostUnits,
                          int maxSqlAttempts, long maxDurationMs) {
        this(allowedPluginIds, maxPluginInvocations, callerType, maxPluginCostUnits,
                maxSqlAttempts, maxDurationMs, Long.MAX_VALUE, Long.MAX_VALUE);
    }
}
