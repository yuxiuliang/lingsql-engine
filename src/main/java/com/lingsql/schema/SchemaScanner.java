package com.lingsql.schema;

import com.lingsql.billing.invocation.PhysicalBillingException;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.llm.IModelGateway;
import com.lingsql.llm.ModelCallCommand;
import com.lingsql.schema.model.TableInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 元数据扫描器，负责读取数据库表结构并补全描述。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaScanner {

    private static final String MODEL_CALLER_TYPE = "SYSTEM";
    private static final String MODEL_CALLER_ID = "SCHEMA_SCANNER";
    private static final String MODEL_STAGE = "SCHEMA_COMMENT";

    private final MetadataCacheService cacheService;
    private final IModelGateway modelGateway;

    /**
     * 扫描指定数据源中的表结构。
     *
     * @param tenantId  租户标识
     * @param dataSource 租户数据源
     * @param whiteList 白名单表名（逗号分隔）
     * @return 表结构列表
     * @author 余炘洋
     * @date 2026-01-22
     */
    public List<TableInfo> scanSchema(String tenantId, DataSource dataSource, String whiteList) {
        List<TableInfo> tableInfos = new ArrayList<>();
        Set<String> whiteTables = parseCsv(whiteList);
        String workflowRequestId = UUID.randomUUID().toString();
        AtomicInteger logicalCallSequence = new AtomicInteger();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            // 本次同步是否已放弃模型注释（首个失败后置位，避免逐字段重复失败）
            boolean[] skipModelComment = {false};

            try (ResultSet tables = metaData.getTables(catalog, null, null, new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    if (!whiteTables.isEmpty() && !whiteTables.contains(tableName.toLowerCase())) {
                        continue;
                    }

                    TableInfo tableInfo = new TableInfo();
                    tableInfo.setSchemaName(catalog);
                    tableInfo.setTableName(tableName);

                    String tableComment = tables.getString("REMARKS");
                    tableInfo.setTableComment(resolveComment(tenantId, workflowRequestId, logicalCallSequence,
                            "table", tableName, tableComment,
                            "请为数据库表 " + tableName + " 生成一句简短中文业务说明", skipModelComment));

                    Map<String, Boolean> primaryKeys = loadPrimaryKeys(metaData, catalog, tableName);

                    List<TableInfo.ColumnInfo> columns = new ArrayList<>();
                    try (ResultSet cols = metaData.getColumns(catalog, null, tableName, null)) {
                        while (cols.next()) {
                            TableInfo.ColumnInfo col = new TableInfo.ColumnInfo();
                            String columnName = cols.getString("COLUMN_NAME");
                            col.setColumnName(columnName);
                            col.setDataType(cols.getString("TYPE_NAME"));
                            col.setColumnComment(cols.getString("REMARKS"));
                            col.setPrimaryKey(primaryKeys.getOrDefault(columnName.toLowerCase(), false));
                            columns.add(col);
                        }
                    }
                    // 一张表的字段注释合并成一次模型调用（原先逐字段各调一次，200 张表即 1600 次）
                    enrichColumnComments(tenantId, workflowRequestId, logicalCallSequence,
                            tableName, columns, skipModelComment);
                    tableInfo.setColumns(columns);
                    tableInfos.add(tableInfo);
                }
            }
        } catch (Exception e) {
            log.error("元数据扫描失败: tenantId={}", tenantId, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "元数据扫描失败，请稍后重试", e);
        }
        return tableInfos;
    }

    /**
     * 加载主键字段集合。
     *
     * @param metaData 元数据对象
     * @param catalog  catalog 名称
     * @param tableName 表名
     * @return 主键映射
     * @author 余炘洋
     * @date 2026-01-22
     */
    private Map<String, Boolean> loadPrimaryKeys(DatabaseMetaData metaData, String catalog, String tableName) {
        Map<String, Boolean> primaryKeys = new HashMap<>();
        try (ResultSet pk = metaData.getPrimaryKeys(catalog, null, tableName)) {
            while (pk.next()) {
                String columnName = pk.getString("COLUMN_NAME");
                if (StringUtils.hasText(columnName)) {
                    primaryKeys.put(columnName.toLowerCase(), true);
                }
            }
        } catch (Exception e) {
            log.warn("读取主键失败: {}", tableName, e);
        }
        return primaryKeys;
    }

    /**
     * 批量补齐一张表的字段注释。
     *
     * <p>一次模型调用覆盖整表字段：提示模型按「字段名=中文描述」逐行输出。
     * 失败时整表降级为「无描述」并置位跳过标志，不再逐字段重试。
     * 已有注释或命中缓存的字段不进入本次调用。</p>
     *
     * @param tenantId            租户
     * @param workflowRequestId   工作流请求标识
     * @param logicalCallSequence 逻辑调用序号
     * @param tableName           表名
     * @param columns             该表字段（就地写入注释）
     * @param skipModelComment    本次同步是否已放弃模型注释
     * @author 余炘洋
     * @date 2026-10-26
     */
    private void enrichColumnComments(String tenantId, String workflowRequestId, AtomicInteger logicalCallSequence,
                                      String tableName, List<TableInfo.ColumnInfo> columns, boolean[] skipModelComment) {
        List<TableInfo.ColumnInfo> pending = new ArrayList<>();
        for (TableInfo.ColumnInfo col : columns) {
            if (StringUtils.hasText(col.getColumnComment())) {
                continue;
            }
            String cached = cacheService.getCachedComment(tenantId + ":column:" + tableName + ":" + col.getColumnName());
            if (StringUtils.hasText(cached)) {
                col.setColumnComment(cached);
            } else {
                pending.add(col);
            }
        }
        if (pending.isEmpty() || skipModelComment[0]) {
            return;
        }
        StringBuilder prompt = new StringBuilder("为数据库表 ")
                .append(tableName)
                .append(" 的下列字段各生成一句简短中文含义。严格按「字段名=中文描述」逐行输出，不要其他内容：\n");
        for (TableInfo.ColumnInfo col : pending) {
            prompt.append(col.getColumnName()).append(" (").append(col.getDataType()).append(")\n");
        }
        try {
            String generated = modelGateway.chat(new ModelCallCommand(
                    tenantId,
                    workflowRequestId,
                    MODEL_CALLER_TYPE,
                    MODEL_CALLER_ID,
                    MODEL_STAGE,
                    logicalCallSequence.incrementAndGet(),
                    "你是数据库元数据助手，只输出「字段名=中文描述」逐行结果。",
                    prompt.toString()
            )).content();
            Map<String, String> parsed = parseCommentLines(generated);
            for (TableInfo.ColumnInfo col : pending) {
                String comment = parsed.get(col.getColumnName().toLowerCase(Locale.ROOT));
                String resolved = StringUtils.hasText(comment) ? comment : "无描述";
                col.setColumnComment(resolved);
                if (StringUtils.hasText(comment)) {
                    cacheService.cacheComment(tenantId + ":column:" + tableName + ":" + col.getColumnName(), resolved);
                }
            }
        } catch (PhysicalBillingException e) {
            throw e;
        } catch (Exception e) {
            skipModelComment[0] = true;
            log.warn("批量生成字段注释失败，本次同步剩余字段跳过模型注释: tenantId={}, table={}, reason={}",
                    tenantId, tableName, e.getMessage());
            for (TableInfo.ColumnInfo col : pending) {
                col.setColumnComment("无描述");
            }
        }
    }

    private Map<String, String> parseCommentLines(String text) {
        Map<String, String> parsed = new HashMap<>();
        if (!StringUtils.hasText(text)) {
            return parsed;
        }
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim().replaceAll("^[-*\\d.、\\s]+", "");
            int idx = trimmed.indexOf('=');
            if (idx <= 0 || idx == trimmed.length() - 1) {
                continue;
            }
            parsed.put(trimmed.substring(0, idx).trim().toLowerCase(Locale.ROOT),
                    trimmed.substring(idx + 1).trim());
        }
        return parsed;
    }

    /**
     * 解析并补全注释。
     *
     * @param tenantId   租户标识
     * @param workflowRequestId 扫描请求标识
     * @param logicalCallSequence 逻辑调用序列
     * @param type       类型（table/column）
     * @param name       名称
     * @param rawComment 原始注释
     * @param prompt     LLM 提示词
     * @return 注释文本
     * @author 余炘洋
     * @date 2026-01-22
     */
    private String resolveComment(
            String tenantId,
            String workflowRequestId,
            AtomicInteger logicalCallSequence,
            String type,
            String name,
            String rawComment,
            String prompt,
            boolean[] skipModelComment
    ) {
        if (StringUtils.hasText(rawComment)) {
            return rawComment;
        }
        String cacheKey = tenantId + ":" + type + ":" + name;
        String cached = cacheService.getCachedComment(cacheKey);
        if (StringUtils.hasText(cached)) {
            return cached;
        }
        // 注释生成是尽力而为的增强项：开户时模型授权往往尚未生效，
        // 若逐字段都试一次，200 张表 × 8 字段就是 1600 次无效调用加 1600 条堆栈。
        // 同一请求内首次失败后直接放弃，只留一条警告。
        if (skipModelComment[0]) {
            return "无描述";
        }
        try {
            String generated = modelGateway.chat(new ModelCallCommand(
                    tenantId,
                    workflowRequestId,
                    MODEL_CALLER_TYPE,
                    MODEL_CALLER_ID,
                    MODEL_STAGE,
                    logicalCallSequence.incrementAndGet(),
                    "你是数据库元数据助手，只输出简短中文描述。",
                    prompt
            )).content();
            String comment = StringUtils.hasText(generated) ? generated.trim() : "无描述";
            cacheService.cacheComment(cacheKey, comment);
            return comment;
        } catch (PhysicalBillingException e) {
            throw e;
        } catch (Exception e) {
            // 一旦失败就放弃本次同步的模型注释，避免逐字段重试把日志淹没
            skipModelComment[0] = true;
            log.warn("生成注释失败，本次同步剩余字段跳过模型注释: tenantId={}, firstFailure={}, reason={}",
                    tenantId, name, e.getMessage());
            return "无描述";
        }
    }

    /**
     * 解析逗号分隔字符串为集合。
     *
     * @param text 原始文本
     * @return 集合
     * @author 余炘洋
     * @date 2026-01-22
     */
    private Set<String> parseCsv(String text) {
        Set<String> set = new HashSet<>();
        if (!StringUtils.hasText(text)) {
            return set;
        }
        String[] items = text.split(",");
        for (String item : items) {
            String value = item.trim();
            if (!value.isEmpty()) {
                set.add(value.toLowerCase());
            }
        }
        return set;
    }
}
