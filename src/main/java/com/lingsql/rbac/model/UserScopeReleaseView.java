package com.lingsql.rbac.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户数据权限发布单视图。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Data
public class UserScopeReleaseView {

    private Long id;

    private String tenantId;

    private Long userId;

    private String username;

    private String nickname;

    private String releaseTitle;

    private String baselineSummary;

    private String targetSummary;

    private String impactSummary;

    private Integer matchedOrgCount;

    private Integer affectedRoleCount;

    private Integer affectedUserCount;

    private Integer addedOrgCount;

    private Integer removedOrgCount;

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
