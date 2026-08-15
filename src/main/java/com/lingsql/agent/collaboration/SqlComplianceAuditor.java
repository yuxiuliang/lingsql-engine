package com.lingsql.agent.collaboration;

import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.security.SqlRewriter;
import com.lingsql.security.SqlSecurityChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 合规审计角色，负责将候选 SQL 改写为可执行的受控 SQL，并完成执行前校验。
 *
 * @author 余炘洋
 * @date 2026-07-29
 */
@Service
@RequiredArgsConstructor
public class SqlComplianceAuditor {

    private final SqlRewriter sqlRewriter;
    private final SqlSecurityChecker sqlSecurityChecker;

    /**
     * 审计候选 SQL。
     *
     * @param rawSql 原始候选 SQL
     * @param config 租户数据源配置
     * @param tenantId 当前租户标识
     * @return 已通过审计的 SQL
     * @author 余炘洋
     * @date 2026-07-29
     */
    public String audit(String rawSql, TenantDbConfig config, String tenantId) {
        String rewrittenSql = sqlRewriter.rewrite(rawSql, config, tenantId);
        sqlSecurityChecker.check(rewrittenSql, config, tenantId);
        return rewrittenSql;
    }
}
