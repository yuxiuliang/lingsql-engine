package com.lingsql.rbac.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据权限变更前后差异视图。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Data
public class DataScopePreviewDiffView {

    /**
     * 基线摘要。
     */
    private String baselineSummary;

    /**
     * 调整后摘要。
     */
    private String targetSummary;

    /**
     * 基线命中组织数。
     */
    private int baselineMatchedOrgCount;

    /**
     * 调整后命中组织数。
     */
    private int targetMatchedOrgCount;

    /**
     * 新增命中组织数。
     */
    private int addedOrgCount;

    /**
     * 移除命中组织数。
     */
    private int removedOrgCount;

    /**
     * 保持不变的命中组织数。
     */
    private int unchangedOrgCount;

    /**
     * 新增命中组织列表。
     */
    private List<DataScopePreviewOrgView> addedOrgs = new ArrayList<>();

    /**
     * 移除命中组织列表。
     */
    private List<DataScopePreviewOrgView> removedOrgs = new ArrayList<>();
}
