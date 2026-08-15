package com.lingsql.rbac.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 租户菜单覆盖快照。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Data
public class MenuTenantOverrideSnapshotView {

    /**
     * 是否存在租户覆盖记录。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private boolean overrideActive;

    /**
     * 显示覆盖值。
     *
     * <p>NULL 表示继承全局。</p>
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private Integer visible;

    /**
     * 状态覆盖值。
     *
     * <p>NULL 表示继承全局。</p>
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private Integer status;

    /**
     * 是否显式覆盖组织类型。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private boolean orgTypeCodesSpecified;

    /**
     * 组织类型覆盖值。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private List<String> orgTypeCodes = new ArrayList<>();

    /**
     * 是否显式覆盖组织编码。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private boolean orgUnitCodesSpecified;

    /**
     * 组织编码覆盖值。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private List<String> orgUnitCodes = new ArrayList<>();

    /**
     * 是否显式覆盖组织标签。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private boolean orgTagsSpecified;

    /**
     * 组织标签覆盖值。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private List<String> orgTags = new ArrayList<>();

    /**
     * 是否显式覆盖关系语义。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private boolean requiredRelationTypesSpecified;

    /**
     * 关系语义覆盖值。
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private List<String> requiredRelationTypes = new ArrayList<>();
}
