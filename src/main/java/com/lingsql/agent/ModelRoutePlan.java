package com.lingsql.agent;

import java.util.List;

/**
 * 一次逻辑模型调用冻结的租户授权路由计划。
 *
 * @param tenantId 租户标识
 * @param routeFingerprint 授权路由 SHA-256
 * @param candidates 候选模型快照
 * @author 余炘洋
 * @date 2026-08-16
 */
public record ModelRoutePlan(
        String tenantId,
        String routeFingerprint,
        List<ModelRouteCandidate> candidates
) {
    public ModelRoutePlan {
        candidates = List.copyOf(candidates);
    }
}
