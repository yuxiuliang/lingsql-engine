package com.lingsql.tenant.readiness.model;

import java.util.List;

/**
 * 租户首问就绪聚合视图。
 *
 * @param tenantId 租户标识
 * @param tenantName 租户名称，不存在时为 {@code null}
 * @param status 就绪状态：READY_TO_ASK / BLOCKED
 * @param ready 是否可以开始真实问数
 * @param checks 完整门禁检查结果
 * @param failureReasons 未通过原因汇总
 * @author 余炘洋
 * @date 2026-08-16
 */
public record TenantFirstQuestionReadinessView(
        String tenantId,
        String tenantName,
        String status,
        boolean ready,
        List<TenantReadinessCheckView> checks,
        List<String> failureReasons
) {
}
