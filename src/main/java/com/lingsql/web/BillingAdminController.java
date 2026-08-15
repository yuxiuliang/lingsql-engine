package com.lingsql.web;

import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.billing.BillingAdminService;
import com.lingsql.billing.BillingCommercialReadinessService;
import com.lingsql.billing.BillingReconcileLogService;
import com.lingsql.billing.acceptance.BillingAcceptanceReceipt;
import com.lingsql.billing.acceptance.BillingAcceptanceSessionReceipt;
import com.lingsql.billing.acceptance.IBillingAcceptanceSession;
import com.lingsql.billing.entity.BillingPolicyVersion;
import com.lingsql.billing.entity.TenantBillingOverride;
import com.lingsql.billing.invocation.IPhysicalBillingResolution;
import com.lingsql.billing.invocation.PhysicalBillingResolutionCommand;
import com.lingsql.billing.invocation.PhysicalBillingResolutionReceipt;
import com.lingsql.billing.model.BillingCommercialReadinessView;
import com.lingsql.billing.model.BillingAcceptanceCloseRequest;
import com.lingsql.billing.model.BillingAcceptanceOpenRequest;
import com.lingsql.billing.model.BillingInvocationResolutionRequest;
import com.lingsql.billing.model.BillingLedgerEvidenceView;
import com.lingsql.billing.model.BillingPolicyVersionRequest;
import com.lingsql.billing.model.BillingReconcileLogQuery;
import com.lingsql.billing.model.BillingReconcileLogView;
import com.lingsql.billing.model.BillingReconcileResolveRequest;
import com.lingsql.billing.model.TenantBillingOverrideRequest;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.common.result.PageResult;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 计费配置管理接口。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@RestController
@RequestMapping("/api/v1/admin/billing")
@RequiredArgsConstructor
@Slf4j
public class BillingAdminController {

    private static final String OVERRIDE_QUERY_ERROR_MESSAGE = "查询租户计费覆盖失败，请稍后重试";

    private final BillingAdminService billingAdminService;
    private final AuditOperationExecutor auditOperationExecutor;
    private final BillingReconcileLogService billingReconcileLogService;
    private final BillingCommercialReadinessService billingCommercialReadinessService;
    private final IPhysicalBillingResolution physicalBillingResolution;
    private final IBillingAcceptanceSession billingAcceptanceSession;

    /**
     * 查询计费策略版本列表。
     *
     * @return 策略列表
     * @author 余炘洋
     * @date 2026-01-27
     */
    @GetMapping("/policies")
    @PreAuthorize("hasAuthority('ADMIN:BILLING:READ')")
    public ApiResponse<List<BillingPolicyVersion>> listPolicies() {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(billingAdminService.listPolicyVersions());
    }

