package com.lingsql.schema.model;

import lombok.Data;

import java.util.List;

/**
 * 表结构元数据模型。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Data
public class TableInfo {
    private String tableName;
    private String tableComment;
    private List<ColumnInfo> columns;

    /**
     * 列信息模型。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Data
    public static class ColumnInfo {
        private String columnName;
        private String dataType;
        private String columnComment;
        private boolean isPrimaryKey;
    }
}
