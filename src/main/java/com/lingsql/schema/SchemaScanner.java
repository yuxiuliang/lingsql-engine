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
                            "请为数据库表 " + tableName + " 生成一句简短中文业务说明"));

                    Map<String, Boolean> primaryKeys = loadPrimaryKeys(metaData, catalog, tableName);

                    List<TableInfo.ColumnInfo> columns = new ArrayList<>();
                    try (ResultSet cols = metaData.getColumns(catalog, null, tableName, null)) {
                        while (cols.next()) {
                            TableInfo.ColumnInfo col = new TableInfo.ColumnInfo();
                            String columnName = cols.getString("COLUMN_NAME");
                            col.setColumnName(columnName);
                            col.setDataType(cols.getString("TYPE_NAME"));

                            String colComment = cols.getString("REMARKS");
                            String colPrompt = "请为字段 " + tableName + "." + columnName + " 生成简短中文含义";
                            col.setColumnComment(resolveComment(tenantId, workflowRequestId, logicalCallSequence,
                                    "column", tableName + ":" + columnName,
                                    colComment, colPrompt));
                            col.setPrimaryKey(primaryKeys.getOrDefault(columnName.toLowerCase(), false));
                            columns.add(col);
                        }
                    }
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
            String prompt
    ) {
        if (StringUtils.hasText(rawComment)) {
            return rawComment;
        }
        String cacheKey = tenantId + ":" + type + ":" + name;
        String cached = cacheService.getCachedComment(cacheKey);
        if (StringUtils.hasText(cached)) {
            return cached;
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
            log.warn("生成注释失败: {}", name, e);
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
