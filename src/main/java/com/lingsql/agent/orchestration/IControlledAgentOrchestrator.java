package com.lingsql.agent.orchestration;

/**
 * 受控 Agent 编排接口。
 *
 * <p>调用方只提交一次运行命令。角色顺序、SQL 安全链、自愈、降级和公开错误映射均由实现负责。</p>
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public interface IControlledAgentOrchestrator {

    /**
     * 执行一次受控 Agent 工作流。
     *
     * @param command 运行命令
     * @return 运行结果
     * @author 余炘洋
     * @date 2026-08-15
     */
    AgentRunResult run(AgentRunCommand command);
}
