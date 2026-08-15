package com.lingsql.agent.plugin;

/**
 * Agent 插件稳定元数据。
 *
 * @param id 唯一标识
 * @param version 语义版本
 * @param access 访问级别
 * @param cost 成本等级
 * @param idempotent 是否幂等
 * @param description 中文能力说明
 * @author 余炘洋
 * @date 2026-08-15
 */
public record AgentPluginDescriptor(
        String id,
        String version,
        AgentPluginAccess access,
        AgentPluginCost cost,
        boolean idempotent,
        String description
) {
}
