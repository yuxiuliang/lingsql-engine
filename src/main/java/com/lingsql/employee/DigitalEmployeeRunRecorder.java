package com.lingsql.employee;

import com.lingsql.employee.entity.DigitalEmployee;
import com.lingsql.employee.entity.DigitalEmployeeRunLog;
import com.lingsql.employee.repository.DigitalEmployeeRepository;
import com.lingsql.employee.repository.DigitalEmployeeRunLogRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数字员工定时执行失败记账。
 *
 * <p>存在的理由：{@link DigitalEmployeeService#runScheduled} 是 {@code @Transactional} 方法，
 * 内层调用抛异常后即使被捕获，外层事务也会被标记为 {@code rollback-only}，提交时抛
 * {@code UnexpectedRollbackException}——结果是<b>运行日志没写、游标没推进</b>：
 * 数据库里看不到任何失败记录，而调度器每 15 秒重试同一个到期时间，无限循环且无声无息。</p>
 *
 * <p>因此失败记账必须走独立事务（{@code REQUIRES_NEW}）：外层回滚不影响这里的提交，
 * 失败得以落地、游标得以推进，调度器进入下一次 cron 周期而不是无休止重试。
 * 代价是失败不再自动重试，需要人工介入——这正是「失败必须可见」的取舍。</p>
 *
 * @author 余炘洋
 * @date 2026-10-26
 */
@Component
public class DigitalEmployeeRunRecorder {

    private static final String TRIGGER_SOURCE_SCHEDULED = "SCHEDULED";
    private static final String TRIGGER_SOURCE_MANUAL = "MANUAL";
    private static final int FAILURE_STATUS = 0;
    private static final int MAX_ERROR_LENGTH = 1000;

    private final DigitalEmployeeRepository employeeRepository;
    private final DigitalEmployeeRunLogRepository runLogRepository;

    public DigitalEmployeeRunRecorder(DigitalEmployeeRepository employeeRepository,
                                      DigitalEmployeeRunLogRepository runLogRepository) {
        this.employeeRepository = employeeRepository;
        this.runLogRepository = runLogRepository;
    }

    /**
     * 记录一次定时执行失败，并推进游标。
     *
     * <p>独立事务：即使调用方事务已回滚，本方法的写入仍然提交。</p>
     *
     * @param employeeId          员工ID
     * @param expectedScheduleTime 本次计划触发时间；推进游标后调度器才不会重复重试
     * @param errorMessage        失败原因
     * @return 新增运行日志ID；员工不存在时返回 null
     * @author 余炘洋
     * @date 2026-10-26
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long recordScheduledFailure(Long employeeId, LocalDateTime expectedScheduleTime, String errorMessage) {
        return record(employeeId, TRIGGER_SOURCE_SCHEDULED, expectedScheduleTime, errorMessage);
    }

    /**
     * 记录一次手动执行失败。
     *
     * <p>手动执行不影响定时游标，因此不推进 {@code lastScheduleTime}——否则一次后台点错
     * 就会把当天的定时任务“顶掉”。</p>
     *
     * @param employeeId   员工ID
     * @param errorMessage 失败原因
     * @return 新增运行日志ID；员工不存在时返回 null
     * @author 余炘洋
     * @date 2026-10-26
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long recordManualFailure(Long employeeId, String errorMessage) {
        return record(employeeId, TRIGGER_SOURCE_MANUAL, null, errorMessage);
    }

    private Long record(Long employeeId, String triggerSource, LocalDateTime expectedScheduleTime, String errorMessage) {
        if (employeeId == null) {
            return null;
        }
        DigitalEmployee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        String safeMessage = truncate(errorMessage);

        DigitalEmployeeRunLog runLog = new DigitalEmployeeRunLog();
        runLog.setEmployeeId(employeeId);
        runLog.setTenantId(employee.getTenantId());
        runLog.setTriggerSource(triggerSource);
        runLog.setQuestion(employee.getDefaultQuestion());
        runLog.setSuccess(FAILURE_STATUS);
        runLog.setErrorMessage(safeMessage);
        runLog.setResultRows(0);
        runLog.setDurationMs(0L);
        runLog.setCreateTime(now);
        Long runLogId = runLogRepository.save(runLog).getId();

        long runCount = employee.getRunCount() == null ? 0L : employee.getRunCount();
        employee.setRunCount(runCount + 1);
        employee.setLastRunStatus(FAILURE_STATUS);
        employee.setLastRunTime(now);
        employee.setLastErrorMessage(safeMessage);
        if (expectedScheduleTime != null) {
            employee.setLastScheduleTime(expectedScheduleTime);
        }
        employee.setUpdateTime(now);
        employeeRepository.save(employee);
        return runLogId;
    }

    private String truncate(String message) {
        if (message == null) {
            return "执行失败，未返回具体原因";
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
