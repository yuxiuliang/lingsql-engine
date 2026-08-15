package com.lingsql.employee;

import com.lingsql.common.pagination.PageParamResolver;
import com.lingsql.employee.entity.DigitalEmployee;
import com.lingsql.employee.entity.DigitalEmployeeRunLog;
import com.lingsql.employee.model.DigitalEmployeeCreateRequest;
import com.lingsql.employee.model.DigitalEmployeeRunRequest;
import com.lingsql.employee.model.DigitalEmployeeRunResult;
import com.lingsql.employee.repository.DigitalEmployeeRepository;
import com.lingsql.employee.repository.DigitalEmployeeRunLogRepository;
import com.lingsql.executor.ResultRefiner;
import com.lingsql.executor.SmartSqlExecutor;
import com.lingsql.executor.SqlExecutionResult;
import com.lingsql.ops.OpsAlertService;
import com.lingsql.ops.model.AlertDispatchChannelResult;
import com.lingsql.ops.model.AlertDispatchResult;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class DigitalEmployeeServiceTest {

    @Test
    void shouldGenerateEmployeeCodeWhenCreateRequestCodeIsNull() {
        DigitalEmployeeRepository employeeRepository = Mockito.mock(DigitalEmployeeRepository.class);
        DigitalEmployeeRunLogRepository runLogRepository = Mockito.mock(DigitalEmployeeRunLogRepository.class);
        SmartSqlExecutor smartSqlExecutor = Mockito.mock(SmartSqlExecutor.class);
        ResultRefiner resultRefiner = Mockito.mock(ResultRefiner.class);
        PageParamResolver pageParamResolver = Mockito.mock(PageParamResolver.class);
        OpsAlertService opsAlertService = Mockito.mock(OpsAlertService.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);

        when(employeeRepository.existsByTenantIdAndEmployeeCodeAndIsDeleted(anyString(), anyString(), eq(0))).thenReturn(false);
        when(employeeRepository.save(any(DigitalEmployee.class))).thenAnswer(invocation -> {
            DigitalEmployee employee = invocation.getArgument(0);
            employee.setId(1L);
            return employee;
        });

        DigitalEmployeeService service = new DigitalEmployeeService(
                employeeRepository,
                runLogRepository,
                smartSqlExecutor,
                resultRefiner,
                pageParamResolver,
                opsAlertService,
                tenantRepository
        );
        DigitalEmployeeCreateRequest request = new DigitalEmployeeCreateRequest();
        request.setTenantId("TENANT_A");
        request.setEmployeeName("运营助理");
        request.setDefaultQuestion("统计昨日订单金额");
        request.setStatus(null);

        var view = service.createEmployee(request);
        assertNotNull(view);
        assertTrue(view.getEmployeeCode().startsWith("EMP"));
        assertEquals(1, view.getStatus());
        assertEquals(0L, view.getRunCount());
        assertEquals(0L, view.getSuccessCount());
        assertEquals(0, view.getScheduleEnabled());
        assertEquals(0, view.getAlertEnabled());
        assertEquals(0, view.getPushEnabled());
        assertTrue(view.getPushChannels() == null || view.getPushChannels().isEmpty());
        assertNull(view.getPushTitleTemplate());
        assertNull(view.getPushMessageTemplate());
    }

    @Test
    void shouldRunEmployeeSuccessfullyAndUpdateStats() {
        DigitalEmployeeRepository employeeRepository = Mockito.mock(DigitalEmployeeRepository.class);
        DigitalEmployeeRunLogRepository runLogRepository = Mockito.mock(DigitalEmployeeRunLogRepository.class);
        SmartSqlExecutor smartSqlExecutor = Mockito.mock(SmartSqlExecutor.class);
        ResultRefiner resultRefiner = Mockito.mock(ResultRefiner.class);
        PageParamResolver pageParamResolver = Mockito.mock(PageParamResolver.class);
        OpsAlertService opsAlertService = Mockito.mock(OpsAlertService.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);

        DigitalEmployee employee = new DigitalEmployee();
        employee.setId(7L);
        employee.setTenantId("TENANT_A");
        employee.setEmployeeCode("EMP001");
        employee.setEmployeeName("销售分析师");
        employee.setDefaultQuestion("统计昨日销售额");
        employee.setStatus(1);
        employee.setRunCount(0L);
        employee.setSuccessCount(0L);
        employee.setIsDeleted(0);

        when(employeeRepository.findByIdAndIsDeleted(eq(7L), eq(0))).thenReturn(Optional.of(employee));
        when(smartSqlExecutor.execute(eq("TENANT_A"), eq("统计昨日销售额"), eq("DIGITAL_EMPLOYEE")))
                .thenReturn(SqlExecutionResult.success("SELECT SUM(amount) AS total FROM orders LIMIT 100", List.of(Map.of("total", 1024))));
        when(resultRefiner.refine(anyString(), any(SqlExecutionResult.class))).thenReturn("昨日销售额为1024");
        when(runLogRepository.save(any(DigitalEmployeeRunLog.class))).thenAnswer(invocation -> {
            DigitalEmployeeRunLog log = invocation.getArgument(0);
            log.setId(99L);
            return log;
        });
        when(employeeRepository.save(any(DigitalEmployee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DigitalEmployeeService service = new DigitalEmployeeService(
                employeeRepository,
                runLogRepository,
                smartSqlExecutor,
                resultRefiner,
                pageParamResolver,
                opsAlertService,
                tenantRepository
        );

        DigitalEmployeeRunResult result = service.run(7L, new DigitalEmployeeRunRequest());
        assertNotNull(result);
        assertTrue(Boolean.TRUE.equals(result.getSuccess()));
        assertEquals(99L, result.getRunLogId());
        assertEquals("昨日销售额为1024", result.getAnswer());
        assertEquals("统计昨日销售额", result.getQuestion());
        assertNotNull(result.getExecuteTime());

        ArgumentCaptor<DigitalEmployee> employeeCaptor = ArgumentCaptor.forClass(DigitalEmployee.class);
        Mockito.verify(employeeRepository, Mockito.atLeastOnce()).save(employeeCaptor.capture());
        DigitalEmployee updated = employeeCaptor.getValue();
        assertEquals(1L, updated.getRunCount());
        assertEquals(1L, updated.getSuccessCount());
        assertEquals(1, updated.getLastRunStatus());
        assertNull(updated.getLastErrorMessage());
    }

    @Test
    void shouldMaskFailureMessageWhenRunEmployeeFailed() {
        DigitalEmployeeRepository employeeRepository = Mockito.mock(DigitalEmployeeRepository.class);
        DigitalEmployeeRunLogRepository runLogRepository = Mockito.mock(DigitalEmployeeRunLogRepository.class);
        SmartSqlExecutor smartSqlExecutor = Mockito.mock(SmartSqlExecutor.class);
        ResultRefiner resultRefiner = Mockito.mock(ResultRefiner.class);
        PageParamResolver pageParamResolver = Mockito.mock(PageParamResolver.class);
        OpsAlertService opsAlertService = Mockito.mock(OpsAlertService.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);

        DigitalEmployee employee = new DigitalEmployee();
        employee.setId(8L);
        employee.setTenantId("TENANT_B");
        employee.setEmployeeCode("EMP002");
        employee.setEmployeeName("经营复盘助理");
        employee.setDefaultQuestion("统计近7天利润");
        employee.setStatus(1);
        employee.setRunCount(3L);
        employee.setSuccessCount(2L);
        employee.setIsDeleted(0);

        when(employeeRepository.findByIdAndIsDeleted(eq(8L), eq(0))).thenReturn(Optional.of(employee));
        when(smartSqlExecutor.execute(eq("TENANT_B"), eq("统计近7天利润"), eq("DIGITAL_EMPLOYEE")))
                .thenThrow(new RuntimeException("JDBC timeout"));
        when(resultRefiner.refine(anyString(), any(SqlExecutionResult.class))).thenReturn("执行失败，请稍后重试");
        when(runLogRepository.save(any(DigitalEmployeeRunLog.class))).thenAnswer(invocation -> {
            DigitalEmployeeRunLog log = invocation.getArgument(0);
            log.setId(100L);
            return log;
        });
        when(employeeRepository.save(any(DigitalEmployee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DigitalEmployeeService service = new DigitalEmployeeService(
                employeeRepository,
                runLogRepository,
                smartSqlExecutor,
                resultRefiner,
                pageParamResolver,
                opsAlertService,
                tenantRepository
        );

        DigitalEmployeeRunResult result = service.run(8L, new DigitalEmployeeRunRequest());
        assertNotNull(result);
        assertFalse(Boolean.TRUE.equals(result.getSuccess()));
        assertEquals("数字员工执行失败，请检查任务配置", result.getErrorMessage());

        ArgumentCaptor<DigitalEmployee> employeeCaptor = ArgumentCaptor.forClass(DigitalEmployee.class);
        Mockito.verify(employeeRepository, Mockito.atLeastOnce()).save(employeeCaptor.capture());
        DigitalEmployee updated = employeeCaptor.getValue();
        assertEquals(4L, updated.getRunCount());
        assertEquals(2L, updated.getSuccessCount());
        assertEquals(0, updated.getLastRunStatus());
        assertEquals("数字员工执行失败，请检查任务配置", updated.getLastErrorMessage());
    }

    @Test
    void shouldSendAlertWhenScheduledRunHitConfiguredRule() {
        DigitalEmployeeRepository employeeRepository = Mockito.mock(DigitalEmployeeRepository.class);
        DigitalEmployeeRunLogRepository runLogRepository = Mockito.mock(DigitalEmployeeRunLogRepository.class);
        SmartSqlExecutor smartSqlExecutor = Mockito.mock(SmartSqlExecutor.class);
        ResultRefiner resultRefiner = Mockito.mock(ResultRefiner.class);
        PageParamResolver pageParamResolver = Mockito.mock(PageParamResolver.class);
        OpsAlertService opsAlertService = Mockito.mock(OpsAlertService.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);

        DigitalEmployee employee = new DigitalEmployee();
        employee.setId(9L);
        employee.setTenantId("TENANT_C");
        employee.setEmployeeCode("EMP003");
        employee.setEmployeeName("异常巡检助手");
        employee.setScenario("异常清单");
        employee.setDefaultQuestion("筛选异常订单");
        employee.setStatus(1);
        employee.setScheduleEnabled(1);
        employee.setScheduleCron("0 0/30 * * * ?");
        employee.setAlertEnabled(1);
        employee.setAlertRuleType("RESULT_ROWS_GT");
        employee.setAlertThreshold(0);
        employee.setRunCount(0L);
        employee.setSuccessCount(0L);
        employee.setIsDeleted(0);

        when(employeeRepository.findByIdAndIsDeleted(eq(9L), eq(0))).thenReturn(Optional.of(employee));
        when(smartSqlExecutor.execute(eq("TENANT_C"), eq("筛选异常订单"), eq("DIGITAL_EMPLOYEE")))
                .thenReturn(SqlExecutionResult.success("SELECT * FROM t_order WHERE abnormal_flag = 1 LIMIT 100", List.of(Map.of("order_id", 1))));
        when(resultRefiner.refine(anyString(), any(SqlExecutionResult.class))).thenReturn("发现 1 条异常订单");
        when(runLogRepository.save(any(DigitalEmployeeRunLog.class))).thenAnswer(invocation -> {
            DigitalEmployeeRunLog log = invocation.getArgument(0);
            log.setId(101L);
            return log;
        });
        when(employeeRepository.save(any(DigitalEmployee.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(opsAlertService.sendAlertWithResult(anyString(), anyString(), anyMap()))
                .thenReturn(AlertDispatchResult.completed(true, List.of(
                        AlertDispatchChannelResult.success("webhook", 200)
                )));

        DigitalEmployeeService service = new DigitalEmployeeService(
                employeeRepository,
                runLogRepository,
                smartSqlExecutor,
                resultRefiner,
                pageParamResolver,
                opsAlertService,
                tenantRepository
        );

        DigitalEmployeeRunResult result = service.runScheduled(9L, java.time.LocalDateTime.of(2026, 4, 14, 9, 0, 0));

        assertNotNull(result);
        assertTrue(Boolean.TRUE.equals(result.getSuccess()));
        Mockito.verify(opsAlertService).sendAlertWithResult(eq("数字员工订阅告警"), anyString(), anyMap());
        ArgumentCaptor<DigitalEmployeeRunLog> runLogCaptor = ArgumentCaptor.forClass(DigitalEmployeeRunLog.class);
        Mockito.verify(runLogRepository).save(runLogCaptor.capture());
        assertEquals(1, runLogCaptor.getValue().getAlertDeliveryStatus());
        assertTrue(runLogCaptor.getValue().getAlertDeliveryDetail().contains("Webhook=成功"));
    }

    @Test
    void shouldSendPushWhenScheduledRunSucceededAndPushEnabled() {
        DigitalEmployeeRepository employeeRepository = Mockito.mock(DigitalEmployeeRepository.class);
        DigitalEmployeeRunLogRepository runLogRepository = Mockito.mock(DigitalEmployeeRunLogRepository.class);
        SmartSqlExecutor smartSqlExecutor = Mockito.mock(SmartSqlExecutor.class);
        ResultRefiner resultRefiner = Mockito.mock(ResultRefiner.class);
        PageParamResolver pageParamResolver = Mockito.mock(PageParamResolver.class);
        OpsAlertService opsAlertService = Mockito.mock(OpsAlertService.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);

        DigitalEmployee employee = new DigitalEmployee();
        employee.setId(10L);
        employee.setTenantId("TENANT_D");
        employee.setEmployeeCode("EMP004");
        employee.setEmployeeName("日报推送助手");
        employee.setScenario("经营日报");
        employee.setDefaultQuestion("统计昨日销售额");
        employee.setStatus(1);
        employee.setScheduleEnabled(1);
        employee.setScheduleCron("0 0 8 * * ?");
        employee.setPushEnabled(1);
        employee.setPushChannels("dingtalk,wecom");
        employee.setPushMaxRows(2);
        employee.setPushTitleTemplate("[{scenario}] {employeeName} 完成，结果 {resultRows} 行");
        employee.setPushMessageTemplate("时间 {executeTime}，问题 {question}，摘要 {answer}");
        employee.setRunCount(0L);
        employee.setSuccessCount(0L);
        employee.setIsDeleted(0);

        when(employeeRepository.findByIdAndIsDeleted(eq(10L), eq(0))).thenReturn(Optional.of(employee));
        when(smartSqlExecutor.execute(eq("TENANT_D"), eq("统计昨日销售额"), eq("DIGITAL_EMPLOYEE")))
                .thenReturn(SqlExecutionResult.success(
                        "SELECT region, amount FROM t_order LIMIT 100",
                        List.of(Map.of("region", "华东", "amount", 1200), Map.of("region", "华南", "amount", 980))
                ));
        when(resultRefiner.refine(anyString(), any(SqlExecutionResult.class))).thenReturn("昨日销售额已汇总");
        when(runLogRepository.save(any(DigitalEmployeeRunLog.class))).thenAnswer(invocation -> {
            DigitalEmployeeRunLog log = invocation.getArgument(0);
            log.setId(102L);
            return log;
        });
        when(employeeRepository.save(any(DigitalEmployee.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(opsAlertService.sendAlertToChannelsWithResult(anyString(), anyString(), anyMap(), anyList()))
                .thenReturn(AlertDispatchResult.completed(true, List.of(
                        AlertDispatchChannelResult.success("dingtalk", 200),
                        AlertDispatchChannelResult.success("wecom", 200)
                )));

        DigitalEmployeeService service = new DigitalEmployeeService(
                employeeRepository,
                runLogRepository,
                smartSqlExecutor,
                resultRefiner,
                pageParamResolver,
                opsAlertService,
                tenantRepository
        );

        DigitalEmployeeRunResult result = service.runScheduled(10L, java.time.LocalDateTime.of(2026, 4, 14, 8, 0, 0));

        assertNotNull(result);
        assertTrue(Boolean.TRUE.equals(result.getSuccess()));
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<String>> channelsCaptor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(opsAlertService).sendAlertToChannelsWithResult(titleCaptor.capture(), messageCaptor.capture(), detailsCaptor.capture(), channelsCaptor.capture());
        assertTrue(titleCaptor.getValue().contains("[经营日报] 日报推送助手 完成"));
        assertTrue(messageCaptor.getValue().contains("问题 统计昨日销售额"));
        assertEquals(List.of("dingtalk", "wecom"), channelsCaptor.getValue());
        @SuppressWarnings("unchecked")
        List<String> previewRows = (List<String>) detailsCaptor.getValue().get("previewRows");
        assertNotNull(previewRows);
        assertEquals(2, previewRows.size());
        assertTrue(previewRows.get(0).contains("region=华东"));
        assertEquals(List.of("dingtalk", "wecom"), detailsCaptor.getValue().get("pushChannels"));
        ArgumentCaptor<DigitalEmployeeRunLog> runLogCaptor = ArgumentCaptor.forClass(DigitalEmployeeRunLog.class);
        Mockito.verify(runLogRepository).save(runLogCaptor.capture());
        assertEquals(1, runLogCaptor.getValue().getPushDeliveryStatus());
        assertTrue(runLogCaptor.getValue().getPushDeliveryDetail().contains("钉钉=成功"));
    }

    @Test
    void shouldFallbackToTenantDefaultChannelsWhenEmployeeChannelsEmpty() {
        DigitalEmployeeRepository employeeRepository = Mockito.mock(DigitalEmployeeRepository.class);
        DigitalEmployeeRunLogRepository runLogRepository = Mockito.mock(DigitalEmployeeRunLogRepository.class);
        SmartSqlExecutor smartSqlExecutor = Mockito.mock(SmartSqlExecutor.class);
        ResultRefiner resultRefiner = Mockito.mock(ResultRefiner.class);
        PageParamResolver pageParamResolver = Mockito.mock(PageParamResolver.class);
        OpsAlertService opsAlertService = Mockito.mock(OpsAlertService.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);

        DigitalEmployee employee = new DigitalEmployee();
        employee.setId(11L);
        employee.setTenantId("TENANT_E");
        employee.setEmployeeCode("EMP005");
        employee.setEmployeeName("租户默认通道助手");
        employee.setScenario("库存日报");
        employee.setDefaultQuestion("统计昨日库存");
        employee.setStatus(1);
        employee.setScheduleEnabled(1);
        employee.setScheduleCron("0 0 8 * * ?");
        employee.setPushEnabled(1);
        employee.setPushChannels(null);
        employee.setPushMaxRows(1);
        employee.setIsDeleted(0);

        TenantInfo tenant = new TenantInfo();
        tenant.setTenantId("TENANT_E");
        tenant.setNotificationChannels("webhook");

        when(employeeRepository.findByIdAndIsDeleted(eq(11L), eq(0))).thenReturn(Optional.of(employee));
        when(tenantRepository.findByTenantIdAndIsDeleted(eq("TENANT_E"), eq(0))).thenReturn(Optional.of(tenant));
        when(smartSqlExecutor.execute(eq("TENANT_E"), eq("统计昨日库存"), eq("DIGITAL_EMPLOYEE")))
                .thenReturn(SqlExecutionResult.success("SELECT sku, qty FROM t_stock LIMIT 100", List.of(Map.of("sku", "SKU-1", "qty", 18))));
        when(resultRefiner.refine(anyString(), any(SqlExecutionResult.class))).thenReturn("库存结果已生成");
        when(runLogRepository.save(any(DigitalEmployeeRunLog.class))).thenAnswer(invocation -> {
            DigitalEmployeeRunLog log = invocation.getArgument(0);
            log.setId(103L);
            return log;
        });
        when(employeeRepository.save(any(DigitalEmployee.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(opsAlertService.sendAlertToChannelsWithResult(anyString(), anyString(), anyMap(), anyList()))
                .thenReturn(AlertDispatchResult.completed(true, List.of(
                        AlertDispatchChannelResult.success("webhook", 200)
                )));

        DigitalEmployeeService service = new DigitalEmployeeService(
                employeeRepository,
                runLogRepository,
                smartSqlExecutor,
                resultRefiner,
                pageParamResolver,
                opsAlertService,
                tenantRepository
        );

        service.runScheduled(11L, java.time.LocalDateTime.of(2026, 4, 15, 8, 0, 0));

        ArgumentCaptor<List<String>> channelsCaptor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(opsAlertService).sendAlertToChannelsWithResult(anyString(), anyString(), anyMap(), channelsCaptor.capture());
        assertEquals(List.of("webhook"), channelsCaptor.getValue());
    }
}
