package com.lingsql.executor;

import com.lingsql.agent.SqlAgentService;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.security.SqlRewriter;
import com.lingsql.security.SqlSecurityChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 智能 SQL 执行器：SQL 生成、改写、校验与执行。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartSqlExecutor {

    private final SqlAgentService agentService;
    private final SqlSecurityChecker securityChecker;
    private final SqlRewriter rewriter;
    private final TenantDbConfigRepository tenantDbConfigRepository;

    @Qualifier("businessJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    /**
     * 执行自然语言查询。
     *
     * @param tenantId 租户标识
     * @param question 用户问题
     * @return 执行结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    public SqlExecutionResult execute(String tenantId, String question) {
        TenantDbConfig config = tenantDbConfigRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("租户不存在: " + tenantId));

        return executeWithRetry(config, tenantId, question);
    }

    /**
     * 执行并带一次重试逻辑。
     *
     * @param config   租户配置
     * @param tenantId 租户标识
     * @param question 用户问题
     * @return 执行结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    private SqlExecutionResult executeWithRetry(TenantDbConfig config, String tenantId, String question) {
        try {
            return executeOnce(config, tenantId, question, null);
        } catch (SqlExecutionException ex) {
            log.warn("首次执行失败，准备重试: {}", ex.getMessage());
            try {
                return executeOnce(config, tenantId, question, ex.getMessage());
            } catch (SqlExecutionException retryEx) {
                log.error("重试失败: {}", retryEx.getMessage());
                return SqlExecutionResult.failure(retryEx.getSql(), retryEx.getMessage());
            }
        }
    }

    /**
     * 单次执行流程。
     *
     * @param config   租户配置
     * @param tenantId 租户标识
     * @param question 用户问题
     * @param reason   失败原因
     * @return 执行结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    private SqlExecutionResult executeOnce(TenantDbConfig config, String tenantId, String question, String reason) {
        String rawSql = reason == null
                ? agentService.generateSql(tenantId, question)
                : agentService.generateSqlWithRetry(tenantId, question, reason);

        if (rawSql == null || rawSql.isBlank() || rawSql.toUpperCase().startsWith("ERROR")) {
            throw new SqlExecutionException("模型未能生成可执行 SQL，请补充查询条件", rawSql);
        }

        String rewrittenSql = rewriter.rewrite(rawSql, config, tenantId);
        try {
            securityChecker.check(rewrittenSql, config);
            List<Map<String, Object>> data = jdbcTemplate.queryForList(rewrittenSql);
            return SqlExecutionResult.success(rewrittenSql, data);
        } catch (Exception ex) {
            throw new SqlExecutionException(ex.getMessage(), rewrittenSql);
        }
    }
}
