package com.lingsql.organization.model;

import lombok.Data;

/**
 * 组织模板视图。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
@Data
public class OrgTemplateView {

    /**
     * 模板编码。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String templateCode;

    /**
     * 模板名称。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String templateName;

    /**
     * 适用行业。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String industry;

    /**
     * 模板描述。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private String description;

    /**
     * 组织类型数量。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Integer typeCount;

    /**
     * 组织节点数量。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Integer unitCount;

    /**
     * 组织关系数量。
     *
     * @author 余炘洋
     * @date 2026-04-11
     */
    private Integer relationCount;

    /**
     * 默认根组织名称。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String defaultRootUnitName;
}
