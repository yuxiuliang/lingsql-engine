package com.lingsql.executor;

import com.lingsql.agent.SqlAgentService;
import com.lingsql.agent.orchestration.AgentAuditPersistenceException;
import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.agent.orchestration.sql.SqlGuardedQueryExecutor;
import com.lingsql.audit.model.SqlExecuteLogCommand;
import com.lingsql.audit.service.SqlExecuteLogService;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.llm.ModelCallResult;
import com.lingsql.schema.MetadataCacheService;
import com.lingsql.schema.model.TableInfo;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmartSqlExecutorResilienceTest {

    @Test
    void executeShouldFailClosedWhenAuditPersistenceFails() {
        SqlAgentService agentService = Mockito.mock(SqlAgentService.class);
        SqlSecurityChecker securityChecker = Mockito.mock(SqlSecurityChecker.class);
        SqlRewriter rewriter = Mockito.mock(SqlRewriter.class);
        TenantDbConfigRepository configRepository = Mockito.mock(TenantDbConfigRepository.class);
        SqlExecuteLogService logService = Mockito.mock(SqlExecuteLogService.class);
        SqlTaskTracker taskTracker = Mockito.mock(SqlTaskTracker.class);
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);

        TenantDbConfig config = new TenantDbConfig();
        config.setTenantId("TENANT_A");
        when(configRepository.findByTenantId("TENANT_A")).thenReturn(Optional.of(config));
        when(agentService.generateSql(any(AgentExecutionContext.class), eq("查询今天订单数"), eq(1)))
                .thenReturn(modelResult("SELECT 1 AS cnt"));
        when(rewriter.rewrite(anyString(), any(TenantDbConfig.class), anyString())).thenReturn("SELECT 1 AS cnt");
        when(jdbcTemplate.queryForList("SELECT 1 AS cnt")).thenReturn(List.of(Map.of("cnt", 1)));
        doThrow(new RuntimeException("审计存储异常"))
                .when(logService).record(any(SqlExecuteLogCommand.class));

        SmartSqlExecutor executor = new SmartSqlExecutor(
                agentService,
                new SqlGuardedQueryExecutor(rewriter, securityChecker, jdbcTemplate),
                new SqlRecoveryPolicy(new LingSqlProperties()),
                new SchemaBackCheckService(Mockito.mock(MetadataCacheService.class)),
                configRepository,
                logService,
                taskTracker
        );

        AgentAuditPersistenceException exception = assertThrows(
                AgentAuditPersistenceException.class,
                () -> executor.execute("TENANT_A", "查询今天订单数", "WEB")
        );

        assertEquals("执行日志写入失败", exception.getMessage());
        assertEquals("审计存储异常", exception.getCause().getMessage());
        verify(taskTracker).finish("TENANT_A");
        ArgumentCaptor<SqlExecuteLogCommand> logCaptor = ArgumentCaptor.forClass(SqlExecuteLogCommand.class);
        verify(logService).record(logCaptor.capture());
        assertTrue(logCaptor.getValue().isSuccess());
    }

    @Test
    void executeShouldExposeMultiRoundSelfHealingTraceWhenThirdAttemptSucceeds() {
        SqlAgentService agentService = Mockito.mock(SqlAgentService.class);
        SqlSecurityChecker securityChecker = Mockito.mock(SqlSecurityChecker.class);
        SqlRewriter rewriter = Mockito.mock(SqlRewriter.class);
        TenantDbConfigRepository configRepository = Mockito.mock(TenantDbConfigRepository.class);
        SqlExecuteLogService logService = Mockito.mock(SqlExecuteLogService.class);
        SqlTaskTracker taskTracker = Mockito.mock(SqlTaskTracker.class);
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);

        TenantDbConfig config = new TenantDbConfig();
        config.setTenantId("TENANT_A");
        when(configRepository.findByTenantId("TENANT_A")).thenReturn(Optional.of(config));
        when(agentService.generateSql(any(AgentExecutionContext.class), eq("查询今天订单数"), eq(1)))
                .thenReturn(modelResult("SELECT bad AS cnt"));
        when(agentService.generateSqlWithRetry(
                any(AgentExecutionContext.class),
                eq("查询今天订单数"),
                anyString(),
                anyInt()
        )).thenReturn(modelResult("SELECT bad_again AS cnt"), modelResult("SELECT 1 AS cnt"));
        when(rewriter.rewrite(anyString(), any(TenantDbConfig.class), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jdbcTemplate.queryForList("SELECT bad AS cnt"))
                .thenThrow(new RuntimeException("Unknown column"));
        when(jdbcTemplate.queryForList("SELECT bad_again AS cnt"))
                .thenThrow(new RuntimeException("Unknown column"));
        when(jdbcTemplate.queryForList("SELECT 1 AS cnt"))
                .thenReturn(List.of(Map.of("cnt", 1)));

        SmartSqlExecutor executor = new SmartSqlExecutor(
                agentService,
                new SqlGuardedQueryExecutor(rewriter, securityChecker, jdbcTemplate),
                new SqlRecoveryPolicy(new LingSqlProperties()),
                new SchemaBackCheckService(Mockito.mock(MetadataCacheService.class)),
                configRepository,
                logService,
                taskTracker
        );

        SqlExecutionResult result = executor.execute("TENANT_A", "查询今天订单数", "WEB");

        assertTrue(result.isSuccess());
        assertTrue(result.getTrace().stream()
                .anyMatch(step -> "ACTION".equals(step.getType()) && Integer.valueOf(3).equals(step.getAttempt())));
        assertTrue(result.getTrace().stream()
                .anyMatch(step -> "FINAL".equals(step.getType())
                        && "SUCCESS".equals(step.getStatus())
                        && Integer.valueOf(3).equals(step.getAttempt())));
        verify(agentService, Mockito.times(2)).refreshSchema("TENANT_A");
    }

    @Test
    void executeShouldThrowClearErrorWhenTenantDbConfigMissing() {
        SqlAgentService agentService = Mockito.mock(SqlAgentService.class);
        SqlSecurityChecker securityChecker = Mockito.mock(SqlSecurityChecker.class);
        SqlRewriter rewriter = Mockito.mock(SqlRewriter.class);
        TenantDbConfigRepository configRepository = Mockito.mock(TenantDbConfigRepository.class);
        SqlExecuteLogService logService = Mockito.mock(SqlExecuteLogService.class);
        SqlTaskTracker taskTracker = Mockito.mock(SqlTaskTracker.class);
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);

        when(configRepository.findByTenantId("TENANT_A")).thenReturn(Optional.empty());

        SmartSqlExecutor executor = new SmartSqlExecutor(
                agentService,
                new SqlGuardedQueryExecutor(rewriter, securityChecker, jdbcTemplate),
                new SqlRecoveryPolicy(new LingSqlProperties()),
                new SchemaBackCheckService(Mockito.mock(MetadataCacheService.class)),
                configRepository,
                logService,
                taskTracker
        );

        BizException ex = assertThrows(BizException.class, () -> executor.execute("TENANT_A", "查询今天订单数", "WEB"));

        assertEquals(ErrorCode.TENANT_DB_CONFIG_NOT_FOUND, ex.getErrorCode());
        assertEquals("租户数据源配置不存在，请先完成租户数据库连接配置", ex.getMessage());
    }

    @Test
    void executeShouldInjectSchemaBackCheckObservationIntoRetryPrompt() {
        SqlAgentService agentService = Mockito.mock(SqlAgentService.class);
        SqlSecurityChecker securityChecker = Mockito.mock(SqlSecurityChecker.class);
        SqlRewriter rewriter = Mockito.mock(SqlRewriter.class);
        TenantDbConfigRepository configRepository = Mockito.mock(TenantDbConfigRepository.class);
        SqlExecuteLogService logService = Mockito.mock(SqlExecuteLogService.class);
        SqlTaskTracker taskTracker = Mockito.mock(SqlTaskTracker.class);
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        MetadataCacheService metadataCacheService = Mockito.mock(MetadataCacheService.class);

        TenantDbConfig config = new TenantDbConfig();
        config.setTenantId("TENANT_A");
        when(configRepository.findByTenantId("TENANT_A")).thenReturn(Optional.of(config));
        when(metadataCacheService.getCachedSchema("TENANT_A")).thenReturn(List.of(deviceSchema()));
        when(agentService.generateSql(any(AgentExecutionContext.class), eq("查询设备"), eq(1)))
                .thenReturn(modelResult("SELECT device_nam FROM device"));
        when(agentService.generateSqlWithRetry(any(AgentExecutionContext.class), eq("查询设备"), anyString(), eq(2)))
                .thenReturn(modelResult("SELECT name FROM device"));
        when(rewriter.rewrite(anyString(), any(TenantDbConfig.class), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jdbcTemplate.queryForList("SELECT device_nam FROM device"))
                .thenThrow(new RuntimeException("Unknown column 'device_nam' in 'field list'"));
        when(jdbcTemplate.queryForList("SELECT name FROM device"))
                .thenReturn(List.of(Map.of("name", "A")));

        SmartSqlExecutor executor = new SmartSqlExecutor(
                agentService,
                new SqlGuardedQueryExecutor(rewriter, securityChecker, jdbcTemplate),
                new SqlRecoveryPolicy(new LingSqlProperties()),
                new SchemaBackCheckService(metadataCacheService),
                configRepository,
                logService,
                taskTracker
        );

        SqlExecutionResult result = executor.execute("TENANT_A", "查询设备", "WEB");

        assertTrue(result.isSuccess());
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentService).generateSqlWithRetry(
                any(AgentExecutionContext.class), eq("查询设备"), reasonCaptor.capture(), eq(2));
        assertTrue(reasonCaptor.getValue().contains("Schema 回查："));
        assertTrue(reasonCaptor.getValue().contains("表 device 的可用字段"));
        assertTrue(result.getTrace().stream().anyMatch(step -> "Schema 回查完成".equals(step.getTitle())));
    }

    private TableInfo deviceSchema() {
        TableInfo table = new TableInfo();
        table.setTableName("device");
        TableInfo.ColumnInfo id = new TableInfo.ColumnInfo();
        id.setColumnName("id");
        id.setPrimaryKey(true);
        TableInfo.ColumnInfo name = new TableInfo.ColumnInfo();
        name.setColumnName("name");
        table.setColumns(List.of(id, name));
        return table;
    }

    private ModelCallResult modelResult(String content) {
        return new ModelCallResult(
                content,
                "provider",
                "model",
                "v1",
                10,
                10L,
                "invocation-id",
                "billing-v1",
                1
        );
    }
}
