package com.lingsql.schema;

import tools.jackson.databind.ObjectMapper;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
     * 表结构正文的字符上限（约 1.4 万 token）。
     *
     * <p>客户库有几百张表时，逐表逐字段全量拼进系统提示词会线性膨胀（实测 60 张表 600 字段
     * 约 16.7 KB，外推到 300 张表约 83.5 KB／2.4 万 token），既挤占上下文窗口，也让每次提问
     * 的成本随表数上涨。超出预算的表降级为「只列名并显式说明已省略」，
     * 让模型知道信息不完整，而不是对着残缺结构硬猜。</p>
     */
    private static final int MAX_SCHEMA_CHARS = 48_000;

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
        List<String> omittedTables = new ArrayList<>();
        for (TableInfo table : mergedTables) {
            String block = renderTable(table);
            if (sb.length() + block.length() > MAX_SCHEMA_CHARS) {
                omittedTables.add(table.getTableName());
                continue;
            }
            sb.append(block);
        }
        if (!omittedTables.isEmpty()) {
            sb.append("（因篇幅限制，以下 ").append(omittedTables.size())
                    .append(" 张表未展开字段，仅列名：")
                    .append(String.join("、", omittedTables))
                    .append("。若问题确实涉及这些表，请先说明表名，系统会再取用其字段。）\n");
        }
        return sb.toString();
    }

    /**
     * 渲染单张表的表名与字段。
     *
     * @param table 表结构
     * @return 该表的文本块
     * @author 余炘洋
     * @date 2026-10-26
     */
    private String renderTable(TableInfo table) {
        StringBuilder sb = new StringBuilder();
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
            writeSchemaCache(tenantId, tables);
        } catch (Exception e) {
            log.error("缓存 Schema 失败: ", e);
        }
    }

    /**
     * 写入必须成功的 Schema 缓存。只有该方法返回后才能发布持久同步收据。
     *
     * @param tenantId 租户标识
     * @param tables 表结构
     */
    public void cacheSchemaRequired(String tenantId, List<TableInfo> tables) {
        try {
            writeSchemaCache(tenantId, tables);
        } catch (Exception exception) {
            log.error("发布 Schema 同步收据前缓存失败: tenantId={}", tenantId, exception);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "Schema 缓存失败，本次同步未生效", exception);
        }
    }

    private void writeSchemaCache(String tenantId, List<TableInfo> tables) throws Exception {
        String cacheKey = buildSchemaCacheKey(tenantId);
        if (cacheKey == null) {
            throw new IllegalArgumentException("租户标识不能为空");
        }
        String json = objectMapper.writeValueAsString(tables);
        redisTemplate.opsForValue().set(cacheKey, json, properties.getCache().getSchemaTtl());
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
                properties.getCache().getCommentTtl()
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
