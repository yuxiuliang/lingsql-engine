package com.lingsql.organization.repository;

import com.lingsql.organization.entity.OrgRelationExecutionTimeline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 组织关系执行轨迹仓库。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
public interface OrgRelationExecutionTimelineRepository extends JpaRepository<OrgRelationExecutionTimeline, Long> {

    /**
     * 按租户和执行项查询轨迹。
     *
     * @param tenantId     租户ID
     * @param executionId  执行项ID
     * @return 轨迹列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    List<OrgRelationExecutionTimeline> findByTenantIdAndExecutionIdOrderByCreateTimeDescIdDesc(String tenantId, Long executionId);
}
