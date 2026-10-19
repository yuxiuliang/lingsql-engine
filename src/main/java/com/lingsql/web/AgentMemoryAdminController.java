package com.lingsql.web;

import com.lingsql.agent.memory.AgentMemoryService;
import com.lingsql.agent.memory.MemoryCandidateService;
import com.lingsql.agent.memory.MemorySourceType;
import com.lingsql.agent.memory.MemoryStatus;
import com.lingsql.agent.memory.MemoryTier;
import com.lingsql.agent.memory.model.AgentMemoryAdminSaveRequest;
import com.lingsql.agent.memory.model.AgentMemoryCorrectRequest;
import com.lingsql.agent.memory.model.AgentMemoryExportView;
import com.lingsql.agent.memory.model.AgentMemoryRejectRequest;
import com.lingsql.agent.memory.model.AgentMemoryView;
import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.security.SecureTenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 长期记忆治理接口：租户范围内的记忆查询、写入、纠正、撤销、候选审核与彻底清除，全部进入审计。
 *
 * @author 余炘洋
 * @date 2026-10-18
 */
@RestController
@RequestMapping("/api/v1/admin/agent-memories")
@RequiredArgsConstructor
public class AgentMemoryAdminController {

    private final AgentMemoryService agentMemoryService;

    private final MemoryCandidateService memoryCandidateService;

    private final AuditOperationExecutor auditOperationExecutor;

    /**
     * 查询租户内长期记忆。
     *
     * @param tenantId 租户ID，必须显式指定
     * @param userId   用户ID
     * @param tier     记忆层级
     * @param status   记忆状态
     * @return 记忆列表
     * @author 余炘洋
     * @date 2026-10-18
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN:MEMORY:READ')")
    public ApiResponse<List<AgentMemoryView>> list(
            @RequestParam String tenantId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String tier,
            @RequestParam(required = false) String status
    ) {
        String secureTenantId = SecureTenantResolver.resolveSecureTenantId(tenantId);
        List<AgentMemoryView> views = agentMemoryService
                .listForAdmin(secureTenantId, userId, resolveTier(tier, false), resolveStatus(status))
                .stream()
                .map(AgentMemoryView::from)
                .collect(Collectors.toList());
        return ApiResponse.success(views);
    }

    /**
     * 写入租户级程序记忆或用户级永久记忆。
     *
     * @param request 请求体
     * @param http    请求上下文
     * @return 记忆详情
     * @author 余炘洋
     * @date 2026-10-18
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN:MEMORY:WRITE')")
    public ApiResponse<AgentMemoryView> create(@Valid @RequestBody AgentMemoryAdminSaveRequest request,
                                              HttpServletRequest http) {
        String secureTenantId = SecureTenantResolver.resolveSecureTenantId(request.getTenantId());
        MemoryTier tier = resolveTier(request.getTier(), true);
        String operator = currentOperator();
        return auditOperationExecutor.execute(
                "AGENT_MEMORY_CREATE",
                "AGENT_MEMORY",
                request,
                http,
                () -> AgentMemoryView.from(agentMemoryService.remember(
                        secureTenantId,
                        request.getUserId(),
                        tier,
                        MemorySourceType.ADMIN_APPROVED,
                        request.getFactText(),
                        request.getRetentionDays(),
                        operator
                ))
        );
    }

    /**
     * 纠正租户内记忆，旧事实失效并生成新版本。
     *
     * @param id       记忆主键
     * @param tenantId 租户ID
     * @param request  请求体
     * @param http     请求上下文
     * @return 新版本记忆
     * @author 余炘洋
     * @date 2026-10-18
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN:MEMORY:WRITE')")
    public ApiResponse<AgentMemoryView> correct(@PathVariable Long id,
                                               @RequestParam(required = false) String tenantId,
                                               @Valid @RequestBody AgentMemoryCorrectRequest request,
                                               HttpServletRequest http) {
        String secureTenantId = SecureTenantResolver.resolveSecureTenantId(tenantId);
        String operator = currentOperator();
        return auditOperationExecutor.execute(
                "AGENT_MEMORY_CORRECT",
                "AGENT_MEMORY",
                request,
                http,
                () -> AgentMemoryView.from(agentMemoryService.correctByAdmin(
                        secureTenantId,
                        id,
                        request.getFactText(),
                        request.getRetentionDays(),
                        operator
                ))
        );
    }

    /**
     * 撤销租户内记忆。
     *
     * @param id       记忆主键
     * @param tenantId 租户ID
     * @param http     请求上下文
     * @return 撤销后的记忆
     * @author 余炘洋
     * @date 2026-10-18
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN:MEMORY:WRITE')")
    public ApiResponse<AgentMemoryView> revoke(@PathVariable Long id,
                                              @RequestParam(required = false) String tenantId,
                                              HttpServletRequest http) {
        String secureTenantId = SecureTenantResolver.resolveSecureTenantId(tenantId);
        return auditOperationExecutor.execute(
                "AGENT_MEMORY_REVOKE",
                "AGENT_MEMORY",
                "id=" + id,
                http,
                () -> AgentMemoryView.from(agentMemoryService.revokeByAdmin(secureTenantId, id))
        );
    }

    /**
     * 查询租户内待确认的记忆候选。
     *
     * @param tenantId 租户ID，必须显式指定
     * @param userId   用户ID，可为空
     * @return 候选列表
     * @author 余炘洋
     * @date 2026-10-19
     */
    @GetMapping("/candidates")
    @PreAuthorize("hasAuthority('ADMIN:MEMORY:READ')")
    public ApiResponse<List<AgentMemoryView>> listCandidates(
            @RequestParam String tenantId,
            @RequestParam(required = false) Long userId
    ) {
        String secureTenantId = SecureTenantResolver.resolveSecureTenantId(tenantId);
        List<AgentMemoryView> views = memoryCandidateService
                .listCandidatesForAdmin(secureTenantId, userId)
                .stream()
                .map(AgentMemoryView::from)
                .collect(Collectors.toList());
        return ApiResponse.success(views);
    }

