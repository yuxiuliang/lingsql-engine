package com.lingsql.rbac.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单规则快照。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Data
public class MenuRuleSnapshotView {

    /**
     * 是否可见。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private Integer visible;

    /**
     * 状态。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private Integer status;

    /**
     * 组织类型列表。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private List<String> orgTypeCodes = new ArrayList<>();

    /**
     * 组织编码列表。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private List<String> orgUnitCodes = new ArrayList<>();

    /**
     * 组织标签列表。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private List<String> orgTags = new ArrayList<>();

    /**
     * 关系语义列表。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private List<String> requiredRelationTypes = new ArrayList<>();
}
