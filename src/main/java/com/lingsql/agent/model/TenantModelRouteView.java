package com.lingsql.agent.model;

/**
 * 租户模型授权路由视图，不包含模型密钥和地址。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
public record TenantModelRouteView(
        Long id,
        String tenantId,
        Long modelConfigId,
        String providerName,
        String modelName,
        String modelVersion,
        Integer priority,
        Integer weight,
        Integer status
) {
}
