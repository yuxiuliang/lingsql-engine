package com.lingsql.organization.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 组织关系路由候选视图。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Data
public class OrgRelationRouteCandidateView {

    /**
     * 关系ID。
     */
    private Long relationId;

    /**
     * 关系名称。
     */
    private String relationName;

    /**
     * 关系类型。
     */
    private String relationType;

    /**
     * 目标组织ID。
     */
    private Long targetOrgUnitId;

    /**
     * 目标组织编码。
     */
    private String targetOrgUnitCode;

    /**
     * 目标组织名称。
     */
    private String targetOrgUnitName;

    /**
     * 目标负责人ID。
     */
    private Long targetLeaderUserId;

    /**
     * 目标负责人名称。
     */
    private String targetLeaderName;

    /**
     * 排序值。
     */
    private Integer sort;

    /**
     * 路由范围节点数。
     */
    private Integer scopeUnitCount;

    /**
     * 路由范围编码列表。
     */
    private List<String> scopeUnitCodes = new ArrayList<>();

    /**
     * 路由范围说明。
     */
    private String routeScopeSummary;
}
