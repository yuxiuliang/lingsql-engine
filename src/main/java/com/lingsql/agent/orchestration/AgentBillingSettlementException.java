package com.lingsql.agent.orchestration;

/**
 * Agent 执行后的计费结算异常。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public class AgentBillingSettlementException extends RuntimeException {

    public AgentBillingSettlementException(String message, Throwable cause) {
        super(message, cause);
    }
}
