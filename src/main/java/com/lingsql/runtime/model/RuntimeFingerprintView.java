package com.lingsql.runtime.model;

import java.time.Instant;
import java.util.List;

/**
 * 候选版本运行指纹。
 *
 * @param commit 构建绑定的 Git 提交
 * @param applicationVersion 应用版本
 * @param buildTime 构建时间
 * @param schemaVersion Flyway 最新成功版本
 * @param dataSnapshotId 验收数据快照ID
 * @param modelRouteFingerprint 激活模型路由摘要
 * @param activeModels 激活模型安全摘要
 * @author 余炘洋
 * @date 2026-08-15
 */
public record RuntimeFingerprintView(
        String commit,
        String applicationVersion,
        Instant buildTime,
        String schemaVersion,
        String dataSnapshotId,
        String modelRouteFingerprint,
        List<RuntimeModelFingerprintView> activeModels
) {
}
