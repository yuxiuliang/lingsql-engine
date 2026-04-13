package com.lingsql.rbac.model;

import lombok.Data;

/**
 * 菜单规则影响分析请求。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Data
public class MenuImpactPreviewRequest {

    /**
     * 目标租户ID。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String tenantId;

    /**
     * 全局菜单ID。
     *
     * <p>预览全局规则变更时使用。</p>
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private Long menuId;

    /**
     * 菜单编码。
     *
     * <p>预览租户覆盖变更时使用。</p>
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String menuCode;

    /**
     * 待预览的全局规则。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private MenuRuleUpdateRequest globalRule;

    /**
     * 待预览的租户覆盖规则。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private TenantMenuRuleUpdateRequest tenantRule;

    /**
     * 样本上下文。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private MenuPreviewRequest previewContext;
}
