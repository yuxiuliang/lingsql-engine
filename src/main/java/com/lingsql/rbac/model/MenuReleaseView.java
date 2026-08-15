package com.lingsql.rbac.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单治理发布单视图。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Data
public class MenuReleaseView {

    private Long id;

    private String tenantId;

    private String releaseType;

    private String releaseTitle;

    private Long menuId;

    private String menuCode;

    private String menuName;

    private String baselineRuleSummary;

    private String targetRuleSummary;

    private String impactSummary;

    private List<String> changedDimensions = new ArrayList<>();

    private Integer addedMenuCount;

    private Integer removedMenuCount;

    private String changeRemark;

    private String releaseStatus;

    private Long requestedByUserId;

    private String requestedByName;

    private Long reviewedByUserId;

    private String reviewedByName;

    private String reviewComment;

    private LocalDateTime reviewedTime;

    private Long appliedByUserId;

    private String appliedByName;

    private LocalDateTime appliedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
