package com.lingsql.tenant.model;

import java.util.List;

/**
 * 租户流失风险快照。
 *
 * @param score   风险分值（0-100）
 * @param level   风险等级
 * @param reasons 风险原因列表
 * @author 余炘洋
 * @date 2026-02-27
 */
public record TenantRiskSnapshot(int score, TenantRiskLevel level, List<String> reasons) {
}

