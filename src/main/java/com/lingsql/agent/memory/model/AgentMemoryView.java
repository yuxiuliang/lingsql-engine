package com.lingsql.agent.memory.model;

import com.lingsql.agent.entity.AgentMemoryFact;
import com.lingsql.agent.memory.MemorySensitivity;
import com.lingsql.agent.memory.MemorySourceType;
import com.lingsql.agent.memory.MemoryStatus;
import com.lingsql.agent.memory.MemoryTier;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 长期记忆视图。
 *
 * @author 余炘洋
 * @date 2026-10-18
 */
@Data
public class AgentMemoryView {

    /**
     * 记忆主键。
     */
    private Long id;

    /**
     * 租户标识。
     */
    private String tenantId;

    /**
     * 用户标识，为空表示租户级记忆。
     */
    private Long userId;

    /**
     * 记忆层级编码。
     */
    private String tier;

    /**
     * 记忆层级中文名。
     */
    private String tierLabel;

    /**
     * 记忆正文。
     */
    private String factText;

    /**
     * 来源编码。
     */
    private String sourceType;

    /**
     * 来源中文名。
     */
    private String sourceLabel;

    /**
     * 状态编码。
     */
    private String status;

    /**
     * 状态中文名。
     */
    private String statusLabel;

    /**
     * 置信度分值。
     */
    private Integer confidence;

    /**
     * 版本号。
     */
    private Integer version;

    /**
     * 被取代的旧记忆主键。
     */
    private Long supersedesId;

    /**
     * 写入人标识。
     */
    private String createdBy;

    /**
     * 候选来源标识（会话或请求标识）。
     */
    private String sourceRef;

    /**
     * 敏感分类编码。
     */
    private String sensitivity;

    /**
     * 敏感分类中文名。
     */
    private String sensitivityLabel;

    /**
     * 候选复核人标识。
     */
    private String reviewedBy;

    /**
     * 候选复核时间。
     */
    private LocalDateTime reviewedAt;

    /**
     * 采纳后生成的生效记忆主键。
     */
    private Long approvedMemoryId;

    /**
     * 候选拒绝原因。
     */
    private String rejectReason;

    /**
     * 保留期到期时间。
     */
    private LocalDateTime expiresAt;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 由实体构建视图。
     *
     * @param fact 记忆实体
     * @return 记忆视图
     * @author 余炘洋
     * @date 2026-10-18
     */
    public static AgentMemoryView from(AgentMemoryFact fact) {
        AgentMemoryView view = new AgentMemoryView();
        view.setId(fact.getId());
        view.setTenantId(fact.getTenantId());
        view.setUserId(fact.getUserId());
        view.setTier(fact.getTier());
        MemoryTier tier = MemoryTier.of(fact.getTier());
        view.setTierLabel(tier == null ? fact.getTier() : tier.label());
        view.setFactText(fact.getFactText());
        view.setSourceType(fact.getSourceType());
        MemorySourceType sourceType = MemorySourceType.of(fact.getSourceType());
        view.setSourceLabel(sourceType == null ? fact.getSourceType() : sourceType.label());
        view.setStatus(fact.getStatus());
        MemoryStatus status = MemoryStatus.of(fact.getStatus());
        view.setStatusLabel(status == null ? fact.getStatus() : status.label());
        view.setConfidence(fact.getConfidence());
        view.setVersion(fact.getVersion());
        view.setSupersedesId(fact.getSupersedesId());
        view.setCreatedBy(fact.getCreatedBy());
        view.setSourceRef(fact.getSourceRef());
        view.setSensitivity(fact.getSensitivity());
        MemorySensitivity sensitivity = MemorySensitivity.of(fact.getSensitivity());
        view.setSensitivityLabel(sensitivity == null ? fact.getSensitivity() : sensitivity.label());
        view.setReviewedBy(fact.getReviewedBy());
        view.setReviewedAt(fact.getReviewedAt());
        view.setApprovedMemoryId(fact.getApprovedMemoryId());
        view.setRejectReason(fact.getRejectReason());
        view.setExpiresAt(fact.getExpiresAt());
        view.setCreatedAt(fact.getCreatedAt());
        view.setUpdatedAt(fact.getUpdatedAt());
        return view;
    }
}
