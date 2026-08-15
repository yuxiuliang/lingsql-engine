package com.lingsql.runtime.model;

/**
 * 运行时模型路由的安全摘要项。
 *
 * @param id 模型配置ID
 * @param providerName 供应商名称
 * @param modelName 模型名称
 * @param modelVersion 模型版本
 * @param priority 路由优先级
 * @param weight 路由权重
 * @author 余炘洋
 * @date 2026-08-15
 */
public record RuntimeModelFingerprintView(
        Long id,
        String providerName,
        String modelName,
        String modelVersion,
        Integer priority,
        Integer weight
) {
}
