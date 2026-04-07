package com.lingsql.executor;

import com.lingsql.agent.SqlAgentService;
import com.lingsql.audit.model.SqlExecuteLogCommand;
import com.lingsql.audit.service.SqlExecuteLogService;
import com.lingsql.billing.TenantBillingService;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.security.SqlRewriter;
import com.lingsql.security.SqlSecurityChecker;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmartSqlExecutorResilienceTest {

    @Test
    void executeShouldKeepSuccessResultWhenChargeFailsInFinally() {
        SqlAgentService agentService = Mockito.mock(SqlAgentService.class);
        SqlSecurityChecker securityChecker = Mockito.mock(SqlSecurityChecker.class);
        SqlRewriter rewriter = Mockito.mock(SqlRewriter.class);
        TenantDbConfigRepository configRepository = Mockito.mock(TenantDbConfigRepository.class);
        SqlExecuteLogService logService = Mockito.mock(SqlExecuteLogService.class);
        SqlTaskTracker taskTracker = Mockito.mock(SqlTaskTracker.class);
        TenantBillingService billingService = Mockito.mock(TenantBillingService.class);
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);

        TenantDbConfig config = new TenantDbConfig();
        config.setTenantId("TENANT_A");
        when(configRepository.findByTenantId("TENANT_A")).thenReturn(Optional.of(config));
        when(agentService.generateSql("TENANT_A", "查询今天订单数")).thenReturn("SELECT 1 AS cnt");
        when(rewriter.rewrite(anyString(), any(TenantDbConfig.class), anyString())).thenReturn("SELECT 1 AS cnt");
        when(jdbcTemplate.queryForList("SELECT 1 AS cnt")).thenReturn(List.of(Map.of("cnt", 1)));
        doThrow(new RuntimeException("计费系统异常"))
                .when(billingService).charge(Mockito.eq("TENANT_A"), any(SqlExecuteLogCommand.class));

        SmartSqlExecutor executor = new SmartSqlExecutor(
                agentService,
                securityChecker,
                rewriter,
                configRepository,
                logService,
                taskTracker,
                billingService,
                jdbcTemplate
        );

        SqlExecutionResult result = executor.execute("TENANT_A", "查询今天订单数", "WEB");

        assertTrue(result.isSuccess());
        assertEquals("SELECT 1 AS cnt", result.getSql());
        verify(taskTracker).finish("TENANT_A");
        ArgumentCaptor<SqlExecuteLogCommand> logCaptor = ArgumentCaptor.forClass(SqlExecuteLogCommand.class);
        verify(logService).record(logCaptor.capture());
        assertTrue(logCaptor.getValue().isSuccess());
    }

    @Test
    void executeShouldExposeSelfHealingTraceWhenRetrySucceeds() {
        SqlAgentService agentService = Mockito.mock(SqlAgentService.class);
        SqlSecurityChecker securityChecker = Mockito.mock(SqlSecurityChecker.class);
        SqlRewriter rewriter = Mockito.mock(SqlRewriter.class);
        TenantDbConfigRepository configRepository = Mockito.mock(TenantDbConfigRepository.class);
        SqlExecuteLogService logService = Mockito.mock(SqlExecuteLogService.class);
        SqlTaskTracker taskTracker = Mockito.mock(SqlTaskTracker.class);
        TenantBillingService billingService = Mockito.mock(TenantBillingService.class);
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);

        TenantDbConfig config = new TenantDbConfig();
        config.setTenantId("TENANT_A");
        when(configRepository.findByTenantId("TENANT_A")).thenReturn(Optional.of(config));
        when(agentService.generateSql("TENANT_A", "查询今天订单数")).thenReturn("SELECT bad AS cnt");
        when(agentService.generateSqlWithRetry(Mockito.eq("TENANT_A"), Mockito.eq("查询今天订单数"), anyString()))
                .thenReturn("SELECT 1 AS cnt");
        when(rewriter.rewrite(anyString(), any(TenantDbConfig.class), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jdbcTemplate.queryForList("SELECT bad AS cnt"))
                .thenThrow(new RuntimeException("Unknown column"));
        when(jdbcTemplate.queryForList("SELECT 1 AS cnt"))
                .thenReturn(List.of(Map.of("cnt", 1)));

        SmartSqlExecutor executor = new SmartSqlExecutor(
                agentService,
                securityChecker,
                rewriter,
                configRepository,
                logService,
                taskTracker,
                billingService,
                jdbcTemplate
        );

        SqlExecutionResult result = executor.execute("TENANT_A", "查询今天订单数", "WEB");

        assertTrue(result.isSuccess());
        assertTrue(result.getTrace().stream()
                .anyMatch(step -> "THOUGHT".equals(step.getType()) && Integer.valueOf(2).equals(step.getAttempt())));
        assertTrue(result.getTrace().stream()
                .anyMatch(step -> "FINAL".equals(step.getType())
                        && "SUCCESS".equals(step.getStatus())
                        && Integer.valueOf(2).equals(step.getAttempt())));
    }
}
