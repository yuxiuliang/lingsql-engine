package com.lingsql.rbac.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单规则影响分析结果。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Data
public class MenuImpactPreviewView {

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String tenantId;

    /**
     * 菜单编码。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String menuCode;

    /**
     * 菜单名称。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String menuName;

    /**
     * 是否租户覆盖模式。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private boolean tenantOverrideMode;

    /**
     * 基线菜单数。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private Integer baselineMenuCount;

    /**
     * 目标菜单数。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private Integer targetMenuCount;

    /**
     * 新增菜单数。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private Integer addedMenuCount;

    /**
     * 移除菜单数。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private Integer removedMenuCount;

    /**
     * 基线是否命中当前菜单。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private boolean baselineMatched;

    /**
     * 目标是否命中当前菜单。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private boolean targetMatched;

    /**
     * 基线规则摘要。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String baselineRuleSummary;

    /**
     * 目标规则摘要。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String targetRuleSummary;

    /**
     * 变更维度列表。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private List<String> changedDimensions = new ArrayList<>();

    /**
     * 影响摘要。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String impactSummary;

    /**
     * 新增菜单清单。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private List<MenuImpactDiffItemView> addedMenus = new ArrayList<>();

    /**
     * 移除菜单清单。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private List<MenuImpactDiffItemView> removedMenus = new ArrayList<>();
}
