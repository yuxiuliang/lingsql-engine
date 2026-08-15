package com.lingsql.agent.orchestration.sql;

/**
 * 尚未通过安全审计的候选 SQL。
 *
 * @param value SQL 文本
 * @author 余炘洋
 * @date 2026-08-15
 */
public record CandidateSql(String value) {

    public CandidateSql {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("候选 SQL 不能为空");
        }
        value = value.trim();
    }
}
