package com.lingsql.employee;

import com.lingsql.billing.invocation.PhysicalBillingException;
import com.lingsql.agent.orchestration.AgentCallerType;
import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.agent.orchestration.AgentRunControl;
import com.lingsql.agent.orchestration.AgentRunPolicy;
import com.lingsql.agent.orchestration.SqlQueryTask;
import com.lingsql.agent.plugin.AgentPluginException;
import com.lingsql.agent.plugin.AgentPluginExecutor;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.common.pagination.PageParam;
import com.lingsql.common.pagination.PageParamResolver;
import com.lingsql.common.result.PageResult;
import com.lingsql.common.trace.RequestTraceContext;
import com.lingsql.employee.entity.DigitalEmployee;
import com.lingsql.employee.entity.DigitalEmployeeRunLog;
import com.lingsql.employee.model.DigitalEmployeeCreateRequest;
import com.lingsql.employee.model.DigitalEmployeeOverviewView;
import com.lingsql.employee.model.DigitalEmployeeRunLogQuery;
import com.lingsql.employee.model.DigitalEmployeeRunLogView;
import com.lingsql.employee.model.DigitalEmployeeRunRequest;
import com.lingsql.employee.model.DigitalEmployeeRunResult;
import com.lingsql.employee.model.DigitalEmployeeTopEmployeeView;
import com.lingsql.employee.model.DigitalEmployeeUpdateRequest;
import com.lingsql.employee.model.DigitalEmployeeView;
import com.lingsql.employee.repository.DigitalEmployeeRepository;
import com.lingsql.employee.repository.DigitalEmployeeRunLogRepository;
import com.lingsql.executor.ResultRefiner;
import com.lingsql.executor.SmartSqlExecutor;
import com.lingsql.executor.SqlExecutionResult;
import com.lingsql.ops.OpsAlertService;
import com.lingsql.ops.model.AlertDispatchResult;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 数字员工服务。
 *
 * @author 余炘洋
 * @date 2026-02-28
 */
@Service
@RequiredArgsConstructor
public class DigitalEmployeeService {

    private static final Integer ACTIVE_STATUS = 1;
    private static final Integer INACTIVE_STATUS = 0;
    private static final Integer NOT_DELETED = 0;
    private static final Integer DELETED = 1;
    private static final String EMPLOYEE_CODE_PREFIX = "EMP";
    private static final int MAX_GENERATE_ATTEMPTS = 5;
    private static final String REQUEST_SOURCE_DIGITAL_EMPLOYEE = "DIGITAL_EMPLOYEE";
    private static final String TRIGGER_SOURCE_MANUAL = "MANUAL";
    private static final String TRIGGER_SOURCE_SCHEDULED = "SCHEDULED";
    private static final String EXECUTION_ERROR_MESSAGE = "数字员工执行失败，请检查任务配置";
    private static final String ALERT_RULE_FAILURE = "FAILURE";
    private static final String ALERT_RULE_EMPTY_RESULT = "EMPTY_RESULT";
    private static final String ALERT_RULE_RESULT_ROWS_GT = "RESULT_ROWS_GT";
    private static final String ALERT_RULE_RESULT_ROWS_LT = "RESULT_ROWS_LT";
    private static final int DEFAULT_PUSH_MAX_ROWS = 3;
    private static final int MAX_PUSH_MAX_ROWS = 10;
    private static final int MAX_PUSH_TITLE_TEMPLATE_LENGTH = 255;
    private static final String DEFAULT_PUSH_TITLE_TEMPLATE = "【{scenario}】{employeeName} 已完成，结果 {resultRows} 行";
    private static final String DEFAULT_PUSH_MESSAGE_TEMPLATE = "执行时间 {executeTime}，问题：{question}，摘要：{answer}";
    private static final List<String> SUPPORTED_PUSH_CHANNELS = List.of("webhook", "dingtalk", "wecom");
    private static final int DEFAULT_WINDOW_HOURS = 24;
    private static final int MAX_WINDOW_HOURS = 24 * 7;
    private static final int MIN_WINDOW_HOURS = 1;
    private static final int TOP_EMPLOYEE_LIMIT = 5;
    /**
     * 数字员工单次运行的固定策略：只允许系统安全查询插件，一次调用，具备有界自愈与截止时间。
     */
    private static final AgentRunPolicy RUN_POLICY = new AgentRunPolicy(
            Set.of(SmartSqlExecutor.PLUGIN_ID),
            1,
            AgentCallerType.SYSTEM,
            3,
            3,
            120_000L
    );

    private final DigitalEmployeeRepository employeeRepository;
    private final DigitalEmployeeRunLogRepository runLogRepository;
    private final AgentPluginExecutor pluginExecutor;
    private final ResultRefiner resultRefiner;
    private final PageParamResolver pageParamResolver;
    private final OpsAlertService opsAlertService;
    private final TenantRepository tenantRepository;

    /**
     * 查询数字员工列表。
     *
     * @param tenantId 租户ID
     * @param keyword  关键字
     * @param status   状态
     * @return 数字员工列表
     * @author 余炘洋
     * @date 2026-02-28
     */
    public List<DigitalEmployeeView> listEmployees(String tenantId, String keyword, Integer status) {
        String safeTenantId = normalizeText(tenantId);
        String safeKeyword = normalizeKeyword(keyword);
        return employeeRepository.findByIsDeleted(NOT_DELETED).stream()
                .filter(employee -> matchTenant(employee, safeTenantId))
                .filter(employee -> matchStatus(employee, status))
                .filter(employee -> matchKeyword(employee, safeKeyword))
                .sorted(Comparator.comparing(DigitalEmployee::getUpdateTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toView)
                .toList();
    }

    /**
     * 查询数字员工详情。
     *
     * @param id 员工ID
     * @return 数字员工详情
     * @author 余炘洋
     * @date 2026-02-28
     */
    public DigitalEmployeeView getEmployee(Long id) {
        return toView(requireEmployee(id));
    }

    /**
     * 创建数字员工。
     *
     * @param request 创建请求
     * @return 数字员工
     * @author 余炘洋
     * @date 2026-02-28
     */
    @Transactional
    public DigitalEmployeeView createEmployee(DigitalEmployeeCreateRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "数字员工信息不能为空");
        }
        String tenantId = normalizeRequired(request.getTenantId(), "租户ID不能为空");
        String employeeName = normalizeRequired(request.getEmployeeName(), "员工名称不能为空");
        String defaultQuestion = normalizeRequired(request.getDefaultQuestion(), "默认执行问题不能为空");

