package com.lingsql.executor;

import com.lingsql.config.LingSqlProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * SQL 自愈策略，限定可重试的错误类型与最大轮次。
 *
 * @author 余炘洋
 * @date 2026-07-29
 */
@Component
@RequiredArgsConstructor
public class SqlRecoveryPolicy {

    private static final String NO_SCHEMA_MATCH = "ERROR:NO_SCHEMA_MATCH";
    private static final String UNKNOWN_COLUMN = "unknown column";
    private static final String UNKNOWN_TABLE = "unknown table";
    private static final String TABLE_NOT_EXIST = "doesn't exist";
    private static final String SQL_SYNTAX = "syntax error";

    private final LingSqlProperties properties;

    /**
     * 获取本次执行允许的最大尝试轮次。
     *
     * @return 最大轮次
     * @author 余炘洋
     * @date 2026-07-29
     */
    public int resolveMaxAttempts() {
        return properties.getSqlExecution().getSelfHealingMaxAttempts();
    }

    /**
     * 根据受控错误信息决定是否允许自愈。
     *
     * @param exception 本轮 SQL 执行异常
     * @return 自愈决策
     * @author 余炘洋
     * @date 2026-07-29
     */
    public SqlRecoveryDecision assess(SqlExecutionException exception) {
        if (exception == null) {
            return SqlRecoveryDecision.notRecoverable();
        }
        if (NO_SCHEMA_MATCH.equalsIgnoreCase(normalize(exception.getSql()))) {
            return new SqlRecoveryDecision(true, true, "未匹配到可用表结构，请基于最新元数据重新生成 SQL。");
        }
        String message = normalize(exception.getMessage()).toLowerCase(Locale.ROOT);
        if (message.contains(UNKNOWN_COLUMN)) {
            return new SqlRecoveryDecision(true, true, "查询字段与当前元数据不匹配，请基于最新字段重新生成 SQL。");
        }
        if (message.contains(UNKNOWN_TABLE) || message.contains(TABLE_NOT_EXIST)) {
            return new SqlRecoveryDecision(true, true, "查询表与当前元数据不匹配，请基于最新表结构重新生成 SQL。");
        }
        if (message.contains(SQL_SYNTAX)) {
            return new SqlRecoveryDecision(true, false, "上一轮 SQL 存在语法问题，请重新生成符合 MySQL 8.x 的 SELECT SQL。");
        }
        return SqlRecoveryDecision.notRecoverable();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
