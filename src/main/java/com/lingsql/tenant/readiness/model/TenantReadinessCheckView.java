package com.lingsql.tenant.readiness.model;

/**
 * 租户首问就绪单项检查。
 *
 * @param code 稳定检查码
 * @param name 检查名称
 * @param passed 是否通过
 * @param failureReason 未通过原因，通过时为 {@code null}
 * @author 余炘洋
 * @date 2026-08-16
 */
public record TenantReadinessCheckView(
        String code,
        String name,
        boolean passed,
        String failureReason
) {
}
