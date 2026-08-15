package com.lingsql.billing.acceptance;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 计费验收专用账本只读仓库。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Repository
@RequiredArgsConstructor
class BillingAcceptanceLedgerRepository {

    private static final int STATUS_APPLIED = 1;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    long currentWatermark(String tenantId) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM t_lingsql_engine_billing_ledger WHERE tenant_id = :tenantId",
                new MapSqlParameterSource("tenantId", tenantId),
                Long.class
        );
        return value == null ? 0L : value;
    }

    IntervalEvidence intervalEvidence(String tenantId,
                                      long startWatermark,
                                      long endWatermark,
                                      List<String> requestIds) {
        String sql = """
                SELECT COUNT(*) AS event_count,
                       SUM(CASE WHEN status = :applied AND apply_time IS NOT NULL THEN 1 ELSE 0 END)
                           AS applied_count,
                       SUM(CASE WHEN status IS NULL OR status <> :applied OR apply_time IS NULL THEN 1 ELSE 0 END)
                           AS invalid_count,
                       SUM(CASE WHEN workflow_request_id IS NULL
                                     OR workflow_request_id NOT IN (:requestIds)
                                THEN 1 ELSE 0 END) AS unrelated_count,
                       COALESCE(SUM(CASE WHEN event_name = 'RESERVE' THEN cost_amount ELSE 0 END), 0)
                           AS reserve_amount,
                       COALESCE(SUM(CASE WHEN event_name IN ('REFUND', 'CANCEL') THEN cost_amount ELSE 0 END), 0)
                           AS refund_amount
                  FROM t_lingsql_engine_billing_ledger
                 WHERE tenant_id = :tenantId
                   AND id > :startWatermark
                   AND id <= :endWatermark
                """;
        MapSqlParameterSource parameters = baseParameters(tenantId, startWatermark, endWatermark, requestIds)
                .addValue("applied", STATUS_APPLIED);
        return jdbcTemplate.queryForObject(sql, parameters, (resultSet, rowNum) -> new IntervalEvidence(
                resultSet.getLong("event_count"),
                resultSet.getLong("applied_count"),
                resultSet.getLong("invalid_count"),
                resultSet.getLong("unrelated_count"),
                resultSet.getLong("reserve_amount"),
                resultSet.getLong("refund_amount")
        ));
    }

    long countScopeEventsAtOrBefore(String tenantId, long watermark, List<String> requestIds) {
        Long value = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM t_lingsql_engine_billing_ledger
                 WHERE tenant_id = :tenantId
                   AND id <= :watermark
                   AND workflow_request_id IN (:requestIds)
                """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("watermark", watermark)
                        .addValue("requestIds", requestIds),
                Long.class
        );
        return value == null ? 0L : value;
    }

    List<InvocationLedgerEvidence> scopeInvocationEvidence(String tenantId,
                                                           long startWatermark,
                                                           long endWatermark,
                                                           List<String> requestIds) {
        String sql = """
                SELECT invocation_id,
                       MIN(workflow_request_id) AS workflow_request_id,
                       COUNT(*) AS event_count,
                       SUM(CASE WHEN status = :applied AND apply_time IS NOT NULL THEN 1 ELSE 0 END)
                           AS applied_count,
                       SUM(CASE WHEN event_name = 'RESERVE' THEN 1 ELSE 0 END) AS reserve_count,
                       SUM(CASE WHEN event_name = 'REFUND' THEN 1 ELSE 0 END) AS refund_count,
                       SUM(CASE WHEN event_name = 'CANCEL' THEN 1 ELSE 0 END) AS cancel_count,
                       SUM(CASE WHEN event_name = 'CLOSE' THEN 1 ELSE 0 END) AS close_count,
                       COALESCE(SUM(CASE WHEN event_name = 'RESERVE' THEN cost_amount ELSE 0 END), 0)
                           AS reserve_amount,
                       COALESCE(SUM(CASE WHEN event_name = 'REFUND' THEN cost_amount ELSE 0 END), 0)
                           AS refund_amount,
                       COALESCE(SUM(CASE WHEN event_name = 'CANCEL' THEN cost_amount ELSE 0 END), 0)
                           AS cancel_amount,
                       COALESCE(SUM(CASE WHEN event_name = 'CLOSE' THEN cost_amount ELSE 0 END), 0)
                           AS close_amount
                  FROM t_lingsql_engine_billing_ledger
                 WHERE tenant_id = :tenantId
                   AND id > :startWatermark
                   AND id <= :endWatermark
                   AND workflow_request_id IN (:requestIds)
                 GROUP BY invocation_id
                 ORDER BY invocation_id
                """;
        MapSqlParameterSource parameters = baseParameters(tenantId, startWatermark, endWatermark, requestIds)
                .addValue("applied", STATUS_APPLIED);
        return jdbcTemplate.query(sql, parameters, (resultSet, rowNum) -> new InvocationLedgerEvidence(
                resultSet.getString("invocation_id"),
                resultSet.getString("workflow_request_id"),
                resultSet.getLong("event_count"),
                resultSet.getLong("applied_count"),
                resultSet.getLong("reserve_count"),
                resultSet.getLong("refund_count"),
                resultSet.getLong("cancel_count"),
                resultSet.getLong("close_count"),
                resultSet.getLong("reserve_amount"),
                resultSet.getLong("refund_amount"),
                resultSet.getLong("cancel_amount"),
                resultSet.getLong("close_amount")
        ));
    }

    private MapSqlParameterSource baseParameters(String tenantId,
                                                 long startWatermark,
                                                 long endWatermark,
                                                 List<String> requestIds) {
        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("startWatermark", startWatermark)
                .addValue("endWatermark", endWatermark)
                .addValue("requestIds", requestIds);
    }

    record IntervalEvidence(
            long eventCount,
            long appliedCount,
            long invalidCount,
            long unrelatedCount,
            long reserveAmount,
            long refundAmount
    ) {

        long netAmount() {
            return Math.subtractExact(reserveAmount, refundAmount);
        }
    }

    record InvocationLedgerEvidence(
            String invocationId,
            String workflowRequestId,
            long eventCount,
            long appliedCount,
            long reserveCount,
            long refundCount,
            long cancelCount,
            long closeCount,
            long reserveAmount,
            long refundAmount,
            long cancelAmount,
            long closeAmount
    ) {

        long netAmount() {
            return Math.subtractExact(reserveAmount, Math.addExact(refundAmount, cancelAmount));
        }
    }
}
