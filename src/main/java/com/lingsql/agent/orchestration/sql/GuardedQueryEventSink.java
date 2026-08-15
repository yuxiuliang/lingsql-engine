package com.lingsql.agent.orchestration.sql;

/**
 * SQL 安全查询阶段事件接收器。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public interface GuardedQueryEventSink {

    GuardedQueryEventSink NO_OP = new GuardedQueryEventSink() {
    };

    /**
     * 安全审计通过后、数据库执行前触发。
     *
     * @param approvedSql 已通过审计的 SQL
     * @param auditDurationMs 审计耗时
     */
    default void onAuditApproved(String approvedSql, long auditDurationMs) {
    }

    /**
     * 数据库查询即将开始时触发。
     *
     * @param approvedSql 已通过审计的 SQL
     */
    default void onQueryStarted(String approvedSql) {
    }
}
