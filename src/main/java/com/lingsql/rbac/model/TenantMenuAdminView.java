package com.lingsql.rbac.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 租户菜单覆盖治理视图。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Data
public class TenantMenuAdminView {

    private Long id;

    private Long overrideId;

    private String tenantId;

    private String menuCode;

    private String menuName;

    private Long parentId;

    private String parentMenuCode;

    private String path;

    private String component;

    private String icon;

    private Integer sort;

    private String permCode;

    private Integer visible;

    private Integer status;

    private List<String> orgTypeCodes = new ArrayList<>();

    private List<String> orgUnitCodes = new ArrayList<>();

    private List<String> orgTags = new ArrayList<>();

    private List<String> requiredRelationTypes = new ArrayList<>();

    private boolean overrideActive;

    private Integer baseVisible;

    private Integer baseStatus;

    private List<String> baseOrgTypeCodes = new ArrayList<>();

    private List<String> baseOrgUnitCodes = new ArrayList<>();

    private List<String> baseOrgTags = new ArrayList<>();

    private List<String> baseRequiredRelationTypes = new ArrayList<>();

    private Integer overrideVisible;

    private Integer overrideStatus;

    private List<String> overrideOrgTypeCodes = new ArrayList<>();

    private List<String> overrideOrgUnitCodes = new ArrayList<>();

    private List<String> overrideOrgTags = new ArrayList<>();

    private List<String> overrideRequiredRelationTypes = new ArrayList<>();

    private List<TenantMenuAdminView> children = new ArrayList<>();
}
