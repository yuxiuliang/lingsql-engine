package com.lingsql.metadata.model;

import lombok.Data;

import java.util.List;

/**
 * AI 语义解析请求体。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
public class AiSuggestRequest {

    private String tenantId;
    private String tableName;
    private List<FieldItem> fields;

    /**
     * 字段简要描述。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Data
    public static class FieldItem {
        private String name;
        private String comment;
    }
}

