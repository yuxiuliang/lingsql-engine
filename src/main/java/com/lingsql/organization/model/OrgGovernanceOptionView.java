package com.lingsql.organization.model;

import lombok.Data;

/**
 * 组织治理选项视图。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Data
public class OrgGovernanceOptionView {

    /**
     * 选项编码。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String code;

    /**
     * 选项名称。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String name;

    /**
     * 补充描述。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private String description;
}
