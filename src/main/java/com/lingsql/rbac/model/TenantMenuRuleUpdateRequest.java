package com.lingsql.rbac.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 租户菜单覆盖更新请求。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Data
public class TenantMenuRuleUpdateRequest {

    private String tenantId;

    private Integer visible;

    private Integer status;

    private List<String> orgTypeCodes = new ArrayList<>();

    private List<String> orgUnitCodes = new ArrayList<>();

    private List<String> orgTags = new ArrayList<>();

    private List<String> requiredRelationTypes = new ArrayList<>();
}
