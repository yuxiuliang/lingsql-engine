package com.lingsql.agent.model;

import java.time.LocalDateTime;

/**
 * 模型即时验证结果。
 *
 * @param modelId 模型ID
 * @param status 验证状态
 * @param checkedAt 验证时间
 * @param costMs 验证耗时
 * @param errorCode 归一化错误码
 * @param message 脱敏说明
 * @author 余炘洋
 * @date 2026-08-15
 */
public record ModelProbeView(
        Long modelId,
        String status,
        LocalDateTime checkedAt,
        long costMs,
        String errorCode,
        String message
) {
}
