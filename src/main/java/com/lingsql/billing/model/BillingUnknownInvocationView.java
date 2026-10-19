package com.lingsql.billing.model;

import java.time.LocalDateTime;

/**
 * UNKNOWN 物理调用工作台视图。
 *
 * <p>仅暴露裁决所需的最小字段，不包含提示词、模型响应或底层异常明细。</p>
 *
 * @param invocationId      物理调用标识
 * @param tenantId          租户标识
 * @param workflowRequestId 工作流请求标识
 * @param logicalCallNo     逻辑调用序号
 * @param stage             调用阶段
 * @param caller            调用来源
 * @param providerName      供应商名称
 * @param modelName         模型名称
 * @param modelVersion      模型版本
 * @param maxBillableTokens 最大可计费 Token
 * @param unitCost          单价（分/千 Token）
 * @param reservedAmount    预留金额（分）
 * @param failureCode       失败原因代码
 * @param reservedAt        预留时间
 * @param dispatchedAt      派发时间
 * @author 余炘洋
 * @date 2026-10-17
 */
public record BillingUnknownInvocationView(String invocationId,
                                           String tenantId,
                                           String workflowRequestId,
                                           Integer logicalCallNo,
                                           String stage,
                                           String caller,
                                           String providerName,
                                           String modelName,
                                           String modelVersion,
                                           Integer maxBillableTokens,
                                           Long unitCost,
                                           Long reservedAmount,
                                           String failureCode,
                                           LocalDateTime reservedAt,
                                           LocalDateTime dispatchedAt) {
}
