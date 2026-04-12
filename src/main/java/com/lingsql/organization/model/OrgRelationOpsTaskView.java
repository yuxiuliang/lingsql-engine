package com.lingsql.organization.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 组织关系消费任务视图。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Data
public class OrgRelationOpsTaskView {

    /**
     * 关系ID。
     */
    private Long relationId;

    /**
     * 关系类型。
     */
    private String relationType;

    /**
     * 关系名称。
     */
    private String relationName;

    /**
     * 源组织名称。
     */
    private String sourceOrgUnitName;

    /**
     * 目标组织名称。
     */
    private String targetOrgUnitName;

    /**
     * 源组织编码。
     */
    private String sourceOrgUnitCode;

    /**
     * 目标组织编码。
     */
    private String targetOrgUnitCode;

    /**
     * 推荐消费场景编码。
     */
    private String sceneCode;

    /**
     * 推荐消费场景名称。
     */
    private String sceneName;

    /**
     * 任务优先级：P0/P1/P2。
     */
    private String priority;

    /**
     * 就绪状态：READY/PENDING/BLOCKED。
     */
    private String readinessStatus;

    /**
     * 建议动作。
     */
    private String suggestedAction;

    /**
     * 触发原因。
     */
    private String triggerReason;

    /**
     * 路由范围说明。
     */
    private String routeScopeSummary;

    /**
     * 路由范围节点数。
     */
    private Integer scopeUnitCount;

    /**
     * 目标负责人ID。
     */
    private Long targetLeaderUserId;

    /**
     * 目标负责人名称。
     */
    private String targetLeaderName;

    /**
     * 当前缺口说明。
     */
    private List<String> missingCapabilities = new ArrayList<>();
}
