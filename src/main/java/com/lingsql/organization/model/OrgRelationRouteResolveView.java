package com.lingsql.organization.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 组织关系路由解析结果。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Data
public class OrgRelationRouteResolveView {

    /**
     * 租户ID。
     */
    private String tenantId;

    /**
     * 源组织ID。
     */
    private Long sourceOrgUnitId;

    /**
     * 源组织编码。
     */
    private String sourceOrgUnitCode;

    /**
     * 源组织名称。
     */
    private String sourceOrgUnitName;

    /**
     * 关系类型。
     */
    private String relationType;

    /**
     * 是否匹配到路由。
     */
    private boolean matched;

    /**
     * 是否存在多候选歧义。
     */
    private boolean ambiguous;

    /**
     * 候选数量。
     */
    private int candidateCount;

    /**
     * 主路由关系ID。
     */
    private Long primaryRelationId;

    /**
     * 主路由关系名称。
     */
    private String primaryRelationName;

    /**
     * 主路由目标组织ID。
     */
    private Long primaryTargetOrgUnitId;

    /**
     * 主路由目标组织编码。
     */
    private String primaryTargetOrgUnitCode;

    /**
     * 主路由目标组织名称。
     */
    private String primaryTargetOrgUnitName;

    /**
     * 主路由负责人ID。
     */
    private Long primaryLeaderUserId;

    /**
     * 主路由负责人名称。
     */
    private String primaryLeaderName;

    /**
     * 主路由范围节点数。
     */
    private Integer primaryScopeUnitCount;

    /**
     * 主路由范围编码列表。
     */
    private List<String> primaryScopeUnitCodes = new ArrayList<>();

    /**
     * 主路由范围说明。
     */
    private String primaryRouteScopeSummary;

    /**
     * 预警说明。
     */
    private List<String> warnings = new ArrayList<>();

    /**
     * 候选列表。
     */
    private List<OrgRelationRouteCandidateView> candidates = new ArrayList<>();
}
