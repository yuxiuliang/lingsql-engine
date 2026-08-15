package com.lingsql.billing.model;

import java.util.List;

/**
 * 关闭计费验收会话请求。
 *
 * @param requestIds 会话开启后产生的精确工作流请求ID
 * @author 余炘洋
 * @date 2026-08-15
 */
public record BillingAcceptanceCloseRequest(List<String> requestIds) {
}