    /**
     * 创建计费策略版本。
     *
     * @param request 创建请求
     * @param http    请求
     * @return 创建结果
     * @author 余炘洋
     * @date 2026-01-27
     */
    @PostMapping("/policies")
    @PreAuthorize("hasAuthority('ADMIN:BILLING:WRITE')")
    public ApiResponse<BillingPolicyVersion> createPolicy(@RequestBody BillingPolicyVersionRequest request, HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "BILLING_POLICY_CREATE",
                "BILLING_POLICY",
                request,
                http,
                () -> billingAdminService.createPolicyVersion(request)
        );
    }

    /**
     * 更新计费策略版本。
     *
     * @param id      版本ID
     * @param request 更新请求
     * @param http    请求
     * @return 更新结果
     * @author 余炘洋
     * @date 2026-01-27
     */
    @PutMapping("/policies/{id}")
    @PreAuthorize("hasAuthority('ADMIN:BILLING:WRITE')")
    public ApiResponse<BillingPolicyVersion> updatePolicy(@PathVariable Long id,
                                                          @RequestBody BillingPolicyVersionRequest request,
                                                          HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "BILLING_POLICY_UPDATE",
                "BILLING_POLICY",
                request,
                http,
                () -> billingAdminService.updatePolicyVersion(id, request)
        );
    }

    /**
     * 立即生效计费策略版本。
     *
     * @param id   版本ID
     * @param http 请求
     * @return 生效结果
     * @author 余炘洋
     * @date 2026-01-27
     */
    @PostMapping("/policies/{id}/activate")
    @PreAuthorize("hasAuthority('ADMIN:BILLING:WRITE')")
    public ApiResponse<BillingPolicyVersion> activatePolicy(@PathVariable Long id, HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "BILLING_POLICY_ACTIVATE",
                "BILLING_POLICY",
                "id=" + id,
                http,
                () -> billingAdminService.activatePolicyVersion(id)
        );
    }

    /**
     * 查询租户计费覆盖列表。
     *
     * @return 覆盖列表
     * @author 余炘洋
     * @date 2026-01-27
     */
    @GetMapping("/overrides")
    @PreAuthorize("hasAuthority('ADMIN:BILLING:READ')")
    public ApiResponse<List<TenantBillingOverride>> listOverrides() {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(billingAdminService.listOverrides());
    }

    /**
     * 查询租户计费覆盖。
     *
     * @param tenantId 租户ID
     * @return 覆盖配置
     * @author 余炘洋
     * @date 2026-01-27
     */
    @GetMapping("/overrides/{tenantId}")
    @PreAuthorize("hasAuthority('ADMIN:BILLING:READ')")
    public ApiResponse<TenantBillingOverride> getOverride(@PathVariable String tenantId) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        try {
            return ApiResponse.success(billingAdminService.getOverride(tenantId));
        } catch (Exception ex) {
            log.warn("查询租户计费覆盖失败: tenantId={}", tenantId, ex);
            return ApiResponse.error(OVERRIDE_QUERY_ERROR_MESSAGE);
        }
    }

    /**
     * 新增或更新租户计费覆盖。
     *
     * @param tenantId 租户ID
     * @param request  覆盖请求
     * @param http     请求
     * @return 覆盖结果
     * @author 余炘洋
     * @date 2026-01-27
     */
    @PutMapping("/overrides/{tenantId}")
    @PreAuthorize("hasAuthority('ADMIN:BILLING:WRITE')")
    public ApiResponse<TenantBillingOverride> upsertOverride(@PathVariable String tenantId,
                                                             @RequestBody TenantBillingOverrideRequest request,
                                                             HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "BILLING_OVERRIDE_UPSERT",
                "BILLING_OVERRIDE",
                request,
                http,
                () -> billingAdminService.upsertOverride(tenantId, request)
        );
    }

    /**
     * 查询计费对账日志。
     *
     * @param tenantId  租户ID
     * @param requestId 请求ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param page      页码
     * @param size      每页大小
     * @return 对账日志分页
     * @author 余炘洋
     * @date 2026-01-29
     */
    @GetMapping("/reconcile/logs")
    @PreAuthorize("hasAuthority('ADMIN:BILLING:READ')")
    public ApiResponse<PageResult<BillingReconcileLogView>> listReconcileLogs(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        BillingReconcileLogQuery query = new BillingReconcileLogQuery();
        query.setTenantId(tenantId);
        query.setRequestId(requestId);
        query.setStatus(status);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        return ApiResponse.success(billingReconcileLogService.query(query, page, size));
    }

    /**
     * 标记对账日志已处理。
     *
     * @param id      日志ID
     * @param request 请求
     * @return 处理后的日志
     * @author 余炘洋
     * @date 2026-01-29
     */
    @PutMapping("/reconcile/logs/{id}/resolve")
    @PreAuthorize("hasAuthority('ADMIN:BILLING:WRITE')")
    public ApiResponse<BillingReconcileLogView> resolveReconcileLog(@PathVariable Long id,
                                                                    @RequestBody(required = false) BillingReconcileResolveRequest request) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        RbacUserSession session = RbacSecurityUtils.currentSession();
        String note = request == null ? null : request.getNote();
        return ApiResponse.success(billingReconcileLogService.resolve(id, session == null ? null : session.getUserId(), note));
    }

    /**
     * 获取计费商用验收快照。
     *
     * @param windowHours 统计窗口（小时）
     * @return 商用验收结果
     * @author 余炘洋
     * @date 2026-02-27
     */
    @GetMapping("/readiness/snapshot")
    @PreAuthorize("hasAuthority('ADMIN:BILLING:READ')")
    public ApiResponse<BillingCommercialReadinessView> readinessSnapshot(
            @RequestParam(required = false) Integer windowHours
    ) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(billingCommercialReadinessService.snapshot(windowHours));
    }

    /**
     * 查询真实请求对应的计费账本验收证据。
     *
     * @param tenantId 租户ID
     * @param requestIds 请求ID集合
     * @return 账本验收证据
     */
    @GetMapping("/ledger/evidence")
    @PreAuthorize("hasAuthority('ADMIN:BILLING:READ')")
    public ApiResponse<BillingLedgerEvidenceView> ledgerEvidence(
            @RequestParam String tenantId,
            @RequestParam List<String> requestIds
    ) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(billingAdminService.ledgerEvidence(tenantId, requestIds));
    }

    /**
     * 依据供应商证据收口 UNKNOWN 物理调用的资金状态。
     *
     * @param invocationId 物理调用ID
     * @param request 裁决请求
     * @param http HTTP 请求
     * @return 裁决回执
     */
    @PutMapping("/invocations/{invocationId}/resolve")
    @PreAuthorize("hasAuthority('ADMIN:BILLING:WRITE')")
    public ApiResponse<PhysicalBillingResolutionReceipt> resolveInvocation(
            @PathVariable String invocationId,
            @RequestBody BillingInvocationResolutionRequest request,
            HttpServletRequest http
    ) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        String operator = currentOperator();
        PhysicalBillingResolutionCommand command = new PhysicalBillingResolutionCommand(
                invocationId,
                operator,
                request == null ? null : request.evidenceHash(),
                request == null ? null : request.providerRequestId(),
                request == null ? null : request.resolutionType(),
                request == null ? null : request.actualTokens()
        );
        return auditOperationExecutor.executeCommittedResult(
                "BILLING_INVOCATION_RESOLVE",
                "BILLING_INVOCATION",
                request,
                http,
                () -> physicalBillingResolution.resolve(command),
                receipt -> receipt
        );
    }

    /**
     * 冻结计费验收的 MySQL 余额与账本起始水位。
     *
     * @param request 开启请求
     * @param http HTTP 请求
     * @return 会话基线回执
     */
    @PostMapping("/acceptance/sessions")
    @PreAuthorize("hasAuthority('ADMIN:BILLING:WRITE')")
    public ApiResponse<BillingAcceptanceSessionReceipt> openAcceptanceSession(
            @RequestBody BillingAcceptanceOpenRequest request,
            HttpServletRequest http
    ) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        String operator = currentOperator();
        return auditOperationExecutor.executeCommittedResult(
                "BILLING_ACCEPTANCE_OPEN",
                "BILLING_ACCEPTANCE_SESSION",
                request,
                http,
                () -> billingAcceptanceSession.open(request == null ? null : request.tenantId(), operator),
                receipt -> receipt
        );
    }

    /**
     * 对精确请求集合完成计费验收并生成不可变回执。
     *
     * @param sessionId 验收会话ID
     * @param request 关闭请求
     * @param http HTTP 请求
     * @return 不可变验收回执
     */
    @PostMapping("/acceptance/sessions/{sessionId}/close")
    @PreAuthorize("hasAuthority('ADMIN:BILLING:WRITE')")
    public ApiResponse<BillingAcceptanceReceipt> closeAcceptanceSession(
            @PathVariable String sessionId,
            @RequestBody BillingAcceptanceCloseRequest request,
            HttpServletRequest http
    ) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        String operator = currentOperator();
        return auditOperationExecutor.executeCommittedResult(
                "BILLING_ACCEPTANCE_CLOSE",
                "BILLING_ACCEPTANCE_RECEIPT",
                request,
                http,
                () -> billingAcceptanceSession.close(
                        sessionId,
                        request == null ? null : request.requestIds(),
                        operator
                ),
                receipt -> receipt
        );
    }

    private String currentOperator() {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        return session == null || session.getUserId() == null ? null : String.valueOf(session.getUserId());
    }

    private boolean isSysAdmin() {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        return com.lingsql.rbac.security.SecureTenantResolver.isSysAdmin(session);
    }
}
