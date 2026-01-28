package com.lingsql.web;

import com.lingsql.audit.model.AuditLogQuery;
import com.lingsql.audit.model.AuditLogView;
import com.lingsql.audit.model.SqlExecuteLogQuery;
import com.lingsql.audit.model.SqlExecuteLogView;
import com.lingsql.audit.service.AuditLogService;
import com.lingsql.audit.service.SqlExecuteLogService;
import com.lingsql.common.pagination.PageParamResolver;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.common.result.PageResult;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.security.SecureTenantResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志查询接口。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final SqlExecuteLogService sqlExecuteLogService;
    private final PageParamResolver pageParamResolver;

    /**
     * 查询管理操作日志。
     *
     * @param tenantId  租户ID
     * @param userId    用户ID
     * @param action    操作动作
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param page      页码
     * @param size      每页大小
     * @return 审计日志分页
     * @author 余炘洋
     * @date 2026-01-26
     */
    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('ADMIN:AUDIT:READ')")
    public ApiResponse<PageResult<AuditLogView>> listAuditLogs(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        AuditLogQuery query = new AuditLogQuery();
        query.setTenantId(sysAdmin ? tenantId : session.getTenantId());
        query.setUserId(userId);
        query.setAction(action);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        return ApiResponse.success(auditLogService.query(query, page, size));
    }

    /**
     * 查询 SQL 执行日志。
     *
     * @param tenantId     租户ID
     * @param userId       用户ID
     * @param question     问题关键词
     * @param requestSource 请求来源
     * @param startTime    开始时间
     * @param endTime      结束时间
     * @param page         页码
     * @param size         每页大小
     * @return SQL 日志分页
     * @author 余炘洋
     * @date 2026-01-26
     */
    @GetMapping("/sql-logs")
    @PreAuthorize("hasAuthority('ADMIN:SQLLOG:READ')")
    public ApiResponse<PageResult<SqlExecuteLogView>> listSqlLogs(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String question,
            @RequestParam(required = false) String requestSource,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        SqlExecuteLogQuery query = new SqlExecuteLogQuery();
        query.setTenantId(sysAdmin ? tenantId : session.getTenantId());
        query.setUserId(userId);
        query.setQuestion(question);
        query.setRequestSource(requestSource);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        return ApiResponse.success(sqlExecuteLogService.query(query, page, size));
    }

    /**
     * 导出 SQL 执行日志（仅超级管理员）。
     *
     * @param tenantId     租户ID
     * @param userId       用户ID
     * @param question     问题关键词
     * @param requestSource 请求来源
     * @param startTime    开始时间
     * @param endTime      结束时间
     * @return SQL 日志列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    @GetMapping("/sql-logs/export")
    @PreAuthorize("hasAuthority('ADMIN:SQLLOG:EXPORT')")
    public ApiResponse<List<SqlExecuteLogView>> exportSqlLogs(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String question,
            @RequestParam(required = false) String requestSource,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime
    ) {
        RbacUserSession session = requireSession();
        if (!SecureTenantResolver.isSysAdmin(session)) {
            return ApiResponse.forbidden("无权限访问");
        }
        SqlExecuteLogQuery query = new SqlExecuteLogQuery();
        query.setTenantId(tenantId);
        query.setUserId(userId);
        query.setQuestion(question);
        query.setRequestSource(requestSource);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        int exportSize = pageParamResolver.resolveExportSize();
        PageResult<SqlExecuteLogView> pageResult = sqlExecuteLogService.queryForExport(query, 1, exportSize);
        return ApiResponse.success(pageResult.getList());
    }

    /**
     * 获取当前会话。
     *
     * @return 会话信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    private RbacUserSession requireSession() {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        if (session == null) {
            throw new BadCredentialsException("未登录或登录已过期");
        }
        return session;
    }
}
