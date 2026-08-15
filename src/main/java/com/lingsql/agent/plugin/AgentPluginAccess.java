package com.lingsql.agent.plugin;

/**
 * Agent 插件访问级别。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public enum AgentPluginAccess {
    /** 仅允许受控系统工作流调用。 */
    SYSTEM_ONLY,
    /** 允许经策略授权的业务工作流调用。 */
    ORCHESTRATED
}
