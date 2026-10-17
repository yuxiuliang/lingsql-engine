package com.lingsql.chat.model;

/**
 * 会话实体引用：记录上一条成功查询结果中的关联实体，供多轮追问消解使用。
 *
 * @param tableName  实体所在表
 * @param columnName 实体关联字段（主键或主键回退字段）
 * @param value      实体值
 * @param rowCount   上一条结果行数
 * @author 余炘洋
 * @date 2026-10-17
 */
public record ChatEntityReference(String tableName, String columnName, String value, int rowCount) {
}
