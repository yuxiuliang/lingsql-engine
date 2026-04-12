package com.lingsql.organization.repository;

import com.lingsql.organization.entity.OrgRelationExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 组织关系执行台账仓库。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
public interface OrgRelationExecutionRepository extends JpaRepository<OrgRelationExecution, Long> {

    /**
     * 按租户倒序查询执行台账。
     *
     * @param tenantId 租户ID
     * @return 台账列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    List<OrgRelationExecution> findByTenantIdOrderByCreateTimeDescIdDesc(String tenantId);

    /**
     * 按租户与状态倒序查询执行台账。
     *
     * @param tenantId 租户ID
     * @param status   状态
     * @return 台账列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    List<OrgRelationExecution> findByTenantIdAndStatusOrderByCreateTimeDescIdDesc(String tenantId, String status);

    /**
     * 按租户与关系类型倒序查询执行台账。
     *
     * @param tenantId     租户ID
     * @param relationType 关系类型
     * @return 台账列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    List<OrgRelationExecution> findByTenantIdAndRelationTypeOrderByCreateTimeDescIdDesc(String tenantId, String relationType);

    /**
     * 按租户、状态和关系类型倒序查询执行台账。
     *
     * @param tenantId     租户ID
     * @param status       状态
     * @param relationType 关系类型
     * @return 台账列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    List<OrgRelationExecution> findByTenantIdAndStatusAndRelationTypeOrderByCreateTimeDescIdDesc(String tenantId,
                                                                                                   String status,
                                                                                                   String relationType);

    /**
     * 按主键与租户查询执行台账。
     *
     * @param id       主键ID
     * @param tenantId 租户ID
     * @return 台账实体
     * @author 余炘洋
     * @date 2026-04-12
     */
    Optional<OrgRelationExecution> findByIdAndTenantId(Long id, String tenantId);

    /**
     * 按状态集合查询存在截止时间的执行台账。
     *
     * @param statuses 状态集合
     * @return 执行台账列表
     * @author 余炘洋
     * @date 2026-04-12
     */
    List<OrgRelationExecution> findByStatusInAndDueTimeIsNotNullOrderByDueTimeAscIdAsc(List<String> statuses);
}
