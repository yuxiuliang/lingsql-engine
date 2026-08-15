package com.lingsql.billing.invocation;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 逐物理调用不可变账本写入仓库。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Repository
@RequiredArgsConstructor
class PhysicalBillingLedgerRepository {

    private static final int STATUS_APPLIED = 1;
    private static final int EVENT_RESERVE = 0;
    private static final int EVENT_REFUND = 1;
    private static final int EVENT_CLOSE = 2;

    private static final String INSERT_SQL = """
            INSERT INTO t_lingsql_engine_billing_ledger
                (event_id, invocation_id, workflow_request_id, event_name,
                 request_id, event_seq, tenant_id, model_name, token_count,
                 unit_cost, cost_amount, billing_version, event_type, status,
                 create_time, apply_time)
            VALUES
                (:eventId, :invocationId, :workflowRequestId, :eventName,
                 :requestId, :eventSeq, :tenantId, :modelName, :tokenCount,
                 :unitCost, :costAmount, :billingVersion, :eventType, :status,
                 :createTime, :applyTime)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    void appendReserve(BillingInvocationEntity entity, LocalDateTime now) {
        append(entity, "RESERVE", EVENT_RESERVE, 1, entity.getMaxBillableTokens(),
                entity.getReservedAmount(), now);
    }

    void appendRefund(BillingInvocationEntity entity, long refundAmount, LocalDateTime now) {
        append(entity, "REFUND", EVENT_REFUND, 2, 0, refundAmount, now);
    }

    void appendClose(BillingInvocationEntity entity, int actualTokens, long actualAmount, LocalDateTime now) {
        append(entity, "CLOSE", EVENT_CLOSE, 3, actualTokens, actualAmount, now);
    }

    void appendCancel(BillingInvocationEntity entity, LocalDateTime now) {
        append(entity, "CANCEL", EVENT_REFUND, 2, 0, entity.getReservedAmount(), now);
    }

    private void append(BillingInvocationEntity entity,
                        String eventName,
                        int eventType,
                        int eventSeq,
                        int tokenCount,
                        long amount,
                        LocalDateTime now) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("eventId", compactUuid())
                .addValue("invocationId", entity.getInvocationId())
                .addValue("workflowRequestId", entity.getWorkflowRequestId())
                .addValue("eventName", eventName)
                .addValue("requestId", entity.getInvocationId())
                .addValue("eventSeq", eventSeq)
                .addValue("tenantId", entity.getTenantId())
                .addValue("modelName", entity.getModelName())
                .addValue("tokenCount", tokenCount)
                .addValue("unitCost", entity.getUnitCost())
                .addValue("costAmount", amount)
                .addValue("billingVersion", entity.getBillingVersion())
                .addValue("eventType", eventType)
                .addValue("status", STATUS_APPLIED)
                .addValue("createTime", now)
                .addValue("applyTime", now);
        jdbcTemplate.update(INSERT_SQL, parameters);
    }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
