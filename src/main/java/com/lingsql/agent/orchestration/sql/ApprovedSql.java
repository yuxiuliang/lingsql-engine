package com.lingsql.agent.orchestration.sql;

/**
 * 仅在 SQL 安全模块内部流转的已批准 SQL。
 *
 * @param value SQL 文本
 * @author 余炘洋
 * @date 2026-08-15
 */
record ApprovedSql(String value) {
}
