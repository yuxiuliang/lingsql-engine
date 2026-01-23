package com.lingsql.metadata.model;

import lombok.Data;

import java.util.List;

/**
 * AI 语义解析响应体。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
public class AiSuggestResponse {

    private List<SuggestItem> suggestions;

    /**
     * 建议项。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Data
    public static class SuggestItem {
        private String columnName;
        private String comment;
    }
}

