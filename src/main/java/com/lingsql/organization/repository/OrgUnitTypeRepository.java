package com.lingsql.organization.repository;

import com.lingsql.organization.entity.OrgUnitType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 组织类型仓库。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
public interface OrgUnitTypeRepository extends JpaRepository<OrgUnitType, Long> {

    /**
     * 按租户查询组织类型。
     *
     * @param tenantId 租户ID
     * @return 类型列表
     * @author 余炘洋
     * @date 2026-04-11
     */
    List<OrgUnitType> findByTenantIdOrderBySortAscIdAsc(String tenantId);

    /**
     * 按租户与编码查询组织类型。
     *
     * @param tenantId 租户ID
     * @param typeCode 类型编码
     * @return 类型实体
     * @author 余炘洋
     * @date 2026-04-11
     */
    Optional<OrgUnitType> findByTenantIdAndTypeCode(String tenantId, String typeCode);

    /**
     * 判断同租户下组织类型编码是否已存在。
     *
     * @param tenantId 租户ID
     * @param typeCode 类型编码
     * @return 是否存在
     * @author 余炘洋
     * @date 2026-04-11
     */
    boolean existsByTenantIdAndTypeCode(String tenantId, String typeCode);
}
