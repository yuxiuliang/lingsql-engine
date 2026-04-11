package com.lingsql.organization.repository;

import com.lingsql.organization.entity.OrgUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 组织单元仓库。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
public interface OrgUnitRepository extends JpaRepository<OrgUnit, Long> {

    /**
     * 按租户查询全部组织节点。
     *
     * @param tenantId 租户ID
     * @return 组织节点列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    List<OrgUnit> findByTenantId(String tenantId);

    /**
     * 按ID列表查询组织节点。
     *
     * @param ids 主键ID列表
     * @return 组织节点列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    List<OrgUnit> findByIdIn(List<Long> ids);

    /**
     * 判断同租户下组织编码是否已存在。
     *
     * @param tenantId 租户ID
     * @param unitCode 组织编码
     * @return 是否存在
     * @author 余炘洋
     * @date 2026-04-11
     */
    boolean existsByTenantIdAndUnitCode(String tenantId, String unitCode);

    /**
     * 按主键与租户查询组织节点。
     *
     * @param id       主键ID
     * @param tenantId 租户ID
     * @return 组织节点
     * @author 余炘洋
     * @date 2026-04-11
     */
    Optional<OrgUnit> findByIdAndTenantId(Long id, String tenantId);

    /**
     * 判断组织类型是否已被组织节点使用。
     *
     * @param tenantId 租户ID
     * @param typeCode 类型编码
     * @return 是否存在
     * @author 余炘洋
     * @date 2026-04-11
     */
    boolean existsByTenantIdAndTypeCode(String tenantId, String typeCode);

    /**
     * 判断是否存在子节点。
     *
     * @param tenantId 租户ID
     * @param parentId 父节点ID
     * @return 是否存在子节点
     * @author 余炘洋
     * @date 2026-04-11
     */
    boolean existsByTenantIdAndParentId(String tenantId, Long parentId);
}
