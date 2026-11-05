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
    private final DigitalEmployeeRunRecorder runRecorder;

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
                // 失败必须落到独立事务里：runScheduled 的事务此时已 rollback-only，
                // 日志与游标都会随之丢弃，调度器会每 15 秒重试同一个到期时间且不留任何记录。
                recordFailureQuietly(employee, dueTime, ex);
            }
        }
    }

    /**
     * 记录失败，绝不让记账本身再次打断调度循环。
     *
     * @param employee  员工
     * @param dueTime   本次计划触发时间
     * @param cause     原始异常
     * @author 余炘洋
     * @date 2026-10-26
     */
    private void recordFailureQuietly(DigitalEmployee employee, LocalDateTime dueTime, Exception cause) {
        try {
            Long runLogId = runRecorder.recordScheduledFailure(employee.getId(), dueTime, cause.getMessage());
            log.info("数字员工失败已记账: employeeId={}, runLogId={}", employee.getId(), runLogId);
        } catch (Exception recordEx) {
            log.error("数字员工失败记账失败: employeeId={}", employee.getId(), recordEx);
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
