package com.lingsql.rbac.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色数据权限发布单实体。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_role_scope_release_record")
public class RoleScopeReleaseRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "release_title", nullable = false)
    private String releaseTitle;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "role_code", nullable = false)
    private String roleCode;

    @Column(name = "role_name", nullable = false)
    private String roleName;

    @Column(name = "baseline_summary", columnDefinition = "TEXT")
    private String baselineSummary;

    @Column(name = "target_summary", columnDefinition = "TEXT")
    private String targetSummary;

    @Column(name = "impact_summary", columnDefinition = "TEXT")
    private String impactSummary;

    @Column(name = "matched_org_count")
    private Integer matchedOrgCount;

    @Column(name = "affected_user_count")
    private Integer affectedUserCount;

    @Column(name = "added_org_count")
    private Integer addedOrgCount;

    @Column(name = "removed_org_count")
    private Integer removedOrgCount;

    @Column(name = "preview_request_json", columnDefinition = "TEXT")
    private String previewRequestJson;

    @Column(name = "preview_snapshot_json", columnDefinition = "TEXT")
    private String previewSnapshotJson;

    @Column(name = "change_remark", columnDefinition = "TEXT")
    private String changeRemark;

    @Column(name = "release_status", nullable = false)
    private String releaseStatus;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Column(name = "requested_by_name")
    private String requestedByName;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "reviewed_by_name")
    private String reviewedByName;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "reviewed_time")
    private LocalDateTime reviewedTime;

    @Column(name = "applied_by_user_id")
    private Long appliedByUserId;

    @Column(name = "applied_by_name")
    private String appliedByName;

    @Column(name = "applied_time")
    private LocalDateTime appliedTime;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
