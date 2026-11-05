package com.lingsql.employee;

import com.lingsql.employee.entity.DigitalEmployee;
import com.lingsql.employee.repository.DigitalEmployeeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

class DigitalEmployeeSchedulerTest {

    @Test
    void shouldDispatchDueScheduledEmployee() {
        DigitalEmployeeRepository repository = Mockito.mock(DigitalEmployeeRepository.class);
        DigitalEmployeeService service = Mockito.mock(DigitalEmployeeService.class);
        DigitalEmployeeRunRecorder recorder = Mockito.mock(DigitalEmployeeRunRecorder.class);
        DigitalEmployeeScheduler scheduler = new DigitalEmployeeScheduler(repository, service, recorder);

        DigitalEmployee employee = new DigitalEmployee();
        employee.setId(1L);
        employee.setEmployeeName("日报助手");
        employee.setScheduleCron("0 0 9 * * ?");
        employee.setCreateTime(LocalDateTime.of(2026, 4, 13, 8, 0, 0));
        employee.setLastScheduleTime(LocalDateTime.of(2026, 4, 13, 9, 0, 0));

        Mockito.when(repository.findByIsDeletedAndStatusAndScheduleEnabled(0, 1, 1))
                .thenReturn(List.of(employee));

        scheduler.dispatchDueEmployees(LocalDateTime.of(2026, 4, 14, 9, 0, 5));

        Mockito.verify(service).runScheduled(1L, LocalDateTime.of(2026, 4, 14, 9, 0, 0));
    }

    @Test
    void shouldSkipEmployeeWhenNextExecutionIsNotDue() {
        DigitalEmployeeRepository repository = Mockito.mock(DigitalEmployeeRepository.class);
        DigitalEmployeeService service = Mockito.mock(DigitalEmployeeService.class);
        DigitalEmployeeRunRecorder recorder = Mockito.mock(DigitalEmployeeRunRecorder.class);
        DigitalEmployeeScheduler scheduler = new DigitalEmployeeScheduler(repository, service, recorder);

        DigitalEmployee employee = new DigitalEmployee();
        employee.setId(2L);
        employee.setEmployeeName("日报助手");
        employee.setScheduleCron("0 0 9 * * ?");
        employee.setCreateTime(LocalDateTime.of(2026, 4, 14, 8, 0, 0));
        employee.setLastScheduleTime(LocalDateTime.of(2026, 4, 14, 9, 0, 0));

        Mockito.when(repository.findByIsDeletedAndStatusAndScheduleEnabled(0, 1, 1))
                .thenReturn(List.of(employee));

        scheduler.dispatchDueEmployees(LocalDateTime.of(2026, 4, 14, 9, 30, 0));

        Mockito.verify(service, Mockito.never()).runScheduled(Mockito.anyLong(), Mockito.any());
    }

    /**
     * 执行抛异常时，必须把失败写进独立事务，否则 run log 与游标会随外层回滚一起消失，
     * 调度器会每 15 秒重试同一个到期时间且不留痕迹（对应上线检查单 C4/C5）。
     */
    @Test
    void shouldRecordFailureWhenScheduledRunThrows() {
        DigitalEmployeeRepository repository = Mockito.mock(DigitalEmployeeRepository.class);
        DigitalEmployeeService service = Mockito.mock(DigitalEmployeeService.class);
        DigitalEmployeeRunRecorder recorder = Mockito.mock(DigitalEmployeeRunRecorder.class);
        DigitalEmployeeScheduler scheduler = new DigitalEmployeeScheduler(repository, service, recorder);

        DigitalEmployee employee = new DigitalEmployee();
        employee.setId(3L);
        employee.setEmployeeName("日报助手");
        employee.setScheduleCron("0 0 9 * * ?");
        employee.setCreateTime(LocalDateTime.of(2026, 4, 13, 8, 0, 0));
        employee.setLastScheduleTime(LocalDateTime.of(2026, 4, 13, 9, 0, 0));

        Mockito.when(repository.findByIsDeletedAndStatusAndScheduleEnabled(0, 1, 1))
                .thenReturn(List.of(employee));
        LocalDateTime dueTime = LocalDateTime.of(2026, 4, 14, 9, 0, 0);
        Mockito.doThrow(new IllegalStateException("租户数据源配置不存在"))
                .when(service).runScheduled(3L, dueTime);

        scheduler.dispatchDueEmployees(LocalDateTime.of(2026, 4, 14, 9, 0, 5));

        Mockito.verify(recorder).recordScheduledFailure(3L, dueTime, "租户数据源配置不存在");
    }
}
