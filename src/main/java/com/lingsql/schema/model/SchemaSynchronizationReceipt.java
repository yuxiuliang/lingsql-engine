package com.lingsql.schema.model;

import java.time.LocalDateTime;

/**
 * 已持久发布的 Schema 同步收据。
 *
 * @param tenantId 租户标识
 * @param tableCount 实际命中的表数
 * @param whitelistTableCount 配置的白名单表数
 * @param whitelistDigest 规范化白名单 SHA-256
 * @param syncedAt 同步完成时间
 * @author 余炘洋
 * @date 2026-08-16
 */
public record SchemaSynchronizationReceipt(
        String tenantId,
        int tableCount,
        int whitelistTableCount,
        String whitelistDigest,
        LocalDateTime syncedAt
) {
}
