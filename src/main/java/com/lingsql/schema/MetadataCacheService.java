package com.lingsql.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.metadata.service.MetadataExtService;
import com.lingsql.schema.model.TableInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 元数据缓存服务，负责 Schema 与 LLM 补全文本的缓存。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final LingSqlProperties properties;
    private final MetadataExtService metadataExtService;

    private static final String SCHEMA_CACHE_PREFIX = "lingsql:schema:";
    private static final String COMMENT_CACHE_PREFIX = "lingsql:comment:";

    /**
     * 将 TableInfo 转换为 AI 易读的文本格式（DDL 简化版）。
     *
     * @param tenantId 租户标识
     * @param tables   表结构列表
     * @return 可读文本
     * @author 余炘洋
     * @date 2026-01-22
     */
    public String getAiFriendlySchema(String tenantId, List<TableInfo> tables) {
        if (tables == null || tables.isEmpty()) {
            return "当前租户暂无可用表结构。";
        }
        List<TableInfo> mergedTables = metadataExtService.mergeComments(tenantId, tables);
        StringBuilder sb = new StringBuilder();
        for (TableInfo table : mergedTables) {
            sb.append("Table: ").append(table.getTableName())
                    .append(" (").append(normalizeComment(table.getTableComment())).append(")\n");
            sb.append("Columns:\n");
            for (TableInfo.ColumnInfo col : table.getColumns()) {
                sb.append("  - ").append(col.getColumnName())
                        .append(" (").append(col.getDataType()).append(")")
                        .append(": ").append(normalizeComment(col.getColumnComment()))
                        .append(col.isPrimaryKey() ? " [Primary Key]" : "")
                        .append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 缓存 Schema 信息。
     *
     * @param tenantId 租户标识
     * @param tables   表结构列表
     * @author 余炘洋
     * @date 2026-01-22
     */
    public void cacheSchema(String tenantId, List<TableInfo> tables) {
        try {
            String cacheKey = buildSchemaCacheKey(tenantId);
            if (cacheKey == null) {
                return;
            }
            String json = objectMapper.writeValueAsString(tables);
            redisTemplate.opsForValue().set(
                    cacheKey,
                    json,
                    properties.getCache().getSchemaTtl().toSeconds(),
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.error("缓存 Schema 失败: ", e);
        }
    }

    /**
     * 从缓存获取 Schema。
     *
     * @param tenantId 租户标识
     * @return 表结构列表
     * @author 余炘洋
     * @date 2026-01-22
     */
    public List<TableInfo> getCachedSchema(String tenantId) {
        String cacheKey = buildSchemaCacheKey(tenantId);
        if (cacheKey == null) {
            return null;
        }
        String json = redisTemplate.opsForValue().get(cacheKey);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, TableInfo.class));
        } catch (Exception e) {
            log.error("解析缓存 Schema 失败: ", e);
            return Collections.emptyList();
        }
    }

    /**
     * 清理指定租户的 Schema 缓存，供受控自愈流程重新读取元数据。
     *
     * @param tenantId 租户标识
     * @author 余炘洋
     * @date 2026-07-29
     */
    public void evictSchema(String tenantId) {
        String cacheKey = buildSchemaCacheKey(tenantId);
        if (cacheKey != null) {
            redisTemplate.delete(cacheKey);
        }
    }

    /**
     * 构建 Schema 缓存 Key。
     *
     * @param tenantId 租户标识
     * @return 缓存 Key
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String buildSchemaCacheKey(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return null;
        }
        String safeSuffix = DigestUtils.md5DigestAsHex(tenantId.getBytes(StandardCharsets.UTF_8));
        return SCHEMA_CACHE_PREFIX + safeSuffix;
    }

    /**
     * 获取字段或表的缓存注释。
     *
     * @param key 缓存键
     * @return 注释文本
     * @author 余炘洋
     * @date 2026-01-22
     */
    public String getCachedComment(String key) {
        return redisTemplate.opsForValue().get(COMMENT_CACHE_PREFIX + key);
    }

    /**
     * 缓存字段或表的补全文本。
     *
     * @param key     缓存键
     * @param comment 注释内容
     * @author 余炘洋
     * @date 2026-01-22
     */
    public void cacheComment(String key, String comment) {
        redisTemplate.opsForValue().set(
                COMMENT_CACHE_PREFIX + key,
                comment,
                properties.getCache().getCommentTtl().toSeconds(),
                TimeUnit.SECONDS
        );
    }

    /**
     * 统一注释格式。
     *
     * @param comment 原始注释
     * @return 格式化注释
     * @author 余炘洋
     * @date 2026-01-22
     */
    private String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return "无描述";
        }
        return comment.trim();
    }
}
