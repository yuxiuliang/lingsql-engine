package com.lingsql.agent.model;

/**
 * Agent 插件治理视图。
 *
 * <p>只暴露稳定元数据，不包含插件实例、类名或内部实现。</p>
 *
 * @param pluginId    插件唯一标识
 * @param version     插件版本
 * @param access      访问级别（SYSTEM_ONLY / ORCHESTRATED）
 * @param costLevel   成本等级（LOW / MEDIUM / HIGH）
 * @param idempotent  是否幂等
 * @param grantable   是否支持租户授权
 * @param granted     租户授权状态；非租户视图或不可授权插件为 null
 * @param description 能力说明
 * @author 余炘洋
 * @date 2026-10-17
 */
public record AgentPluginView(
        String pluginId,
        String version,
        String access,
        String costLevel,
        boolean idempotent,
        boolean grantable,
        Boolean granted,
        String description
) {
}
