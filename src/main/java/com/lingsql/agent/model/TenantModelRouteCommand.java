package com.lingsql.agent.model;

/**
 * 租户模型授权路由写入项。
 *
 * @param modelConfigId 模型配置 ID
 * @param priority 租户内优先级，数值越小越优先
 * @param weight 同优先级模型的权重
 * @author 余炘洋
 * @date 2026-08-16
 */
public record TenantModelRouteCommand(Long modelConfigId, Integer priority, Integer weight) {
}