    /**
     * 批准租户内记忆候选：生成生效记忆，候选记录标记为已采纳。
     *
     * @param id       候选主键
     * @param tenantId 租户ID，必须显式指定
     * @param http     请求上下文
     * @return 生成的生效记忆
     * @author 余炘洋
     * @date 2026-10-19
     */
    @PostMapping("/candidates/{id}/approve")
    @PreAuthorize("hasAuthority('ADMIN:MEMORY:WRITE')")
    public ApiResponse<AgentMemoryView> approveCandidate(@PathVariable Long id,
                                                        @RequestParam String tenantId,
                                                        HttpServletRequest http) {
        String secureTenantId = SecureTenantResolver.resolveSecureTenantId(tenantId);
        String operator = currentOperator();
        return auditOperationExecutor.execute(
                "AGENT_MEMORY_CANDIDATE_APPROVE",
                "AGENT_MEMORY",
                "candidateId=" + id,
                http,
                () -> AgentMemoryView.from(
                        memoryCandidateService.approveByAdmin(secureTenantId, id, operator))
        );
    }

    /**
     * 拒绝租户内记忆候选：只更新候选状态，不生成任何生效记忆。
     *
     * @param id       候选主键
     * @param tenantId 租户ID，必须显式指定
     * @param request  请求体，拒绝原因可为空
     * @param http     请求上下文
     * @return 候选记录
     * @author 余炘洋
     * @date 2026-10-19
     */
    @PostMapping("/candidates/{id}/reject")
    @PreAuthorize("hasAuthority('ADMIN:MEMORY:WRITE')")
    public ApiResponse<AgentMemoryView> rejectCandidate(@PathVariable Long id,
                                                       @RequestParam String tenantId,
                                                       @Valid @RequestBody(required = false)
                                                       AgentMemoryRejectRequest request,
                                                       HttpServletRequest http) {
        String secureTenantId = SecureTenantResolver.resolveSecureTenantId(tenantId);
        String operator = currentOperator();
        String reason = request == null ? null : request.getReason();
        return auditOperationExecutor.execute(
                "AGENT_MEMORY_CANDIDATE_REJECT",
                "AGENT_MEMORY",
                "candidateId=" + id,
                http,
                () -> AgentMemoryView.from(
                        memoryCandidateService.rejectByAdmin(secureTenantId, id, reason, operator))
        );
    }

    /**
     * 彻底清除租户内记忆：删除记录本身，只留审计摘要，不记录正文。
     *
     * @param id       记忆主键
     * @param tenantId 租户ID，必须显式指定
     * @param http     请求上下文
     * @return 空响应
     * @author 余炘洋
     * @date 2026-10-19
     */
    @DeleteMapping("/{id}/purge")
    @PreAuthorize("hasAuthority('ADMIN:MEMORY:WRITE')")
    public ApiResponse<Void> purge(@PathVariable Long id,
                                  @RequestParam String tenantId,
                                  HttpServletRequest http) {
        String secureTenantId = SecureTenantResolver.resolveSecureTenantId(tenantId);
        return auditOperationExecutor.execute(
                "AGENT_MEMORY_PURGE",
                "AGENT_MEMORY",
                "id=" + id,
                http,
                () -> {
                    agentMemoryService.purgeByAdmin(secureTenantId, id);
                    return null;
                }
        );
    }

    /**
     * 导出租户内记忆与候选：内容与来源、时间、保留期一致，用于隐私自查与留档。
     *
     * @param tenantId 租户ID，必须显式指定
     * @param userId   用户ID，可为空
     * @return 导出视图
     * @author 余炘洋
     * @date 2026-10-19
     */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('ADMIN:MEMORY:READ')")
    public ApiResponse<AgentMemoryExportView> export(
            @RequestParam String tenantId,
            @RequestParam(required = false) Long userId
    ) {
        String secureTenantId = SecureTenantResolver.resolveSecureTenantId(tenantId);
        return ApiResponse.success(agentMemoryService.exportForAdmin(secureTenantId, userId));
    }

    /**
     * 解析记忆层级，未知编码直接拒绝。
     *
     * @param code     层级编码
     * @param required 是否必填
     * @return 记忆层级
     * @author 余炘洋
     * @date 2026-10-18
     */
    private MemoryTier resolveTier(String code, boolean required) {
        if (!StringUtils.hasText(code)) {
            if (required) {
                throw new BizException(ErrorCode.INVALID_PARAM, "记忆层级不能为空");
            }
            return null;
        }
        MemoryTier tier = MemoryTier.of(code);
        if (tier == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "记忆层级不合法");
        }
        return tier;
    }

    /**
     * 解析记忆状态，未知编码直接拒绝。
     *
     * @param code 状态编码
     * @return 记忆状态
     * @author 余炘洋
     * @date 2026-10-18
     */
    private MemoryStatus resolveStatus(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        MemoryStatus status = MemoryStatus.of(code);
        if (status == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "记忆状态不合法");
        }
        return status;
    }

    /**
     * 解析当前操作人。
     *
     * @return 操作人标识
     * @author 余炘洋
     * @date 2026-10-18
     */
    private String currentOperator() {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        if (session == null) {
            return null;
        }
        if (StringUtils.hasText(session.getUsername())) {
            return session.getUsername();
        }
        return session.getUserId() == null ? null : String.valueOf(session.getUserId());
    }
}
