package com.lingsql.employee;

import com.lingsql.employee.entity.DigitalEmployee;
import com.lingsql.employee.entity.DigitalEmployeeRunLog;
import com.lingsql.employee.repository.DigitalEmployeeRepository;
import com.lingsql.employee.repository.DigitalEmployeeRunLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 失败记账的行为约束：失败必须落库、游标必须推进。
 *
 * <p>这两点决定了「失败可见」与「不再无限重试」，是上线检查单 C4/C5 的代码侧保证。</p>
 */
class DigitalEmployeeRunRecorderTest {

    @Test
    void shouldPersistFailureAndAdvanceCursor() {
        DigitalEmployeeRepository employeeRepository = Mockito.mock(DigitalEmployeeRepository.class);
        DigitalEmployeeRunLogRepository runLogRepository = Mockito.mock(DigitalEmployeeRunLogRepository.class);
        DigitalEmployeeRunRecorder recorder = new DigitalEmployeeRunRecorder(employeeRepository, runLogRepository);

        DigitalEmployee employee = new DigitalEmployee();
        employee.setId(7L);
        employee.setTenantId("demo_sales");
        employee.setDefaultQuestion("昨天销售额是多少？");
        employee.setRunCount(3L);
        Mockito.when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));
        Mockito.when(runLogRepository.save(Mockito.any(DigitalEmployeeRunLog.class))).thenAnswer(invocation -> {
            DigitalEmployeeRunLog log = invocation.getArgument(0);
            log.setId(99L);
            return log;
        });
        Mockito.when(employeeRepository.save(Mockito.any(DigitalEmployee.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime dueTime = LocalDateTime.of(2026, 10, 26, 8, 0, 0);
        Long runLogId = recorder.recordScheduledFailure(7L, dueTime, "租户数据源配置不存在");

        assertEquals(99L, runLogId);

        ArgumentCaptor<DigitalEmployeeRunLog> logCaptor = ArgumentCaptor.forClass(DigitalEmployeeRunLog.class);
        Mockito.verify(runLogRepository).save(logCaptor.capture());
        DigitalEmployeeRunLog saved = logCaptor.getValue();
        assertEquals(7L, saved.getEmployeeId());
        assertEquals("demo_sales", saved.getTenantId());
        assertEquals("SCHEDULED", saved.getTriggerSource());
        assertEquals(0, saved.getSuccess());
        assertEquals("租户数据源配置不存在", saved.getErrorMessage());
        assertEquals("昨天销售额是多少？", saved.getQuestion());

        assertEquals(dueTime, employee.getLastScheduleTime(), "游标必须推进，否则调度器会重复重试同一到期时间");
        assertEquals(4L, employee.getRunCount());
        assertEquals(0, employee.getLastRunStatus());
        Mockito.verify(employeeRepository).save(Mockito.same(employee));
    }

    @Test
    void shouldReturnNullWhenEmployeeMissing() {
        DigitalEmployeeRepository employeeRepository = Mockito.mock(DigitalEmployeeRepository.class);
        DigitalEmployeeRunLogRepository runLogRepository = Mockito.mock(DigitalEmployeeRunLogRepository.class);
        DigitalEmployeeRunRecorder recorder = new DigitalEmployeeRunRecorder(employeeRepository, runLogRepository);

        Mockito.when(employeeRepository.findById(404L)).thenReturn(Optional.empty());

        assertNull(recorder.recordScheduledFailure(404L, LocalDateTime.now(), "x"));
        Mockito.verify(runLogRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void shouldKeepNullErrorAsReadableMessage() {
        DigitalEmployeeRepository employeeRepository = Mockito.mock(DigitalEmployeeRepository.class);
        DigitalEmployeeRunLogRepository runLogRepository = Mockito.mock(DigitalEmployeeRunLogRepository.class);
        DigitalEmployeeRunRecorder recorder = new DigitalEmployeeRunRecorder(employeeRepository, runLogRepository);

        DigitalEmployee employee = new DigitalEmployee();
        employee.setId(8L);
        employee.setTenantId("demo_sales");
        Mockito.when(employeeRepository.findById(8L)).thenReturn(Optional.of(employee));
        Mockito.when(runLogRepository.save(Mockito.any(DigitalEmployeeRunLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(employeeRepository.save(Mockito.any(DigitalEmployee.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        recorder.recordScheduledFailure(8L, LocalDateTime.now(), null);

        ArgumentCaptor<DigitalEmployeeRunLog> logCaptor = ArgumentCaptor.forClass(DigitalEmployeeRunLog.class);
        Mockito.verify(runLogRepository).save(logCaptor.capture());
        assertSame("执行失败，未返回具体原因", logCaptor.getValue().getErrorMessage());
    }

    /**
     * 手动执行失败不能推进定时游标：否则后台点一次「立即运行」失败，
     * 就会把当天的定时任务“顶掉”，客户当天收不到日报。
     */
    @Test
    void shouldRecordManualFailureWithoutTouchingScheduleCursor() {
        DigitalEmployeeRepository employeeRepository = Mockito.mock(DigitalEmployeeRepository.class);
        DigitalEmployeeRunLogRepository runLogRepository = Mockito.mock(DigitalEmployeeRunLogRepository.class);
        DigitalEmployeeRunRecorder recorder = new DigitalEmployeeRunRecorder(employeeRepository, runLogRepository);

        DigitalEmployee employee = new DigitalEmployee();
        employee.setId(9L);
        employee.setTenantId("demo_sales");
        employee.setRunCount(1L);
        LocalDateTime scheduleCursor = LocalDateTime.of(2026, 10, 25, 8, 0, 0);
        employee.setLastScheduleTime(scheduleCursor);
        Mockito.when(employeeRepository.findById(9L)).thenReturn(Optional.of(employee));
        Mockito.when(runLogRepository.save(Mockito.any(DigitalEmployeeRunLog.class))).thenAnswer(invocation -> {
            DigitalEmployeeRunLog log = invocation.getArgument(0);
            log.setId(100L);
            return log;
        });
        Mockito.when(employeeRepository.save(Mockito.any(DigitalEmployee.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        recorder.recordManualFailure(9L, "手动触发失败");

        ArgumentCaptor<DigitalEmployeeRunLog> logCaptor = ArgumentCaptor.forClass(DigitalEmployeeRunLog.class);
        Mockito.verify(runLogRepository).save(logCaptor.capture());
        assertEquals("MANUAL", logCaptor.getValue().getTriggerSource());
        assertEquals(0, logCaptor.getValue().getSuccess());
        assertEquals(scheduleCursor, employee.getLastScheduleTime(), "手动执行不得改动定时游标");
        assertEquals(2L, employee.getRunCount());
    }
}
