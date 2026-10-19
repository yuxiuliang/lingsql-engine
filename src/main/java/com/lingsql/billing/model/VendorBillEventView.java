package com.lingsql.billing.model;

import java.time.LocalDateTime;

/**
 * 供应商账单视图。
 *
 * @param id                   记录主键
 * @param tenantId             租户标识
 * @param providerName         供应商名称
 * @param externalBillId       外部账单标识
 * @param providerRequestId    供应商请求标识
 * @param amountMinor          账单金额（分）
 * @param vendorTokens         供应商账单 Token 数
 * @param currency             币种
 * @param status               供应商原始状态
 * @param matchState           匹配状态
 * @param matchStateDescription 匹配状态说明
 * @param matchedInvocationId  匹配到的物理调用标识
 * @param matchedState         匹配到的物理调用状态
 * @param matchedActualAmount  匹配到的物理调用结算金额（分）
 * @param matchedActualTokens  匹配到的物理调用实际 Token 数
 * @param evidenceHash         账单证据摘要（SHA-256），可用于 UNKNOWN 裁决
 * @param matchTime            匹配时间
 * @param createdAt            导入时间
 * @author 余炘洋
 * @date 2026-10-17
 */
public record VendorBillEventView(Long id,
                                  String tenantId,
                                  String providerName,
                                  String externalBillId,
                                  String providerRequestId,
                                  Long amountMinor,
                                  Integer vendorTokens,
                                  String currency,
                                  String status,
                                  String matchState,
                                  String matchStateDescription,
                                  String matchedInvocationId,
                                  String matchedState,
                                  Long matchedActualAmount,
                                  Integer matchedActualTokens,
                                  String evidenceHash,
                                  LocalDateTime matchTime,
                                  LocalDateTime createdAt) {
}
