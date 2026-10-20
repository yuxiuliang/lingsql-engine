package com.lingsql.agent.memory.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆隐私导出视图：导出内容与来源、时间、保留期一一对应，便于用户核对与留档。
 *
 * @param tenantId   租户标识
 * @param userId     用户标识，为空表示租户范围
 * @param scope      导出范围说明
 * @param total      导出条数
 * @param exportedAt 导出时间
 * @param memories   记忆明细，包含全部状态（含候选、已撤销与已过期）
 * @author 余炘洋
 * @date 2026-10-19
 */
public record AgentMemoryExportView(
        String tenantId,
        Long userId,
        String scope,
        int total,
        LocalDateTime exportedAt,
        List<AgentMemoryView> memories
) {
}
