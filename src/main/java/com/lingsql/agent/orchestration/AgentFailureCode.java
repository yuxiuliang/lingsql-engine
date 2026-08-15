package com.lingsql.agent.orchestration;

/**
 * Agent 公开失败分类。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public enum AgentFailureCode {
    INVALID_REQUEST,
    TENANT_DATABASE_NOT_CONFIGURED,
    PLUGIN_POLICY_DENIED,
    BUDGET_EXHAUSTED,
    DEADLINE_EXCEEDED,
    BILLING_SETTLEMENT_FAILED,
    AUDIT_PERSIST_FAILED,
    PLUGIN_EXECUTION_FAILED,
    SQL_EXECUTION_FAILED,
    INTERNAL_ERROR
}
