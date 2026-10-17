package com.lingsql.agent.plugin;

import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.agent.orchestration.AgentRunControl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 统一 Agent 插件执行网关。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Service
@RequiredArgsConstructor
public class AgentPluginExecutor {

    private final AgentPluginRegistry registry;
    private final com.lingsql.agent.service.PluginInvocationOutboxService pluginInvocationOutboxService;
    private final com.lingsql.agent.service.AgentPluginGrantService pluginGrantService;

    public <I, O> O execute(
            String pluginId,
            AgentExecutionContext context,
            AgentRunControl control,
            I input,
            Class<O> outputType
    ) {
        IAgentPlugin<?, ?> plugin = registry.require(pluginId);
        if (input == null || !plugin.inputType().isInstance(input)) {
            throw new AgentPluginException(AgentPluginFailureReason.CONTRACT_VIOLATION,
                    "Agent 插件输入类型不匹配: " + pluginId);
        }
        if (!plugin.outputType().equals(outputType)) {
            throw new AgentPluginException(AgentPluginFailureReason.CONTRACT_VIOLATION,
                    "Agent 插件输出类型不匹配: " + pluginId);
        }
        assertTenantGrant(pluginId, plugin.descriptor(), context);
        control.beforePlugin(plugin.descriptor());
        try {
            O result = invoke(plugin, context, control, input, outputType);
            control.assertWithinDeadline();
            pluginInvocationOutboxService.record(context.tenantId(), context.executionId(), pluginId, "SUCCEEDED", plugin.descriptor().cost().units());
            return result;
        } catch (AgentPluginException exception) {
            pluginInvocationOutboxService.record(context.tenantId(), context.executionId(), pluginId, "FAILED", plugin.descriptor().cost().units());
            throw exception;
        } catch (RuntimeException exception) {
            pluginInvocationOutboxService.record(context.tenantId(), context.executionId(), pluginId, "FAILED", plugin.descriptor().cost().units());
            throw new AgentPluginException(AgentPluginFailureReason.PLUGIN_FAILED,
                    "Agent 插件执行失败: " + pluginId, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private <I, O> O invoke(
            IAgentPlugin<?, ?> plugin,
            AgentExecutionContext context,
            AgentRunControl control,
            I input,
            Class<O> outputType
    ) {
        IAgentPlugin<I, O> typedPlugin = (IAgentPlugin<I, O>) plugin;
        return outputType.cast(typedPlugin.execute(context, control, input));
    }

    /**
     * 校验租户插件授权：受控业务插件必须由租户显式授权，未授权一律拒绝且不消耗预算。
     *
     * <p>系统插件由受控工作流固定调用，不参与租户授权。</p>
     *
     * @param pluginId   插件标识
     * @param descriptor 插件元数据
     * @param context    执行上下文
     * @author 余炘洋
     * @date 2026-10-17
     */
    private void assertTenantGrant(String pluginId, AgentPluginDescriptor descriptor, AgentExecutionContext context) {
        if (descriptor.access() != AgentPluginAccess.ORCHESTRATED) {
            return;
        }
        if (!pluginGrantService.isGranted(context.tenantId(), pluginId)) {
            throw new AgentPluginException(AgentPluginFailureReason.POLICY_DENIED,
                    "租户未授权插件: " + pluginId);
        }
    }
}
