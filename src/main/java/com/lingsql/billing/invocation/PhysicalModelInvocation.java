package com.lingsql.billing.invocation;

/**
 * 一次物理模型调用的稳定描述，不包含 Prompt 或密钥原文。
 *
 * @param invocationId 物理调用幂等ID
 * @param tenantId 租户ID
 * @param workflowRequestId 工作流请求ID
 * @param logicalCallNo 逻辑调用序号
 * @param stage 调用阶段
 * @param caller 调用来源
 * @param routeAttempt 模型路由尝试序号
 * @param modelConfigId 实际模型配置ID
 * @param providerName 供应商名称
 * @param modelName 模型名称
 * @param modelVersion 模型版本
 * @param routeFingerprint 调用选择时的路由配置指纹
 * @param maxBillableTokens 最大可计费Token
 * @param promptHash Prompt摘要
 * @author 余炘洋
 * @date 2026-08-15
 */
public record PhysicalModelInvocation(
        String invocationId,
        String tenantId,
        String workflowRequestId,
        int logicalCallNo,
        String stage,
        String caller,
        int routeAttempt,
        long modelConfigId,
        String providerName,
        String modelName,
        String modelVersion,
        String routeFingerprint,
        int maxBillableTokens,
        String promptHash
) {
}
