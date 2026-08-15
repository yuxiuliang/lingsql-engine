package com.lingsql.agent.orchestration;

import com.lingsql.agent.plugin.AgentPluginDescriptor;
import com.lingsql.agent.plugin.AgentPluginAccess;
import com.lingsql.agent.plugin.AgentPluginException;
import com.lingsql.agent.plugin.AgentPluginFailureReason;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单次 Agent 运行的预算执行器。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public final class AgentRunControl {

    private final AgentRunPolicy policy;
    private final long deadlineNanos;
    private final AtomicInteger pluginInvocations = new AtomicInteger();
    private final AtomicInteger pluginCostUnits = new AtomicInteger();

    public AgentRunControl(AgentRunPolicy policy) {
        this.policy = policy;
        this.deadlineNanos = System.nanoTime() + policy.maxDurationMs() * 1_000_000L;
    }

    public void beforePlugin(AgentPluginDescriptor descriptor) {
        assertWithinDeadline();
        if (!policy.allowedPluginIds().contains(descriptor.id())) {
            throw new AgentPluginException(AgentPluginFailureReason.POLICY_DENIED,
                    "Agent 策略未授权插件: " + descriptor.id());
        }
        if (descriptor.access() == AgentPluginAccess.SYSTEM_ONLY
                && policy.callerType() != AgentCallerType.SYSTEM) {
            throw new AgentPluginException(AgentPluginFailureReason.POLICY_DENIED,
                    "当前工作流无权调用系统插件: " + descriptor.id());
        }
        if (pluginInvocations.incrementAndGet() > policy.maxPluginInvocations()) {
            throw new AgentPluginException(AgentPluginFailureReason.BUDGET_EXHAUSTED,
                    "Agent 插件调用次数超过预算");
        }
        if (pluginCostUnits.addAndGet(descriptor.cost().units()) > policy.maxPluginCostUnits()) {
            throw new AgentPluginException(AgentPluginFailureReason.BUDGET_EXHAUSTED,
                    "Agent 插件成本超过预算");
        }
    }

    public int maxSqlAttempts() {
        return policy.maxSqlAttempts();
    }

    public void assertWithinDeadline() {
        if (System.nanoTime() > deadlineNanos) {
            throw new AgentPluginException(AgentPluginFailureReason.DEADLINE_EXCEEDED,
                    "Agent 运行超过截止时间");
        }
    }
}
