package com.lingsql.organization.repository;

import com.lingsql.organization.entity.OrgUnitRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 组织关系仓库。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
public interface OrgUnitRelationRepository extends JpaRepository<OrgUnitRelation, Long> {

    /**
     * 按租户查询组织关系。
     *
     * @param tenantId 租户ID
     * @return 关系列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    List<OrgUnitRelation> findByTenantId(String tenantId);

    /**
     * 按租户、状态、关系类型与源组织节点列表查询组织关系。
     *
     * @param tenantId        租户ID
     * @param status          状态
     * @param relationType    关系类型
     * @param sourceOrgUnitIds 源组织节点ID列表
     * @return 关系列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    List<OrgUnitRelation> findByTenantIdAndStatusAndRelationTypeAndSourceOrgUnitIdIn(String tenantId,
                                                                                      Integer status,
                                                                                      String relationType,
                                                                                      List<Long> sourceOrgUnitIds);

    /**
     * 按租户、状态与源组织节点列表查询组织关系。
     *
     * @param tenantId         租户ID
     * @param status           状态
     * @param sourceOrgUnitIds 源组织节点ID列表
     * @return 关系列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    List<OrgUnitRelation> findByTenantIdAndStatusAndSourceOrgUnitIdIn(String tenantId,
                                                                      Integer status,
                                                                      List<Long> sourceOrgUnitIds);

    /**
     * 判断同租户下关系是否已存在。
     *
     * @param tenantId         租户ID
     * @param sourceOrgUnitId  源组织节点ID
     * @param targetOrgUnitId  目标组织节点ID
     * @param relationType     关系类型
     * @return 是否存在
     * @author 余炘洋
     * @date 2026-04-11
     */
    boolean existsByTenantIdAndSourceOrgUnitIdAndTargetOrgUnitIdAndRelationType(String tenantId,
                                                                                Long sourceOrgUnitId,
                                                                                Long targetOrgUnitId,
                                                                                String relationType);

    /**
     * 按主键与租户查询组织关系。
     *
     * @param id       主键ID
     * @param tenantId 租户ID
     * @return 关系实体
     * @author 余炘洋
     * @date 2026-04-11
     */
    Optional<OrgUnitRelation> findByIdAndTenantId(Long id, String tenantId);
}
