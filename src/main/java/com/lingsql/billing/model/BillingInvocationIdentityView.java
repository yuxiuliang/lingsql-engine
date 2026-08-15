package com.lingsql.billing.model;

/**
 * 一次物理模型调用的实际模型身份摘要。
 *
 * @param invocationId 物理调用ID
 * @param stage 调用阶段
 * @param routeAttempt 路由尝试序号
 * @param modelConfigId 实际模型配置ID
 * @param providerName 供应商名称
 * @param modelName 模型名称
 * @param modelVersion 调用时模型版本
 * @param routeFingerprint 调用选择时路由配置指纹
 * @param state 调用资金终态
 * @param identityComplete 身份证据是否完整
 * @author 余炘洋
 * @date 2026-08-15
 */
public record BillingInvocationIdentityView(
        String invocationId,
        String stage,
        int routeAttempt,
        Long modelConfigId,
        String providerName,
        String modelName,
        String modelVersion,
        String routeFingerprint,
        String state,
        boolean identityComplete
) {
}
