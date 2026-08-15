package com.lingsql.organization.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 租户组织治理字典视图。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Data
public class OrgGovernanceCatalogView {

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String tenantId;

    /**
     * 组织类型候选列表。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private List<OrgGovernanceOptionView> orgTypeOptions = new ArrayList<>();

    /**
     * 组织节点候选列表。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private List<OrgGovernanceOptionView> orgUnitOptions = new ArrayList<>();

    /**
     * 组织标签候选列表。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private List<OrgGovernanceOptionView> orgTagOptions = new ArrayList<>();

    /**
     * 关系类型候选列表。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private List<OrgGovernanceOptionView> relationTypeOptions = new ArrayList<>();
}
