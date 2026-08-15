package com.lingsql.agent.plugin;

/**
 * 插件网关异常。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public class AgentPluginException extends RuntimeException {

    private final AgentPluginFailureReason reason;

    public AgentPluginException(AgentPluginFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AgentPluginException(AgentPluginFailureReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public AgentPluginFailureReason getReason() {
        return reason;
    }
}
