package com.lingsql.employee;

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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
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
    private static final String EXECUTION_ERROR_MESSAGE = "数字员工执行失败，请检查任务配置";
    private static final int DEFAULT_WINDOW_HOURS = 24;
    private static final int MAX_WINDOW_HOURS = 24 * 7;
    private static final int MIN_WINDOW_HOURS = 1;
    private static final int TOP_EMPLOYEE_LIMIT = 5;

    private final DigitalEmployeeRepository employeeRepository;
    private final DigitalEmployeeRunLogRepository runLogRepository;
    private final SmartSqlExecutor smartSqlExecutor;
    private final ResultRefiner resultRefiner;
    private final PageParamResolver pageParamResolver;

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
        employee.setStatus(resolveStatus(request.getStatus(), ACTIVE_STATUS));
        employee.setRunCount(0L);
        employee.setSuccessCount(0L);
        employee.setLastRunStatus(null);
        employee.setLastRunTime(null);
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
        String question = resolveRunQuestion(employee, request);
        String triggerSource = resolveTriggerSource(request == null ? null : request.getTriggerSource());

        long startMs = System.currentTimeMillis();
        SqlExecutionResult executionResult;
        try {
            executionResult = smartSqlExecutor.execute(employee.getTenantId(), question, REQUEST_SOURCE_DIGITAL_EMPLOYEE);
        } catch (Exception ex) {
            executionResult = SqlExecutionResult.failure(null, EXECUTION_ERROR_MESSAGE);
        }
        SqlExecutionResult safeResult = sanitizeExecutionResult(executionResult);
        String answer = resultRefiner.refine(question, safeResult);
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
        runLog.setRequestId(RequestTraceContext.resolveRequestId());
        runLog.setDurationMs(durationMs);
        runLog.setCreateTime(now);
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
        view.setStatus(employee.getStatus());
        view.setRunCount(employee.getRunCount());
        view.setSuccessCount(employee.getSuccessCount());
        view.setLastRunStatus(employee.getLastRunStatus());
        view.setLastRunTime(employee.getLastRunTime());
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
        view.setCreateTime(runLog.getCreateTime());
        return view;
    }
}
