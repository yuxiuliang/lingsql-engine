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
        control.beforePlugin(plugin.descriptor());
        try {
            O result = invoke(plugin, context, control, input, outputType);
            control.assertWithinDeadline();
            return result;
        } catch (AgentPluginException exception) {
            throw exception;
        } catch (RuntimeException exception) {
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
}
