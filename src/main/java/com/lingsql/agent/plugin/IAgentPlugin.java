package com.lingsql.agent.plugin;

import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.agent.orchestration.AgentRunControl;

/**
 * 类型安全的 Agent 插件扩展点。
 *
 * @param <I> 输入类型
 * @param <O> 输出类型
 * @author 余炘洋
 * @date 2026-08-15
 */
public interface IAgentPlugin<I, O> {

    AgentPluginDescriptor descriptor();

    Class<I> inputType();

    Class<O> outputType();

    O execute(AgentExecutionContext context, AgentRunControl control, I input);
}
