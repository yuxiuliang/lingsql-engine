package com.lingsql.agent.orchestration.sql;

import lombok.Getter;

/**
 * 受控查询执行异常。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Getter
public class GuardedQueryException extends RuntimeException {

    private final GuardedQueryStage stage;
    private final String sql;
    private final long auditDurationMs;

    public GuardedQueryException(
            GuardedQueryStage stage,
            String sql,
            long auditDurationMs,
            Throwable cause
    ) {
        super(cause == null ? null : cause.getMessage(), cause);
        this.stage = stage;
        this.sql = sql;
        this.auditDurationMs = auditDurationMs;
    }
}
