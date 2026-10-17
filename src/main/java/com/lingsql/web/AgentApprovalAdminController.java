package com.lingsql.web;

import com.lingsql.agent.entity.AgentApprovalNode;
import com.lingsql.agent.model.AgentApprovalDecisionCommand;
import com.lingsql.agent.model.AgentApprovalResumeView;
import com.lingsql.agent.model.AgentApprovalView;
import com.lingsql.agent.orchestration.AgentResumeCommand;
import com.lingsql.agent.orchestration.AgentRunResult;
import com.lingsql.agent.orchestration.IControlledAgentOrchestrator;
import com.lingsql.agent.service.AgentApprovalService;
import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.security.SecureTenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 人工审批管理接口：查询审批节点、裁决与审批通过后的续跑。
 *
 * <p>续跑只能从人工审批节点重新进入，且必须已经裁决为通过；不允许从安全查询模块之后恢复。</p>
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
@RestController
@RequestMapping("/api/v1/admin/agent-approvals")
@RequiredArgsConstructor
public class AgentApprovalAdminController {

    private final AgentApprovalService approvalService;
    private final IControlledAgentOrchestrator agentOrchestrator;
    private final AuditOperationExecutor auditOperationExecutor;

    /**
     * 查询审批节点列表。
     *
     * @param tenantId 租户标识
     * @param status   审批状态
     * @return 审批节点列表
     * @author 余炘洋
     * @date 2026-10-17
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN:APPROVAL:READ')")
    public ApiResponse<List<AgentApprovalView>> list(@RequestParam(required = false) String tenantId,
                                                     @RequestParam(required = false) String status) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(approvalService.list(tenantId, status).stream().map(this::toView).toList());
    }

    /**
     * 审批决策（通过或驳回）。
     *
     * @param id      审批节点主键
     * @param request 决策请求
     * @param http    请求上下文
     * @return 裁决后的审批节点
     * @author 余炘洋
     * @date 2026-10-17
     */
    @PostMapping("/{id}/decide")
    @PreAuthorize("hasAuthority('ADMIN:APPROVAL:WRITE')")
    public ApiResponse<AgentApprovalView> decide(@PathVariable Long id,
                                                 @Valid @RequestBody AgentApprovalDecisionCommand request,
                                                 HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "AGENT_APPROVAL_DECIDE",
                "AGENT_APPROVAL",
                request,
                http,
                () -> toView(approvalService.decide(
                        id,
                        Boolean.TRUE.equals(request.getApproved()),
                        resolveApproverId(),
                        request.getComment()
                ))
        );
    }

    /**
     * 审批通过后的续跑。
     *
     * @param id   审批节点主键
     * @param http 请求上下文
     * @return 续跑结果
     * @author 余炘洋
     * @date 2026-10-17
     */
    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('ADMIN:APPROVAL:WRITE')")
    public ApiResponse<AgentApprovalResumeView> resume(@PathVariable Long id, HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "AGENT_APPROVAL_RESUME",
                "AGENT_APPROVAL",
                "id=" + id,
                http,
                () -> {
                    AgentApprovalNode node = approvalService.require(id);
                    AgentRunResult runResult = agentOrchestrator.resume(new AgentResumeCommand(
                            node.getTenantId(),
                            node.getExecutionId(),
                            resolveApproverId()
                    ));
                    return new AgentApprovalResumeView(
                            node.getId(),
                            node.getExecutionId(),
                            runResult.status().name(),
                            runResult.answer(),
                            runResult.sql(),
                            runResult.durationMs()
                    );
                }
        );
    }

    private AgentApprovalView toView(AgentApprovalNode node) {
        return new AgentApprovalView(
                node.getId(),
                node.getTenantId(),
                node.getExecutionId(),
                node.getStage(),
                node.getStatus(),
                node.getApproverId(),
                node.getComment(),
                node.getCreatedAt(),
                node.getDecidedAt()
        );
    }

    private String resolveApproverId() {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        return session == null || session.getUserId() == null ? null : String.valueOf(session.getUserId());
    }

    private boolean isSysAdmin() {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        return SecureTenantResolver.isSysAdmin(session);
    }
}
