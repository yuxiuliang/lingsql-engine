package com.lingsql.agent.service;

import com.lingsql.agent.entity.AgentApprovalNode;
import com.lingsql.agent.orchestration.AgentRunStage;
import com.lingsql.agent.repository.AgentApprovalNodeRepository;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 人工审批服务：记录审批节点并只允许从安全阶段之前恢复。
 *
 * <p>审批节点只能由受控编排发起；裁决只允许对 {@code PENDING} 节点执行一次，续跑由编排模块负责。</p>
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Service
@RequiredArgsConstructor
public class AgentApprovalService {

    /** 待审批。 */
    public static final String STATUS_PENDING = "PENDING";
    /** 审批通过。 */
    public static final String STATUS_APPROVED = "APPROVED";
    /** 审批驳回。 */
    public static final String STATUS_REJECTED = "REJECTED";

    private static final int LIST_LIMIT = 100;
    private static final int MAX_COMMENT_LENGTH = 512;
    private static final List<String> KNOWN_STATUS = List.of(STATUS_PENDING, STATUS_APPROVED, STATUS_REJECTED);

    private final AgentApprovalNodeRepository repository;

    /**
     * 发起人工审批节点。
     *
     * @param tenantId    租户标识
     * @param executionId 执行标识
     * @param stage       恢复点阶段
     * @return 审批节点
     * @author 余炘洋
     * @date 2026-10-17
     */
    public AgentApprovalNode requestApproval(String tenantId, String executionId, AgentRunStage stage) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(executionId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "审批节点缺少租户或执行标识");
        }
        if (stage == null || !stage.isApprovalStage()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "人工审批只能挂在受控审批阶段");
        }
        AgentApprovalNode node = new AgentApprovalNode();
        node.setTenantId(tenantId.trim());
        node.setExecutionId(executionId.trim());
        node.setStage(stage.name());
        node.setStatus(STATUS_PENDING);
        node.setCreatedAt(LocalDateTime.now());
        return repository.save(node);
    }

    /**
     * 按主键读取审批节点。
     *
     * @param id 审批节点主键
     * @return 审批节点
     * @author 余炘洋
     * @date 2026-10-17
     */
    public AgentApprovalNode require(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "审批节点标识不能为空");
        }
        return repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "审批节点不存在"));
    }

    /**
     * 审批决策：只允许对 {@code PENDING} 节点裁决一次。
     *
     * @param id         审批节点主键
     * @param approved   是否通过
     * @param approverId 审批人标识
     * @param comment    审批意见
     * @return 裁决后的审批节点
     * @author 余炘洋
     * @date 2026-10-17
     */
    public AgentApprovalNode decide(Long id, boolean approved, String approverId, String comment) {
        if (!StringUtils.hasText(approverId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "审批人不能为空");
        }
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new BizException(ErrorCode.INVALID_PARAM, "审批意见不能超过512个字符");
        }
        AgentApprovalNode node = require(id);
        if (!STATUS_PENDING.equals(node.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "审批节点已裁决，不可重复决策");
        }
        node.setStatus(approved ? STATUS_APPROVED : STATUS_REJECTED);
        node.setApproverId(approverId.trim());
        node.setDecidedAt(LocalDateTime.now());
        node.setComment(comment);
        return repository.save(node);
    }

    /**
     * 最新审批节点。
     *
     * @param tenantId    租户标识
     * @param executionId 执行标识
     * @return 最新审批节点
     * @author 余炘洋
     * @date 2026-10-05
     */
    public Optional<AgentApprovalNode> latest(String tenantId, String executionId) {
        return repository.findTopByTenantIdAndExecutionIdOrderByCreatedAtDesc(tenantId, executionId);
    }

    /**
     * 审批节点列表：最多返回 100 条，按创建时间倒序。
     *
     * @param tenantId 租户标识，为空时查询全部（仅系统管理员可调用）
     * @param status   审批状态，为空时查询全部
     * @return 审批节点列表
     * @author 余炘洋
     * @date 2026-10-17
     */
    public List<AgentApprovalNode> list(String tenantId, String status) {
        String safeStatus = null;
        if (StringUtils.hasText(status)) {
            safeStatus = status.trim().toUpperCase();
            if (!KNOWN_STATUS.contains(safeStatus)) {
                throw new BizException(ErrorCode.INVALID_PARAM, "审批状态取值非法");
            }
        }
        boolean hasTenant = StringUtils.hasText(tenantId);
        String safeTenantId = hasTenant ? tenantId.trim() : null;
        if (hasTenant && safeStatus != null) {
            return repository.findTop100ByTenantIdAndStatusOrderByCreatedAtDesc(safeTenantId, safeStatus);
        }
        if (hasTenant) {
            return repository.findTop100ByTenantIdOrderByCreatedAtDesc(safeTenantId);
        }
        if (safeStatus != null) {
            return repository.findTop100ByStatusOrderByCreatedAtDesc(safeStatus);
        }
        return repository.findTop100ByOrderByCreatedAtDesc();
    }
}
