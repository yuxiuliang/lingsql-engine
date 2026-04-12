package com.lingsql.rbac.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据权限命中预览结果。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Data
public class DataScopePreviewView {

    /**
     * 生效数据权限。
     */
    private DataScopeRule effectiveDataScope;

    /**
     * 生效来源。
     */
    private String effectiveSource;

    /**
     * 摘要说明。
     */
    private String summary;

    /**
     * 详细说明。
     */
    private String description;

    /**
     * 直属组织编码。
     */
    private List<String> directOrgCodes = new ArrayList<>();

    /**
     * 直属组织及下级编码。
     */
    private List<String> directOrgSubtreeCodes = new ArrayList<>();

    /**
     * 关系直属范围。
     */
    private Map<String, List<String>> relationDirectScopes = new LinkedHashMap<>();

    /**
     * 关系子树范围。
     */
    private Map<String, List<String>> relationSubtreeScopes = new LinkedHashMap<>();

    /**
     * 命中组织总数。
     */
    private int matchedOrgCount;

    /**
     * 命中组织是否被截断。
     */
    private boolean matchedOrgTruncated;

    /**
     * 命中组织列表。
     */
    private List<DataScopePreviewOrgView> matchedOrgs = new ArrayList<>();

    /**
     * 角色候选列表。
     */
    private List<DataScopePreviewRoleCandidateView> roleCandidates = new ArrayList<>();

    /**
     * 告警信息。
     */
    private List<String> warnings = new ArrayList<>();

    /**
     * 受影响角色数。
     */
    private int affectedRoleCount;

    /**
     * 受影响用户数。
     */
    private int affectedUserCount;

    /**
     * 影响范围摘要。
     */
    private String impactSummary;

    /**
     * 基线对比差异。
     */
    private DataScopePreviewDiffView diff;
}
