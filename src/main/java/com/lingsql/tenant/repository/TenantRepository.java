package com.lingsql.tenant.repository;

import com.lingsql.tenant.entity.TenantInfo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

/**
 * 租户仓库。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
public interface TenantRepository extends JpaRepository<TenantInfo, Long> {

    /**
     * 按删除状态查询租户列表。
     *
     * @param isDeleted 删除标记
     * @return 租户列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    List<TenantInfo> findByIsDeleted(Integer isDeleted);

    /**
     * 按ID与删除状态查询租户。
     *
     * @param id        租户ID
     * @param isDeleted 删除标记
     * @return 租户信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    Optional<TenantInfo> findByIdAndIsDeleted(Long id, Integer isDeleted);

    /**
     * 按租户ID与删除状态查询租户。
     *
     * @param tenantId  租户ID
     * @param isDeleted 删除标记
     * @return 租户信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    Optional<TenantInfo> findByTenantIdAndIsDeleted(String tenantId, Integer isDeleted);

    /**
     * 按租户ID与删除状态查询租户并加锁。
     *
     * @param tenantId  租户ID
     * @param isDeleted 删除标记
     * @return 租户信息
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TenantInfo> findByTenantIdAndIsDeletedForUpdate(String tenantId, Integer isDeleted);

    /**
     * 判断租户ID是否存在。
     *
     * @param tenantId  租户ID
     * @param isDeleted 删除标记
     * @return 是否存在
     * @author 余炘洋
     * @date 2026-01-26
     */
    boolean existsByTenantIdAndIsDeleted(String tenantId, Integer isDeleted);

    /**
     * 统计活跃租户数量。
     *
     * @param isDeleted 删除标记
     * @param status    状态
     * @return 数量
     * @author 余炘洋
     * @date 2026-01-27
     */
    long countByIsDeletedAndStatus(Integer isDeleted, Integer status);
}
