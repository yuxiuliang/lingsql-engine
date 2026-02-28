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
                pageParamResolver
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
    }

    @Test
    void shouldRunEmployeeSuccessfullyAndUpdateStats() {
        DigitalEmployeeRepository employeeRepository = Mockito.mock(DigitalEmployeeRepository.class);
        DigitalEmployeeRunLogRepository runLogRepository = Mockito.mock(DigitalEmployeeRunLogRepository.class);
        SmartSqlExecutor smartSqlExecutor = Mockito.mock(SmartSqlExecutor.class);
        ResultRefiner resultRefiner = Mockito.mock(ResultRefiner.class);
        PageParamResolver pageParamResolver = Mockito.mock(PageParamResolver.class);

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
                pageParamResolver
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
                pageParamResolver
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
}
