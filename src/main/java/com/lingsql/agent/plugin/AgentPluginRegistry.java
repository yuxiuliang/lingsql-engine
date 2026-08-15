package com.lingsql.agent.plugin;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 插件注册中心。
 *
 * <p>启动时完成元数据与重复 ID 校验，拒绝不确定的插件覆盖。</p>
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Component
public class AgentPluginRegistry {

    private final Map<String, IAgentPlugin<?, ?>> plugins;

    public AgentPluginRegistry(List<IAgentPlugin<?, ?>> pluginList) {
        Map<String, IAgentPlugin<?, ?>> registered = new LinkedHashMap<>();
        for (IAgentPlugin<?, ?> plugin : pluginList) {
            AgentPluginDescriptor descriptor = validate(plugin);
            IAgentPlugin<?, ?> existing = registered.putIfAbsent(descriptor.id(), plugin);
            if (existing != null) {
                throw new IllegalStateException("Agent 插件 ID 重复: " + descriptor.id());
            }
        }
        this.plugins = Map.copyOf(registered);
    }

    IAgentPlugin<?, ?> require(String pluginId) {
        IAgentPlugin<?, ?> plugin = plugins.get(pluginId);
        if (plugin == null) {
            throw new IllegalArgumentException("Agent 插件不存在: " + pluginId);
        }
        return plugin;
    }

    public List<AgentPluginDescriptor> descriptors() {
        return plugins.values().stream().map(IAgentPlugin::descriptor).toList();
    }

    private AgentPluginDescriptor validate(IAgentPlugin<?, ?> plugin) {
        if (plugin == null || plugin.descriptor() == null) {
            throw new IllegalStateException("Agent 插件元数据不能为空");
        }
        AgentPluginDescriptor descriptor = plugin.descriptor();
        if (!StringUtils.hasText(descriptor.id())
                || !descriptor.id().matches("[a-z][a-z0-9_.-]{2,63}")
                || !StringUtils.hasText(descriptor.version())
                || descriptor.access() == null
                || descriptor.cost() == null
                || !StringUtils.hasText(descriptor.description())
                || plugin.inputType() == null
                || plugin.outputType() == null) {
            throw new IllegalStateException("Agent 插件元数据不完整: " + descriptor.id());
        }
        return descriptor;
    }
}
