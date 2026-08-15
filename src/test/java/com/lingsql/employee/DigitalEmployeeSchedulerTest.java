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
        DigitalEmployeeScheduler scheduler = new DigitalEmployeeScheduler(repository, service);

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
        DigitalEmployeeScheduler scheduler = new DigitalEmployeeScheduler(repository, service);

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
}
