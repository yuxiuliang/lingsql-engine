package com.lingsql.metadata.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.llm.LlmClient;
import com.lingsql.metadata.entity.MetadataExt;
import com.lingsql.metadata.model.AiSuggestRequest;
import com.lingsql.metadata.model.AiSuggestResponse;
import com.lingsql.metadata.model.MetadataFieldView;
import com.lingsql.metadata.model.MetadataTableView;
import com.lingsql.metadata.repository.MetadataExtRepository;
import com.lingsql.schema.model.TableInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 元数据扩展服务。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataExtService {

    private final MetadataExtRepository metadataExtRepository;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    /**
     * 合并业务注释，字典表优先。
     *
     * @param tenantId 租户标识
     * @param tables   表结构列表
     * @return 合并后的表结构
     * @author 余炘洋
     * @date 2026-01-23
     */
    public List<TableInfo> mergeComments(String tenantId, List<TableInfo> tables) {
        if (tables == null || tables.isEmpty()) {
            return tables;
        }
        List<MetadataExt> extList = metadataExtRepository.findByTenantId(tenantId);
        if (extList.isEmpty()) {
            return tables;
        }
        Map<String, MetadataExt> mapping = new HashMap<>();
        for (MetadataExt ext : extList) {
            String key = buildKey(ext.getTableName(), ext.getColumnName());
            mapping.put(key, ext);
        }
        for (TableInfo table : tables) {
            if (table.getColumns() == null) {
                continue;
            }
            for (TableInfo.ColumnInfo column : table.getColumns()) {
                MetadataExt ext = mapping.get(buildKey(table.getTableName(), column.getColumnName()));
                if (ext != null && StringUtils.hasText(ext.getBusinessComment())) {
                    column.setColumnComment(ext.getBusinessComment());
                }
            }
        }
        return tables;
    }

    /**
     * 查询表详情（含业务注释与隔离标记）。
     *
     * @param tenantId 租户标识
     * @param tableInfo 表结构
     * @return 表详情视图
     * @author 余炘洋
     * @date 2026-01-23
     */
    public MetadataTableView buildTableView(String tenantId, TableInfo tableInfo) {
        if (tableInfo == null) {
            return null;
        }
        List<MetadataExt> extList = metadataExtRepository.findByTenantIdAndTableName(tenantId, tableInfo.getTableName());
        Map<String, MetadataExt> mapping = new HashMap<>();
        for (MetadataExt ext : extList) {
            mapping.put(buildKey(ext.getTableName(), ext.getColumnName()), ext);
        }
        MetadataTableView view = new MetadataTableView();
        view.setSchemaName(tableInfo.getSchemaName());
        view.setTableName(tableInfo.getTableName());
        view.setTableComment(tableInfo.getTableComment());

        List<MetadataFieldView> columns = new ArrayList<>();
        if (tableInfo.getColumns() != null) {
            for (TableInfo.ColumnInfo column : tableInfo.getColumns()) {
                MetadataFieldView fieldView = new MetadataFieldView();
                fieldView.setColumnName(column.getColumnName());
                fieldView.setDataType(column.getDataType());
                fieldView.setColumnComment(column.getColumnComment());
                fieldView.setPrimaryKey(column.isPrimaryKey());
                MetadataExt ext = mapping.get(buildKey(tableInfo.getTableName(), column.getColumnName()));
                if (ext != null) {
                    fieldView.setBusinessComment(ext.getBusinessComment());
                    fieldView.setIsolatedField(ext.getIsIsolatedField() != null && ext.getIsIsolatedField() == 1);
                }
                columns.add(fieldView);
            }
        }
        view.setColumns(columns);
        return view;
    }

    /**
     * 更新字段业务注释。
     *
     * @param tenantId 租户标识
     * @param tableName 表名
     * @param columnName 字段名
     * @param comment 业务注释
     * @author 余炘洋
     * @date 2026-01-23
     */
    public void updateBusinessComment(String tenantId, String tableName, String columnName, String comment) {
        MetadataExt ext = metadataExtRepository.findByTenantIdAndTableNameAndColumnName(tenantId, tableName, columnName)
                .orElseGet(() -> createExt(tenantId, tableName, columnName));
        ext.setBusinessComment(comment);
        ext.setUpdateTime(LocalDateTime.now());
        metadataExtRepository.save(ext);
    }

    /**
     * 设置隔离字段。
     *
     * @param tenantId 租户标识
     * @param tableName 表名
     * @param columnName 字段名
     * @param isolated 是否隔离
     * @author 余炘洋
     * @date 2026-01-23
     */
    public void updateIsolationField(String tenantId, String tableName, String columnName, boolean isolated) {
        List<MetadataExt> extList = metadataExtRepository.findByTenantIdAndTableName(tenantId, tableName);
        for (MetadataExt ext : extList) {
            ext.setIsIsolatedField(0);
        }
        if (isolated && StringUtils.hasText(columnName)) {
            MetadataExt target = metadataExtRepository.findByTenantIdAndTableNameAndColumnName(tenantId, tableName, columnName)
                    .orElseGet(() -> createExt(tenantId, tableName, columnName));
            target.setIsIsolatedField(1);
            target.setUpdateTime(LocalDateTime.now());
            if (!extList.contains(target)) {
                extList.add(target);
            }
        }
        metadataExtRepository.saveAll(extList);
    }

    /**
     * 获取表级隔离字段。
     *
     * @param tenantId 租户标识
     * @param tableName 表名
     * @return 隔离字段
     * @author 余炘洋
     * @date 2026-01-23
     */
    public Optional<String> resolveIsolationField(String tenantId, String tableName) {
        return metadataExtRepository.findByTenantIdAndTableName(tenantId, tableName)
                .stream()
                .filter(ext -> ext.getIsIsolatedField() != null && ext.getIsIsolatedField() == 1)
                .map(MetadataExt::getColumnName)
                .findFirst();
    }

    /**
     * AI 语义解析建议。
     *
     * @param request 请求体
     * @return 建议列表
     * @author 余炘洋
     * @date 2026-01-23
     */
    public AiSuggestResponse aiSuggest(AiSuggestRequest request) {
        AiSuggestResponse response = new AiSuggestResponse();
        response.setSuggestions(new ArrayList<>());
        if (request == null || !StringUtils.hasText(request.getTableName())) {
            return response;
        }
        String systemPrompt = "你是数据库字段语义解析助手，只输出 JSON 数组，元素格式为 {\"columnName\":\"字段名\",\"comment\":\"中文含义\"}，不要任何额外文字。";
        String userPrompt = buildPrompt(request);
        try {
            String raw = llmClient.chat(systemPrompt, userPrompt);
            List<AiSuggestResponse.SuggestItem> items = objectMapper.readValue(raw,
                    new TypeReference<List<AiSuggestResponse.SuggestItem>>() {
                    });
            response.setSuggestions(items);
            return response;
        } catch (Exception ex) {
            log.warn("AI 语义解析失败: {}", ex.getMessage());
            return response;
        }
    }

    /**
     * 构建 AI 提示词。
     *
     * @param request 请求体
     * @return 提示词
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String buildPrompt(AiSuggestRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("表名：").append(request.getTableName()).append("\n字段列表：\n");
        if (request.getFields() != null) {
            for (AiSuggestRequest.FieldItem item : request.getFields()) {
                sb.append("- ").append(item.getName());
                if (StringUtils.hasText(item.getComment())) {
                    sb.append("（已有注释：").append(item.getComment()).append("）");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 创建扩展记录。
     *
     * @param tenantId 租户标识
     * @param tableName 表名
     * @param columnName 字段名
     * @return 扩展记录
     * @author 余炘洋
     * @date 2026-01-23
     */
    private MetadataExt createExt(String tenantId, String tableName, String columnName) {
        MetadataExt ext = new MetadataExt();
        ext.setTenantId(tenantId);
        ext.setTableName(tableName);
        ext.setColumnName(columnName);
        ext.setIsIsolatedField(0);
        ext.setCreateTime(LocalDateTime.now());
        ext.setUpdateTime(LocalDateTime.now());
        return ext;
    }

    /**
     * 构建映射键。
     *
     * @param tableName 表名
     * @param columnName 字段名
     * @return 键
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String buildKey(String tableName, String columnName) {
        return (tableName == null ? "" : tableName.toLowerCase()) + ":" + (columnName == null ? "" : columnName.toLowerCase());
    }
}
