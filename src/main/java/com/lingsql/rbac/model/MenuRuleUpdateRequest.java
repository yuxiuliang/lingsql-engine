package com.lingsql.rbac.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单组织适配规则更新请求。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Data
public class MenuRuleUpdateRequest {

    /**
     * 适配的组织类型编码列表。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> orgTypeCodes = new ArrayList<>();

    /**
     * 适配的组织编码列表。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> orgUnitCodes = new ArrayList<>();

    /**
     * 适配的组织标签列表。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> orgTags = new ArrayList<>();

    /**
     * 要求具备的组织关系类型列表。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private List<String> requiredRelationTypes = new ArrayList<>();
}
