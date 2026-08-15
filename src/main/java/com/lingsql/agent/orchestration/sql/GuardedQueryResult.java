package com.lingsql.agent.orchestration.sql;

import java.util.List;
import java.util.Map;

/**
 * 通过安全审计后的查询结果。
 *
 * @param approvedSql 通过改写和校验的 SQL
 * @param data 查询数据
 * @param auditDurationMs 安全审计耗时
 * @param queryDurationMs 数据库查询耗时
 * @author 余炘洋
 * @date 2026-08-15
 */
public record GuardedQueryResult(
        String approvedSql,
        List<Map<String, Object>> data,
        long auditDurationMs,
        long queryDurationMs
) {

    public GuardedQueryResult {
        data = data == null ? List.of() : List.copyOf(data);
    }
}
