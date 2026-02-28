package com.lingsql.web;

import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.common.result.PageResult;
import com.lingsql.employee.DigitalEmployeeService;
import com.lingsql.employee.model.DigitalEmployeeCreateRequest;
import com.lingsql.employee.model.DigitalEmployeeOverviewView;
import com.lingsql.employee.model.DigitalEmployeeRunLogQuery;
import com.lingsql.employee.model.DigitalEmployeeRunLogView;
import com.lingsql.employee.model.DigitalEmployeeRunRequest;
import com.lingsql.employee.model.DigitalEmployeeRunResult;
import com.lingsql.employee.model.DigitalEmployeeUpdateRequest;
import com.lingsql.employee.model.DigitalEmployeeView;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数字员工管理接口。
 *
 * @author 余炘洋
 * @date 2026-02-28
 */
@RestController
@RequestMapping("/api/v1/admin/digital-employees")
@RequiredArgsConstructor
public class DigitalEmployeeAdminController {

    private final DigitalEmployeeService digitalEmployeeService;
    private final AuditOperationExecutor auditOperationExecutor;

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
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN:DIGITAL_EMPLOYEE:READ')")
    public ApiResponse<List<DigitalEmployeeView>> list(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status
    ) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(digitalEmployeeService.listEmployees(tenantId, keyword, status));
    }

    /**
     * 查询数字员工详情。
     *
     * @param id 员工ID
     * @return 数字员工详情
     * @author 余炘洋
     * @date 2026-02-28
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN:DIGITAL_EMPLOYEE:READ')")
    public ApiResponse<DigitalEmployeeView> detail(@PathVariable Long id) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(digitalEmployeeService.getEmployee(id));
    }

    /**
     * 创建数字员工。
     *
     * @param request 请求体
     * @param http    请求上下文
     * @return 数字员工
     * @author 余炘洋
     * @date 2026-02-28
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN:DIGITAL_EMPLOYEE:WRITE')")
    public ApiResponse<DigitalEmployeeView> create(@Valid @RequestBody DigitalEmployeeCreateRequest request,
                                                   HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "DIGITAL_EMPLOYEE_CREATE",
                "DIGITAL_EMPLOYEE",
                request,
                http,
                () -> digitalEmployeeService.createEmployee(request)
        );
    }

    /**
     * 更新数字员工。
     *
     * @param id      员工ID
     * @param request 请求体
     * @param http    请求上下文
     * @return 数字员工
     * @author 余炘洋
     * @date 2026-02-28
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN:DIGITAL_EMPLOYEE:WRITE')")
    public ApiResponse<DigitalEmployeeView> update(@PathVariable Long id,
                                                   @RequestBody DigitalEmployeeUpdateRequest request,
                                                   HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "DIGITAL_EMPLOYEE_UPDATE",
                "DIGITAL_EMPLOYEE",
                request,
                http,
                () -> digitalEmployeeService.updateEmployee(id, request)
        );
    }

    /**
     * 删除数字员工。
     *
     * @param id   员工ID
     * @param http 请求上下文
     * @return 删除结果
     * @author 余炘洋
     * @date 2026-02-28
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN:DIGITAL_EMPLOYEE:WRITE')")
    public ApiResponse<String> delete(@PathVariable Long id, HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.executeVoid(
                "DIGITAL_EMPLOYEE_DELETE",
                "DIGITAL_EMPLOYEE",
                "id=" + id,
                http,
                () -> digitalEmployeeService.deleteEmployee(id),
                "数字员工已删除"
        );
    }

    /**
     * 运行数字员工。
     *
     * @param id      员工ID
     * @param request 执行请求
     * @param http    请求上下文
     * @return 执行结果
     * @author 余炘洋
     * @date 2026-02-28
     */
    @PostMapping("/{id}/run")
    @PreAuthorize("hasAuthority('ADMIN:DIGITAL_EMPLOYEE:RUN')")
    public ApiResponse<DigitalEmployeeRunResult> run(@PathVariable Long id,
                                                     @RequestBody(required = false) DigitalEmployeeRunRequest request,
                                                     HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "DIGITAL_EMPLOYEE_RUN",
                "DIGITAL_EMPLOYEE",
                request,
                http,
                () -> digitalEmployeeService.run(id, request)
        );
    }

    /**
     * 查询数字员工运行日志。
     *
     * @param tenantId      租户ID
     * @param employeeId    员工ID
     * @param success       是否成功
     * @param triggerSource 触发来源
     * @param startTime     开始时间
     * @param endTime       结束时间
     * @param page          页码
     * @param size          每页大小
     * @return 日志分页
     * @author 余炘洋
     * @date 2026-02-28
     */
    @GetMapping("/runs")
    @PreAuthorize("hasAuthority('ADMIN:DIGITAL_EMPLOYEE:READ')")
    public ApiResponse<PageResult<DigitalEmployeeRunLogView>> logs(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false) String triggerSource,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        DigitalEmployeeRunLogQuery query = new DigitalEmployeeRunLogQuery();
        query.setTenantId(tenantId);
        query.setEmployeeId(employeeId);
        query.setSuccess(success);
        query.setTriggerSource(triggerSource);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        return ApiResponse.success(digitalEmployeeService.listRunLogs(query, page, size));
    }

    /**
     * 查询数字员工运营概览。
     *
     * @param tenantId    租户ID
     * @param windowHours 窗口小时
     * @return 运营概览
     * @author 余炘洋
     * @date 2026-02-28
     */
    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('ADMIN:DIGITAL_EMPLOYEE:READ')")
    public ApiResponse<DigitalEmployeeOverviewView> overview(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) Integer windowHours
    ) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(digitalEmployeeService.overview(tenantId, windowHours));
    }

    private boolean isSysAdmin() {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        return com.lingsql.rbac.security.SecureTenantResolver.isSysAdmin(session);
    }
}

