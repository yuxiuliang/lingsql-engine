package com.lingsql.employee;

import com.lingsql.employee.entity.DigitalEmployee;
import com.lingsql.employee.repository.DigitalEmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数字员工定时调度器。
 *
 * @author 余炘洋
 * @date 2026-04-14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DigitalEmployeeScheduler {

    private static final Integer ACTIVE_STATUS = 1;
    private static final Integer NOT_DELETED = 0;
    private static final int MAX_CATCH_UP_ITERATIONS = 512;

    private final DigitalEmployeeRepository digitalEmployeeRepository;
    private final DigitalEmployeeService digitalEmployeeService;

    /**
     * 定时扫描需要执行的数字员工。
     *
     * @author 余炘洋
     * @date 2026-04-14
     */
    @Scheduled(fixedDelayString = "${lingsql.digital-employee.dispatch-interval-ms:15000}")
    public void dispatch() {
        dispatchDueEmployees(LocalDateTime.now().withNano(0));
    }

    void dispatchDueEmployees(LocalDateTime now) {
        List<DigitalEmployee> employees = digitalEmployeeRepository.findByIsDeletedAndStatusAndScheduleEnabled(
                NOT_DELETED,
                ACTIVE_STATUS,
                ACTIVE_STATUS
        );
        for (DigitalEmployee employee : employees) {
            LocalDateTime dueTime = resolveLatestDueTime(employee, now);
            if (dueTime == null) {
                continue;
            }
            try {
                digitalEmployeeService.runScheduled(employee.getId(), dueTime);
            } catch (Exception ex) {
                log.warn("数字员工定时执行失败: employeeId={}, employeeName={}, message={}",
                        employee.getId(), employee.getEmployeeName(), ex.getMessage());
            }
        }
    }

    private LocalDateTime resolveLatestDueTime(DigitalEmployee employee, LocalDateTime now) {
        if (employee == null || !StringUtils.hasText(employee.getScheduleCron())) {
            return null;
        }
        CronExpression cronExpression;
        try {
            cronExpression = CronExpression.parse(employee.getScheduleCron().trim());
        } catch (IllegalArgumentException ex) {
            log.warn("数字员工 Cron 配置非法，已跳过: employeeId={}, cron={}", employee.getId(), employee.getScheduleCron());
            return null;
        }
        LocalDateTime cursor = employee.getLastScheduleTime();
        if (cursor == null) {
            cursor = employee.getCreateTime();
        }
        if (cursor == null) {
            cursor = now.minusMinutes(1);
        }
        LocalDateTime dueTime = null;
        int iterations = 0;
        while (iterations++ < MAX_CATCH_UP_ITERATIONS) {
            LocalDateTime next = cronExpression.next(cursor);
            if (next == null || next.isAfter(now)) {
                return dueTime;
            }
            dueTime = next;
            cursor = next;
        }
        return dueTime;
    }
}
