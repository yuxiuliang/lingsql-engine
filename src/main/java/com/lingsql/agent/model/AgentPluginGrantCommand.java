package com.lingsql.agent.model;

/**
 * 租户插件授权命令。
 *
 * @param granted 是否授权，必须显式提供
 * @author 余炘洋
 * @date 2026-10-17
 */
public record AgentPluginGrantCommand(Boolean granted) {
}
