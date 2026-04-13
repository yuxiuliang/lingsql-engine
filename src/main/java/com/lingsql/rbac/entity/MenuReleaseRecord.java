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
 * 菜单治理发布单实体。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_menu_release_record")
public class MenuReleaseRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "release_type", nullable = false)
    private String releaseType;

    @Column(name = "release_title", nullable = false)
    private String releaseTitle;

    @Column(name = "menu_id")
    private Long menuId;

    @Column(name = "menu_code", nullable = false)
    private String menuCode;

    @Column(name = "menu_name", nullable = false)
    private String menuName;

    @Column(name = "baseline_rule_summary", columnDefinition = "TEXT")
    private String baselineRuleSummary;

    @Column(name = "target_rule_summary", columnDefinition = "TEXT")
    private String targetRuleSummary;

    @Column(name = "impact_summary", columnDefinition = "TEXT")
    private String impactSummary;

    @Column(name = "changed_dimensions_json", columnDefinition = "TEXT")
    private String changedDimensionsJson;

    @Column(name = "added_menu_count")
    private Integer addedMenuCount;

    @Column(name = "removed_menu_count")
    private Integer removedMenuCount;

    @Column(name = "impact_request_json", columnDefinition = "TEXT")
    private String impactRequestJson;

    @Column(name = "impact_snapshot_json", columnDefinition = "TEXT")
    private String impactSnapshotJson;

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
