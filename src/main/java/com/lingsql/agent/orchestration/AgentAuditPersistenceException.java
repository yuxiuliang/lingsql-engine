package com.lingsql.agent.orchestration;

/**
 * Agent 执行审计持久化异常。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public class AgentAuditPersistenceException extends RuntimeException {

    public AgentAuditPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