        String employeeCode = normalizeCode(request.getEmployeeCode());
        if (!StringUtils.hasText(employeeCode)) {
            employeeCode = generateEmployeeCode(tenantId);
        }
        if (employeeRepository.existsByTenantIdAndEmployeeCodeAndIsDeleted(tenantId, employeeCode, NOT_DELETED)) {
            throw new BizException(ErrorCode.CONFLICT, "员工编码已存在");
        }

        DigitalEmployee employee = new DigitalEmployee();
        employee.setTenantId(tenantId);
        employee.setEmployeeCode(employeeCode);
        employee.setEmployeeName(employeeName);
        employee.setRoleName(normalizeText(request.getRoleName()));
        employee.setScenario(normalizeText(request.getScenario()));
        employee.setPromptTemplate(normalizeText(request.getPromptTemplate()));
        employee.setDefaultQuestion(defaultQuestion);
        employee.setScheduleEnabled(resolveStatus(request.getScheduleEnabled(), INACTIVE_STATUS));
        employee.setScheduleCron(normalizeScheduleCron(request.getScheduleCron(), employee.getScheduleEnabled()));
        employee.setAlertEnabled(resolveStatus(request.getAlertEnabled(), INACTIVE_STATUS));
        employee.setAlertRuleType(normalizeAlertRuleType(request.getAlertRuleType(), employee.getAlertEnabled()));
        employee.setAlertThreshold(normalizeAlertThreshold(request.getAlertThreshold(), employee.getAlertRuleType(), employee.getAlertEnabled()));
        employee.setPushEnabled(resolveStatus(request.getPushEnabled(), INACTIVE_STATUS));
        employee.setPushChannels(normalizePushChannels(request.getPushChannels(), employee.getPushEnabled()));
        employee.setPushMaxRows(normalizePushMaxRows(request.getPushMaxRows(), employee.getPushEnabled()));
        employee.setPushTitleTemplate(normalizePushTitleTemplate(request.getPushTitleTemplate(), employee.getPushEnabled()));
        employee.setPushMessageTemplate(normalizePushMessageTemplate(request.getPushMessageTemplate(), employee.getPushEnabled()));
        employee.setStatus(resolveStatus(request.getStatus(), ACTIVE_STATUS));
        employee.setRunCount(0L);
        employee.setSuccessCount(0L);
        employee.setLastRunStatus(null);
        employee.setLastRunTime(null);
        employee.setLastScheduleTime(null);
        employee.setLastErrorMessage(null);
        employee.setRemark(normalizeText(request.getRemark()));
        employee.setIsDeleted(NOT_DELETED);
        LocalDateTime now = LocalDateTime.now();
        employee.setCreateTime(now);
        employee.setUpdateTime(now);
        return toView(employeeRepository.save(employee));
    }

    /**
     * 更新数字员工。
     *
     * @param id      员工ID
     * @param request 更新请求
     * @return 数字员工
     * @author 余炘洋
     * @date 2026-02-28
     */
    @Transactional
    public DigitalEmployeeView updateEmployee(Long id, DigitalEmployeeUpdateRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "数字员工信息不能为空");
        }
        DigitalEmployee employee = requireEmployee(id);

        String tenantId = employee.getTenantId();
        if (request.getTenantId() != null) {
            tenantId = normalizeRequired(request.getTenantId(), "租户ID不能为空");
        }
        if (request.getEmployeeCode() != null) {
            String code = normalizeCode(request.getEmployeeCode());
            if (!StringUtils.hasText(code)) {
                throw new BizException(ErrorCode.INVALID_PARAM, "员工编码不能为空");
            }
            if ((!tenantId.equals(employee.getTenantId()) || !code.equals(employee.getEmployeeCode()))
                    && employeeRepository.existsByTenantIdAndEmployeeCodeAndIsDeleted(tenantId, code, NOT_DELETED)) {
                throw new BizException(ErrorCode.CONFLICT, "员工编码已存在");
            }
            employee.setEmployeeCode(code);
        } else if (!tenantId.equals(employee.getTenantId())
                && employeeRepository.existsByTenantIdAndEmployeeCodeAndIsDeleted(tenantId, employee.getEmployeeCode(), NOT_DELETED)) {
            throw new BizException(ErrorCode.CONFLICT, "员工编码已存在");
        }
        employee.setTenantId(tenantId);

        if (request.getEmployeeName() != null) {
            employee.setEmployeeName(normalizeRequired(request.getEmployeeName(), "员工名称不能为空"));
        }
        if (request.getRoleName() != null) {
            employee.setRoleName(normalizeText(request.getRoleName()));
        }
        if (request.getScenario() != null) {
            employee.setScenario(normalizeText(request.getScenario()));
        }
        if (request.getPromptTemplate() != null) {
            employee.setPromptTemplate(normalizeText(request.getPromptTemplate()));
        }
        if (request.getDefaultQuestion() != null) {
            employee.setDefaultQuestion(normalizeRequired(request.getDefaultQuestion(), "默认执行问题不能为空"));
        }
        if (request.getScheduleEnabled() != null) {
            employee.setScheduleEnabled(resolveStatus(request.getScheduleEnabled(), employee.getScheduleEnabled()));
        }
        if (request.getScheduleCron() != null || request.getScheduleEnabled() != null) {
            employee.setScheduleCron(normalizeScheduleCron(
                    request.getScheduleCron() != null ? request.getScheduleCron() : employee.getScheduleCron(),
                    employee.getScheduleEnabled()
            ));
            if (!ACTIVE_STATUS.equals(employee.getScheduleEnabled())) {
                employee.setLastScheduleTime(null);
            }
        }
        if (request.getAlertEnabled() != null) {
            employee.setAlertEnabled(resolveStatus(request.getAlertEnabled(), employee.getAlertEnabled()));
        }
        if (request.getAlertRuleType() != null || request.getAlertEnabled() != null) {
            employee.setAlertRuleType(normalizeAlertRuleType(
                    request.getAlertRuleType() != null ? request.getAlertRuleType() : employee.getAlertRuleType(),
                    employee.getAlertEnabled()
            ));
        }
        if (request.getAlertThreshold() != null || request.getAlertRuleType() != null || request.getAlertEnabled() != null) {
            employee.setAlertThreshold(normalizeAlertThreshold(
                    request.getAlertThreshold() != null ? request.getAlertThreshold() : employee.getAlertThreshold(),
                    employee.getAlertRuleType(),
                    employee.getAlertEnabled()
            ));
        }
        if (request.getPushEnabled() != null) {
            employee.setPushEnabled(resolveStatus(request.getPushEnabled(), employee.getPushEnabled()));
        }
        if (request.getPushChannels() != null || request.getPushEnabled() != null) {
            employee.setPushChannels(normalizePushChannels(
                    request.getPushChannels() != null ? request.getPushChannels() : parsePushChannels(employee.getPushChannels()),
                    employee.getPushEnabled()
            ));
        }
        if (request.getPushMaxRows() != null || request.getPushEnabled() != null) {
            employee.setPushMaxRows(normalizePushMaxRows(
                    request.getPushMaxRows() != null ? request.getPushMaxRows() : employee.getPushMaxRows(),
                    employee.getPushEnabled()
            ));
        }
        if (request.getPushTitleTemplate() != null || request.getPushEnabled() != null) {
            employee.setPushTitleTemplate(normalizePushTitleTemplate(
                    request.getPushTitleTemplate() != null ? request.getPushTitleTemplate() : employee.getPushTitleTemplate(),
                    employee.getPushEnabled()
            ));
        }
        if (request.getPushMessageTemplate() != null || request.getPushEnabled() != null) {
            employee.setPushMessageTemplate(normalizePushMessageTemplate(
                    request.getPushMessageTemplate() != null ? request.getPushMessageTemplate() : employee.getPushMessageTemplate(),
                    employee.getPushEnabled()
            ));
        }
        if (request.getStatus() != null) {
            employee.setStatus(resolveStatus(request.getStatus(), employee.getStatus()));
        }
        if (request.getRemark() != null) {
            employee.setRemark(normalizeText(request.getRemark()));
        }
        employee.setUpdateTime(LocalDateTime.now());
        return toView(employeeRepository.save(employee));
    }

    /**
     * 删除数字员工（软删除）。
     *
     * @param id 员工ID
     * @author 余炘洋
     * @date 2026-02-28
     */
    @Transactional
    public void deleteEmployee(Long id) {
        DigitalEmployee employee = requireEmployee(id);
        employee.setStatus(INACTIVE_STATUS);
        employee.setIsDeleted(DELETED);
        employee.setUpdateTime(LocalDateTime.now());
        employeeRepository.save(employee);
    }

    /**
     * 运行数字员工。
     *
     * @param id      员工ID
     * @param request 执行请求
     * @return 执行结果
     * @author 余炘洋
     * @date 2026-02-28
     */
    @Transactional
    public DigitalEmployeeRunResult run(Long id, DigitalEmployeeRunRequest request) {
        DigitalEmployee employee = requireEmployee(id);
        if (!ACTIVE_STATUS.equals(employee.getStatus())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "数字员工已停用，无法执行");
        }
        return runInternal(employee, request, null);
    }

    /**
     * 运行定时数字员工。
     *
     * @param id                 员工ID
     * @param expectedScheduleTime 本次计划触发时间
     * @return 执行结果
     * @author 余炘洋
     * @date 2026-04-14
     */
    @Transactional
    public DigitalEmployeeRunResult runScheduled(Long id, LocalDateTime expectedScheduleTime) {
        DigitalEmployee employee = requireEmployee(id);
        if (!ACTIVE_STATUS.equals(employee.getStatus()) || !ACTIVE_STATUS.equals(employee.getScheduleEnabled())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "数字员工未开启定时执行");
        }
        return runInternal(employee, null, expectedScheduleTime);
    }

    /**
     * 查询运行日志分页。
     *
     * @param query 查询条件
     * @param page  页码
     * @param size  每页大小
     * @return 分页结果
     * @author 余炘洋
     * @date 2026-02-28
     */
    public PageResult<DigitalEmployeeRunLogView> listRunLogs(DigitalEmployeeRunLogQuery query, int page, int size) {
        DigitalEmployeeRunLogQuery safeQuery = query == null ? new DigitalEmployeeRunLogQuery() : query;
        PageParam param = pageParamResolver.resolve(page, size);
        Specification<DigitalEmployeeRunLog> specification = (root, criteriaQuery, criteriaBuilder) -> {
            var predicate = criteriaBuilder.conjunction();
            if (StringUtils.hasText(safeQuery.getTenantId())) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("tenantId"), safeQuery.getTenantId().trim()));
            }
            if (safeQuery.getEmployeeId() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("employeeId"), safeQuery.getEmployeeId()));
            }
            if (safeQuery.getSuccess() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("success"), safeQuery.getSuccess()));
            }
            if (StringUtils.hasText(safeQuery.getTriggerSource())) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("triggerSource"), safeQuery.getTriggerSource().trim().toUpperCase(Locale.ROOT)));
            }
            if (safeQuery.getStartTime() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.greaterThanOrEqualTo(root.get("createTime"), safeQuery.getStartTime()));
            }
            if (safeQuery.getEndTime() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.lessThanOrEqualTo(root.get("createTime"), safeQuery.getEndTime()));
            }
            return predicate;
        };

        PageRequest pageable = PageRequest.of(Math.max(param.page() - 1, 0), param.size(), Sort.by(Sort.Direction.DESC, "createTime"));
        Page<DigitalEmployeeRunLog> resultPage = runLogRepository.findAll(specification, pageable);

        Set<Long> employeeIds = resultPage.getContent().stream()
                .map(DigitalEmployeeRunLog::getEmployeeId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, String> employeeNameMap = employeeIds.isEmpty() ? Collections.emptyMap()
                : employeeRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(DigitalEmployee::getId, DigitalEmployee::getEmployeeName, (a, b) -> a));

        List<DigitalEmployeeRunLogView> records = resultPage.getContent().stream()
                .map(log -> toRunLogView(log, employeeNameMap.get(log.getEmployeeId())))
                .toList();
        return new PageResult<>(records, resultPage.getTotalElements(), param.page(), param.size());
    }

    /**
     * 查询数字员工运营概览。
     *
     * @param tenantId    租户ID
     * @param windowHours 统计窗口（小时）
     * @return 运营概览
     * @author 余炘洋
     * @date 2026-02-28
     */
    public DigitalEmployeeOverviewView overview(String tenantId, Integer windowHours) {
        String safeTenantId = normalizeText(tenantId);
        int safeWindowHours = resolveWindowHours(windowHours);
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(safeWindowHours);

        List<DigitalEmployee> employeeList = employeeRepository.findByIsDeleted(NOT_DELETED).stream()
                .filter(employee -> matchTenant(employee, safeTenantId))
                .toList();
        long totalEmployeeCount = employeeList.size();
        long activeEmployeeCount = employeeList.stream()
                .filter(employee -> ACTIVE_STATUS.equals(employee.getStatus()))
                .count();

        long runCount = runLogRepository.countInWindow(safeTenantId, startTime, endTime);
        long successCount = runLogRepository.countSuccessInWindow(safeTenantId, startTime, endTime);
        long failureCount = Math.max(0L, runCount - successCount);

        Map<Long, String> employeeNameMap = employeeList.stream()
                .filter(employee -> employee.getId() != null)
                .collect(Collectors.toMap(DigitalEmployee::getId, DigitalEmployee::getEmployeeName, (a, b) -> a));

        List<DigitalEmployeeTopEmployeeView> topEmployees = runLogRepository
                .findTopRunEmployees(safeTenantId, startTime, endTime, TOP_EMPLOYEE_LIMIT)
                .stream()
                .map(projection -> toTopEmployeeView(projection, employeeNameMap))
                .toList();

        DigitalEmployeeOverviewView overview = new DigitalEmployeeOverviewView();
        overview.setTenantId(safeTenantId);
        overview.setWindowHours(safeWindowHours);
        overview.setStartTime(startTime);
        overview.setEndTime(endTime);
        overview.setTotalEmployeeCount(totalEmployeeCount);
        overview.setActiveEmployeeCount(activeEmployeeCount);
        overview.setRunCount(runCount);
        overview.setSuccessCount(successCount);
        overview.setFailureCount(failureCount);
        overview.setSuccessRate(calculateRate(successCount, runCount));
        overview.setTopEmployees(topEmployees);
        return overview;
    }

    /**
     * 从插件异常原因链中提取物理计费异常。
     *
     * <p>插件网关会把插件内部抛出的运行时异常统一包装为 {@link AgentPluginException}，
     * 而计费状态机的不确定结果不能被降级为普通执行失败，必须继续向上传递。</p>
     *
     * @param throwable 插件异常
     * @return 物理计费异常；不存在时返回 null
     * @author 余炘洋
     * @date 2026-10-17
     */
    private PhysicalBillingException findPhysicalBillingException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof PhysicalBillingException billingException) {
                return billingException;
            }
            current = current.getCause();
        }
        return null;
    }

    private DigitalEmployeeRunResult runInternal(DigitalEmployee employee,
                                                 DigitalEmployeeRunRequest request,
                                                 LocalDateTime expectedScheduleTime) {
        String question = resolveRunQuestion(employee, request);
        String triggerSource = expectedScheduleTime == null
                ? resolveTriggerSource(request == null ? null : request.getTriggerSource())
                : TRIGGER_SOURCE_SCHEDULED;
        String workflowRequestId = RequestTraceContext.resolveRequestId();
        AgentExecutionContext executionContext = new AgentExecutionContext(
                workflowRequestId,
                workflowRequestId,
                employee.getTenantId(),
                null,
                null
        );

        long startMs = System.currentTimeMillis();
        SqlExecutionResult executionResult;
        try {
            executionResult = pluginExecutor.execute(
                    SmartSqlExecutor.PLUGIN_ID,
                    executionContext,
                    new AgentRunControl(RUN_POLICY),
                    new SqlQueryTask(question, REQUEST_SOURCE_DIGITAL_EMPLOYEE),
                    SqlExecutionResult.class
            );
        } catch (AgentPluginException ex) {
            PhysicalBillingException billingException = findPhysicalBillingException(ex);
            if (billingException != null) {
                throw billingException;
            }
            executionResult = SqlExecutionResult.failure(null, EXECUTION_ERROR_MESSAGE);
        } catch (PhysicalBillingException ex) {
            throw ex;
        } catch (Exception ex) {
            executionResult = SqlExecutionResult.failure(null, EXECUTION_ERROR_MESSAGE);
        }
        SqlExecutionResult safeResult = sanitizeExecutionResult(executionResult);
        String answer = resultRefiner.refine(executionContext, question, safeResult);
        long durationMs = Math.max(0L, System.currentTimeMillis() - startMs);
        LocalDateTime now = LocalDateTime.now();

        DigitalEmployeeRunLog runLog = new DigitalEmployeeRunLog();
        runLog.setEmployeeId(employee.getId());
        runLog.setTenantId(employee.getTenantId());
        runLog.setTriggerSource(triggerSource);
        runLog.setQuestion(question);
        runLog.setAnswer(answer);
        runLog.setExecutedSql(safeResult.getSql());
        runLog.setSuccess(safeResult.isSuccess() ? ACTIVE_STATUS : INACTIVE_STATUS);
        runLog.setErrorMessage(safeResult.getErrorMessage());
        runLog.setResultRows(resolveResultRows(safeResult));
        runLog.setRequestId(workflowRequestId);
        runLog.setDurationMs(durationMs);
        runLog.setCreateTime(now);
        AlertDispatchResult alertDispatchResult = sendScheduledAlertIfNecessary(employee, triggerSource, runLog, safeResult, question, answer);
        AlertDispatchResult pushDispatchResult = sendScheduledResultIfNecessary(employee, triggerSource, runLog, safeResult, question, answer);
        runLog.setAlertDeliveryStatus(resolveDispatchStatus(alertDispatchResult));
        runLog.setAlertDeliveryDetail(resolveDispatchDetail(alertDispatchResult));
        runLog.setPushDeliveryStatus(resolveDispatchStatus(pushDispatchResult));
        runLog.setPushDeliveryDetail(resolveDispatchDetail(pushDispatchResult));
        DigitalEmployeeRunLog savedRunLog = runLogRepository.save(runLog);

        long runCount = employee.getRunCount() == null ? 0L : employee.getRunCount();
        long successCount = employee.getSuccessCount() == null ? 0L : employee.getSuccessCount();
        employee.setRunCount(runCount + 1);
        if (safeResult.isSuccess()) {
            successCount += 1;
        }
        employee.setSuccessCount(successCount);
        employee.setLastRunStatus(safeResult.isSuccess() ? ACTIVE_STATUS : INACTIVE_STATUS);
        employee.setLastRunTime(now);
        if (expectedScheduleTime != null) {
            employee.setLastScheduleTime(expectedScheduleTime);
        }
        employee.setLastErrorMessage(safeResult.isSuccess() ? null : safeResult.getErrorMessage());
        employee.setUpdateTime(now);
        employeeRepository.save(employee);

        DigitalEmployeeRunResult result = new DigitalEmployeeRunResult();
        result.setRunLogId(savedRunLog.getId());
        result.setEmployeeId(employee.getId());
        result.setEmployeeName(employee.getEmployeeName());
        result.setTenantId(employee.getTenantId());
        result.setTriggerSource(triggerSource);
        result.setQuestion(question);
        result.setAnswer(answer);
        result.setSql(safeResult.getSql());
        result.setData(safeResult.getData());
        result.setSuccess(safeResult.isSuccess());
        result.setErrorMessage(safeResult.getErrorMessage());
        result.setDurationMs(durationMs);
        result.setExecuteTime(now);
        return result;
    }

    private DigitalEmployee requireEmployee(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "数字员工ID不能为空");
        }
        return employeeRepository.findByIdAndIsDeleted(id, NOT_DELETED)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "数字员工不存在"));
    }

    private String resolveRunQuestion(DigitalEmployee employee, DigitalEmployeeRunRequest request) {
        if (request != null && StringUtils.hasText(request.getQuestion())) {
            return request.getQuestion().trim();
        }
        if (StringUtils.hasText(employee.getDefaultQuestion())) {
            return employee.getDefaultQuestion().trim();
        }
        throw new BizException(ErrorCode.INVALID_PARAM, "执行问题不能为空");
    }

    private String resolveTriggerSource(String triggerSource) {
        if (!StringUtils.hasText(triggerSource)) {
            return TRIGGER_SOURCE_MANUAL;
        }
        String normalized = triggerSource.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 32) {
            throw new BizException(ErrorCode.INVALID_PARAM, "触发来源长度不能超过32");
        }
        return normalized;
    }

    private SqlExecutionResult sanitizeExecutionResult(SqlExecutionResult executionResult) {
        if (executionResult == null) {
            return SqlExecutionResult.failure(null, EXECUTION_ERROR_MESSAGE);
        }
        if (executionResult.isSuccess()) {
            return executionResult;
        }
        return SqlExecutionResult.failure(executionResult.getSql(), EXECUTION_ERROR_MESSAGE);
    }

    private int resolveResultRows(SqlExecutionResult result) {
        if (result == null || result.getData() == null) {
            return 0;
        }
        return result.getData().size();
    }

    private String normalizeScheduleCron(String cron, Integer scheduleEnabled) {
        if (!ACTIVE_STATUS.equals(scheduleEnabled)) {
            return null;
        }
        String normalized = normalizeRequired(cron, "启用定时执行时必须填写 Cron");
        try {
            CronExpression.parse(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.INVALID_PARAM, "定时执行 Cron 格式非法");
        }
        return normalized;
    }

    private String normalizeAlertRuleType(String ruleType, Integer alertEnabled) {
        if (!ACTIVE_STATUS.equals(alertEnabled)) {
            return null;
        }
        String normalized = normalizeRequired(ruleType, "启用告警时必须选择告警规则");
        return switch (normalized.trim().toUpperCase(Locale.ROOT)) {
            case ALERT_RULE_FAILURE, ALERT_RULE_EMPTY_RESULT, ALERT_RULE_RESULT_ROWS_GT, ALERT_RULE_RESULT_ROWS_LT -> normalized.trim().toUpperCase(Locale.ROOT);
            default -> throw new BizException(ErrorCode.INVALID_PARAM, "告警规则类型非法");
        };
    }

    private Integer normalizeAlertThreshold(Integer alertThreshold, String ruleType, Integer alertEnabled) {
        if (!ACTIVE_STATUS.equals(alertEnabled)) {
            return null;
        }
        if (!StringUtils.hasText(ruleType)) {
            return null;
        }
        if (ALERT_RULE_RESULT_ROWS_GT.equals(ruleType) || ALERT_RULE_RESULT_ROWS_LT.equals(ruleType)) {
            if (alertThreshold == null || alertThreshold < 0) {
                throw new BizException(ErrorCode.INVALID_PARAM, "结果行数阈值必须大于等于0");
            }
            return alertThreshold;
        }
        return null;
    }

    private Integer normalizePushMaxRows(Integer pushMaxRows, Integer pushEnabled) {
        if (!ACTIVE_STATUS.equals(pushEnabled)) {
            return null;
        }
        if (pushMaxRows == null) {
            return DEFAULT_PUSH_MAX_ROWS;
        }
        if (pushMaxRows < 0) {
            throw new BizException(ErrorCode.INVALID_PARAM, "结果推送预览行数必须大于等于0");
        }
        return Math.min(pushMaxRows, MAX_PUSH_MAX_ROWS);
    }

    private String normalizePushChannels(List<String> channels, Integer pushEnabled) {
        if (!ACTIVE_STATUS.equals(pushEnabled)) {
            return null;
        }
        if (channels == null || channels.isEmpty()) {
            return null;
        }
        List<String> normalized = channels.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        for (String channel : normalized) {
            if (!SUPPORTED_PUSH_CHANNELS.contains(channel)) {
                throw new BizException(ErrorCode.INVALID_PARAM, "结果推送通道非法");
            }
        }
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }

    private String normalizePushTitleTemplate(String template, Integer pushEnabled) {
        if (!ACTIVE_STATUS.equals(pushEnabled)) {
            return null;
        }
        String normalized = normalizeText(template);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.length() > MAX_PUSH_TITLE_TEMPLATE_LENGTH) {
            throw new BizException(ErrorCode.INVALID_PARAM, "结果推送标题模板长度不能超过255");
        }
        return normalized;
    }

    private String normalizePushMessageTemplate(String template, Integer pushEnabled) {
        if (!ACTIVE_STATUS.equals(pushEnabled)) {
            return null;
        }
        return normalizeText(template);
    }

    private AlertDispatchResult sendScheduledAlertIfNecessary(DigitalEmployee employee,
                                                              String triggerSource,
                                                              DigitalEmployeeRunLog runLog,
                                                              SqlExecutionResult executionResult,
                                                              String question,
                                                              String answer) {
        if (!TRIGGER_SOURCE_SCHEDULED.equals(triggerSource) || !ACTIVE_STATUS.equals(employee.getAlertEnabled())) {
            return null;
        }
        String alertRuleType = employee.getAlertRuleType();
        int resultRows = resolveResultRows(executionResult);
        boolean shouldAlert = switch (alertRuleType) {
            case ALERT_RULE_FAILURE -> executionResult == null || !executionResult.isSuccess();
            case ALERT_RULE_EMPTY_RESULT -> executionResult != null && executionResult.isSuccess() && resultRows == 0;
            case ALERT_RULE_RESULT_ROWS_GT -> resultRows > resolveSafeThreshold(employee.getAlertThreshold());
            case ALERT_RULE_RESULT_ROWS_LT -> resultRows < resolveSafeThreshold(employee.getAlertThreshold());
            default -> false;
        };
        if (!shouldAlert) {
            return null;
        }
        String title = "数字员工订阅告警";
        String message = buildAlertMessage(employee, alertRuleType, executionResult, resultRows);
        return opsAlertService.sendAlertWithResult(
                title,
                message,
                buildAlertDetails(employee, runLog, question, answer, executionResult, resultRows)
        );
    }

    private AlertDispatchResult sendScheduledResultIfNecessary(DigitalEmployee employee,
                                                               String triggerSource,
                                                               DigitalEmployeeRunLog runLog,
                                                               SqlExecutionResult executionResult,
                                                               String question,
                                                               String answer) {
        if (!TRIGGER_SOURCE_SCHEDULED.equals(triggerSource)
                || !ACTIVE_STATUS.equals(employee.getPushEnabled())
                || executionResult == null
                || !executionResult.isSuccess()) {
            return null;
        }
        Map<String, String> templateValues = buildPushTemplateValues(employee, runLog, executionResult, question, answer);
        String title = renderPushTemplate(employee.getPushTitleTemplate(), DEFAULT_PUSH_TITLE_TEMPLATE, templateValues);
        String message = renderPushTemplate(employee.getPushMessageTemplate(), DEFAULT_PUSH_MESSAGE_TEMPLATE, templateValues);
        return opsAlertService.sendAlertToChannelsWithResult(
                title,
                message,
                buildPushDetails(employee, runLog, executionResult, question, answer),
                resolveEffectivePushChannels(employee)
        );
    }

    private String buildAlertMessage(DigitalEmployee employee,
                                     String alertRuleType,
                                     SqlExecutionResult executionResult,
                                     int resultRows) {
        return switch (alertRuleType) {
            case ALERT_RULE_FAILURE -> "定时任务执行失败，请检查数字员工问题模板、模型链路或租户数据源。";
            case ALERT_RULE_EMPTY_RESULT -> "定时任务执行成功但结果为空，请确认当前业务数据是否异常或筛选条件过严。";
            case ALERT_RULE_RESULT_ROWS_GT -> "定时任务命中结果行数上阈值，请及时查看异常明细。";
            case ALERT_RULE_RESULT_ROWS_LT -> "定时任务命中结果行数下阈值，请确认业务数据是否低于预期。";
            default -> "数字员工触发了订阅告警。";
        };
    }

    private Map<String, Object> buildAlertDetails(DigitalEmployee employee,
                                                  DigitalEmployeeRunLog runLog,
                                                  String question,
                                                  String answer,
                                                  SqlExecutionResult executionResult,
                                                  int resultRows) {
        return Map.ofEntries(
                Map.entry("tenantId", employee.getTenantId()),
                Map.entry("employeeId", employee.getId()),
                Map.entry("employeeName", employee.getEmployeeName()),
                Map.entry("scenario", defaultText(employee.getScenario())),
                Map.entry("question", defaultText(question)),
                Map.entry("triggerSource", defaultText(runLog.getTriggerSource())),
                Map.entry("alertRuleType", defaultText(employee.getAlertRuleType())),
                Map.entry("alertThreshold", employee.getAlertThreshold() == null ? "-" : employee.getAlertThreshold()),
                Map.entry("resultRows", resultRows),
                Map.entry("success", executionResult != null && executionResult.isSuccess()),
                Map.entry("errorMessage", executionResult == null ? "-" : defaultText(executionResult.getErrorMessage())),
                Map.entry("answer", abbreviate(answer, 200))
        );
    }

    private Map<String, Object> buildPushDetails(DigitalEmployee employee,
                                                 DigitalEmployeeRunLog runLog,
                                                 SqlExecutionResult executionResult,
                                                 String question,
                                                 String answer) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("tenantId", employee.getTenantId());
        details.put("employeeId", employee.getId());
        details.put("employeeName", employee.getEmployeeName());
        details.put("scenario", defaultText(employee.getScenario()));
        details.put("question", defaultText(question));
        details.put("triggerSource", defaultText(runLog.getTriggerSource()));
        details.put("pushChannels", resolveEffectivePushChannels(employee));
        details.put("resultRows", resolveResultRows(executionResult));
        details.put("executeTime", runLog.getCreateTime());
        details.put("answer", abbreviate(answer, 300));
        details.put("sql", abbreviate(executionResult.getSql(), 300));
        List<String> preview = renderPreviewRows(executionResult.getData(), resolveSafePushMaxRows(employee.getPushMaxRows()));
        details.put("previewRows", preview.isEmpty() ? List.of("无") : preview);
        return details;
    }

    private Map<String, String> buildPushTemplateValues(DigitalEmployee employee,
                                                        DigitalEmployeeRunLog runLog,
                                                        SqlExecutionResult executionResult,
                                                        String question,
                                                        String answer) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("tenantId", defaultText(employee.getTenantId()));
        values.put("employeeId", employee.getId() == null ? "-" : String.valueOf(employee.getId()));
        values.put("employeeName", defaultText(employee.getEmployeeName()));
        values.put("scenario", defaultText(employee.getScenario()));
        values.put("question", abbreviate(defaultText(question), 120));
        values.put("answer", abbreviate(defaultText(answer), 180));
        values.put("pushChannels", String.join(",", resolveEffectivePushChannels(employee)));
        values.put("resultRows", String.valueOf(resolveResultRows(executionResult)));
        values.put("executeTime", runLog.getCreateTime() == null ? "-" : runLog.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        values.put("triggerSource", defaultText(runLog.getTriggerSource()));
        values.put("sql", abbreviate(defaultText(executionResult == null ? null : executionResult.getSql()), 180));
        return values;
    }

    private String renderPushTemplate(String configuredTemplate,
                                      String defaultTemplate,
                                      Map<String, String> values) {
        String template = StringUtils.hasText(configuredTemplate) ? configuredTemplate : defaultTemplate;
        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", defaultText(entry.getValue()));
        }
        return rendered;
    }

    private List<String> parsePushChannels(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
                .filter(StringUtils::hasText)
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private List<String> resolveEffectivePushChannels(DigitalEmployee employee) {
        List<String> employeeChannels = parsePushChannels(employee == null ? null : employee.getPushChannels());
        if (!employeeChannels.isEmpty()) {
            return employeeChannels;
        }
        if (employee == null || !StringUtils.hasText(employee.getTenantId())) {
            return Collections.emptyList();
        }
        return tenantRepository.findByTenantIdAndIsDeleted(employee.getTenantId(), NOT_DELETED)
                .map(TenantInfo::getNotificationChannels)
                .map(this::parsePushChannels)
                .orElse(Collections.emptyList());
    }

    private int resolveSafeThreshold(Integer threshold) {
        return threshold == null ? 0 : Math.max(threshold, 0);
    }

    private int resolveSafePushMaxRows(Integer pushMaxRows) {
        if (pushMaxRows == null) {
            return DEFAULT_PUSH_MAX_ROWS;
        }
        return Math.max(0, Math.min(pushMaxRows, MAX_PUSH_MAX_ROWS));
    }

    private Integer resolveDispatchStatus(AlertDispatchResult dispatchResult) {
        if (dispatchResult == null) {
            return null;
        }
        return dispatchResult.success() ? ACTIVE_STATUS : INACTIVE_STATUS;
    }

    private String resolveDispatchDetail(AlertDispatchResult dispatchResult) {
        if (dispatchResult == null) {
            return null;
        }
        return dispatchResult.summarize();
    }

    private List<String> renderPreviewRows(List<Map<String, Object>> data, int maxRows) {
        if (data == null || data.isEmpty() || maxRows <= 0) {
            return Collections.emptyList();
        }
        List<String> previewRows = new ArrayList<>();
        for (Map<String, Object> row : data.stream().limit(maxRows).toList()) {
            previewRows.add(renderRow(row));
        }
        return previewRows;
    }

    private String renderRow(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return "-";
        }
        return row.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + abbreviate(defaultText(stringifyValue(entry.getValue())), 80))
                .collect(Collectors.joining(", "));
    }

    private String stringifyValue(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof CharSequence) {
            return String.valueOf(value);
        }
        return String.valueOf(value);
    }

    private String defaultText(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return defaultText(value);
        }
        return value.substring(0, maxLength) + "...";
    }

    private int resolveWindowHours(Integer windowHours) {
        if (windowHours == null) {
            return DEFAULT_WINDOW_HOURS;
        }
        return Math.min(Math.max(windowHours, MIN_WINDOW_HOURS), MAX_WINDOW_HOURS);
    }

    private String normalizeRequired(String value, String errorMessage) {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized)) {
            throw new BizException(ErrorCode.INVALID_PARAM, errorMessage);
        }
        return normalized;
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private int resolveStatus(Integer status, Integer fallback) {
        if (status == null) {
            return fallback == null ? ACTIVE_STATUS : fallback;
        }
        if (!ACTIVE_STATUS.equals(status) && !INACTIVE_STATUS.equals(status)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "状态值非法");
        }
        return status;
    }

    private boolean matchTenant(DigitalEmployee employee, String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return true;
        }
        return tenantId.equals(employee.getTenantId());
    }

    private boolean matchStatus(DigitalEmployee employee, Integer status) {
        if (status == null) {
            return true;
        }
        return status.equals(employee.getStatus());
    }

    private boolean matchKeyword(DigitalEmployee employee, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        return containsIgnoreCase(employee.getEmployeeCode(), keyword)
                || containsIgnoreCase(employee.getEmployeeName(), keyword)
                || containsIgnoreCase(employee.getRoleName(), keyword)
                || containsIgnoreCase(employee.getScenario(), keyword);
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(keyword)) {
            return false;
        }
        return source.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String generateEmployeeCode(String tenantId) {
        int attempt = 0;
        String code = buildEmployeeCode();
        while (employeeRepository.existsByTenantIdAndEmployeeCodeAndIsDeleted(tenantId, code, NOT_DELETED)) {
            if (attempt++ >= MAX_GENERATE_ATTEMPTS) {
                throw new BizException(ErrorCode.BUSINESS_ERROR, "员工编码生成失败，请重试");
            }
            code = buildEmployeeCode();
        }
        return code;
    }

    private String buildEmployeeCode() {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT)
                .format(LocalDateTime.now());
        int random = ThreadLocalRandom.current().nextInt(1000, 10000);
        return EMPLOYEE_CODE_PREFIX + timestamp + random;
    }

    private DigitalEmployeeTopEmployeeView toTopEmployeeView(DigitalEmployeeRunLogRepository.TopRunProjection projection,
                                                             Map<Long, String> employeeNameMap) {
        long runCount = projection.getRunCount() == null ? 0L : projection.getRunCount();
        long successCount = projection.getSuccessCount() == null ? 0L : projection.getSuccessCount();
        long failureCount = Math.max(0L, runCount - successCount);
        Long employeeId = projection.getEmployeeId();
        String employeeName = employeeNameMap.get(employeeId);
        if (!StringUtils.hasText(employeeName)) {
            employeeName = "员工#" + employeeId;
        }

        DigitalEmployeeTopEmployeeView view = new DigitalEmployeeTopEmployeeView();
        view.setEmployeeId(employeeId);
        view.setEmployeeName(employeeName);
        view.setRunCount(runCount);
        view.setSuccessCount(successCount);
        view.setFailureCount(failureCount);
        view.setSuccessRate(calculateRate(successCount, runCount));
        return view;
    }

    private double calculateRate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0d;
        }
        return round2(numerator * 100d / denominator);
    }

    private double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private DigitalEmployeeView toView(DigitalEmployee employee) {
        if (employee == null) {
            return null;
        }
        DigitalEmployeeView view = new DigitalEmployeeView();
        view.setId(employee.getId());
        view.setTenantId(employee.getTenantId());
        view.setEmployeeCode(employee.getEmployeeCode());
        view.setEmployeeName(employee.getEmployeeName());
        view.setRoleName(employee.getRoleName());
        view.setScenario(employee.getScenario());
        view.setPromptTemplate(employee.getPromptTemplate());
        view.setDefaultQuestion(employee.getDefaultQuestion());
        view.setScheduleEnabled(employee.getScheduleEnabled());
        view.setScheduleCron(employee.getScheduleCron());
        view.setAlertEnabled(employee.getAlertEnabled());
        view.setAlertRuleType(employee.getAlertRuleType());
        view.setAlertThreshold(employee.getAlertThreshold());
        view.setPushEnabled(employee.getPushEnabled());
        view.setPushChannels(parsePushChannels(employee.getPushChannels()));
        view.setPushMaxRows(employee.getPushMaxRows());
        view.setPushTitleTemplate(employee.getPushTitleTemplate());
        view.setPushMessageTemplate(employee.getPushMessageTemplate());
        view.setStatus(employee.getStatus());
        view.setRunCount(employee.getRunCount());
        view.setSuccessCount(employee.getSuccessCount());
        view.setLastRunStatus(employee.getLastRunStatus());
        view.setLastRunTime(employee.getLastRunTime());
        view.setLastScheduleTime(employee.getLastScheduleTime());
        view.setLastErrorMessage(employee.getLastErrorMessage());
        view.setRemark(employee.getRemark());
        view.setCreateTime(employee.getCreateTime());
        view.setUpdateTime(employee.getUpdateTime());
        return view;
    }

    private DigitalEmployeeRunLogView toRunLogView(DigitalEmployeeRunLog runLog, String employeeName) {
        DigitalEmployeeRunLogView view = new DigitalEmployeeRunLogView();
        view.setId(runLog.getId());
        view.setEmployeeId(runLog.getEmployeeId());
        view.setEmployeeName(employeeName);
        view.setTenantId(runLog.getTenantId());
        view.setTriggerSource(runLog.getTriggerSource());
        view.setQuestion(runLog.getQuestion());
        view.setAnswer(runLog.getAnswer());
        view.setExecutedSql(runLog.getExecutedSql());
        view.setSuccess(runLog.getSuccess());
        view.setErrorMessage(runLog.getErrorMessage());
        view.setResultRows(runLog.getResultRows());
        view.setRequestId(runLog.getRequestId());
        view.setDurationMs(runLog.getDurationMs());
        view.setAlertDeliveryStatus(runLog.getAlertDeliveryStatus());
        view.setAlertDeliveryDetail(runLog.getAlertDeliveryDetail());
        view.setPushDeliveryStatus(runLog.getPushDeliveryStatus());
        view.setPushDeliveryDetail(runLog.getPushDeliveryDetail());
        view.setCreateTime(runLog.getCreateTime());
        return view;
    }
}
