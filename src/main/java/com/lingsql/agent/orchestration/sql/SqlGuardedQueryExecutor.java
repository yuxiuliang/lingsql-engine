package com.lingsql.agent.orchestration.sql;

import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.security.SqlRewriter;
import com.lingsql.security.SqlSecurityChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 不可拆分的 SQL 安全查询模块。
 *
 * <p>候选 SQL 必须在本模块内依次完成租户改写、安全校验和 JDBC 查询。调用方不能获得绕过审计的 JDBC 接缝。</p>
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Service
@RequiredArgsConstructor
public class SqlGuardedQueryExecutor {

    private final SqlRewriter sqlRewriter;
    private final SqlSecurityChecker sqlSecurityChecker;

    @Qualifier("businessJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    /**
     * 执行安全查询。
     *
     * @param candidateSql 候选 SQL
     * @param config 租户数据源配置
     * @param tenantId 租户标识
     * @return 受控查询结果
     * @author 余炘洋
     * @date 2026-08-15
     */
    public GuardedQueryResult execute(CandidateSql candidateSql, TenantDbConfig config, String tenantId) {
        return execute(candidateSql, config, tenantId, GuardedQueryEventSink.NO_OP);
    }

    /**
     * 执行安全查询并按事实时序发送阶段事件。
     *
     * @param candidateSql 候选 SQL
     * @param config 租户数据源配置
     * @param tenantId 租户标识
     * @param eventSink 阶段事件接收器
     * @return 受控查询结果
     * @author 余炘洋
     * @date 2026-08-15
     */
    public GuardedQueryResult execute(
            CandidateSql candidateSql,
            TenantDbConfig config,
            String tenantId,
            GuardedQueryEventSink eventSink
    ) {
        long auditStart = System.currentTimeMillis();
        ApprovedSql approvedSql;
        try {
            String rewrittenSql = sqlRewriter.rewrite(candidateSql.value(), config, tenantId);
            sqlSecurityChecker.check(rewrittenSql, config, tenantId);
            approvedSql = new ApprovedSql(rewrittenSql);
        } catch (Exception exception) {
            throw new GuardedQueryException(
                    GuardedQueryStage.AUDIT,
                    candidateSql.value(),
                    System.currentTimeMillis() - auditStart,
                    exception
            );
        }
        long auditDurationMs = System.currentTimeMillis() - auditStart;
        GuardedQueryEventSink sink = eventSink == null ? GuardedQueryEventSink.NO_OP : eventSink;
        sink.onAuditApproved(approvedSql.value(), auditDurationMs);
        sink.onQueryStarted(approvedSql.value());
        long queryStart = System.currentTimeMillis();
        try {
            List<Map<String, Object>> data = jdbcTemplate.queryForList(approvedSql.value());
            return new GuardedQueryResult(
                    approvedSql.value(),
                    data,
                    auditDurationMs,
                    System.currentTimeMillis() - queryStart
            );
        } catch (Exception exception) {
            throw new GuardedQueryException(
                    GuardedQueryStage.QUERY,
                    approvedSql.value(),
                    auditDurationMs,
                    exception
            );
        }
    }
}
