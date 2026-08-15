package com.lingsql.tenant.repository;

import com.lingsql.tenant.entity.TenantInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * 查询带到期时间的活跃租户。
     *
     * @param isDeleted 删除标记
     * @param status    状态
     * @return 租户列表
     * @author 余炘洋
     * @date 2026-02-27
     */
    List<TenantInfo> findByIsDeletedAndStatusAndExpireTimeIsNotNull(Integer isDeleted, Integer status);

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
     * 扣减租户余额（原子更新）。
     *
     * @param tenantId  租户ID
     * @param cost      扣费金额
     * @param isDeleted 删除标记
     * @param updateTime 更新时间
     * @return 更新条数
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Modifying
    @Query("""
            UPDATE TenantInfo t
               SET t.quotaBalance = CASE
                                        WHEN t.quotaBalance - :cost < 0 THEN 0
                                        ELSE t.quotaBalance - :cost
                                    END,
                   t.updateTime = :updateTime
             WHERE t.tenantId = :tenantId
               AND t.isDeleted = :isDeleted
            """)
    int decreaseBalance(@Param("tenantId") String tenantId,
                        @Param("cost") long cost,
                        @Param("isDeleted") Integer isDeleted,
                        @Param("updateTime") java.time.LocalDateTime updateTime);

    /**
     * 在余额充足时原子扣减租户余额。
     *
     * @param tenantId 租户ID
     * @param cost 扣费金额
     * @param isDeleted 删除标记
     * @param updateTime 更新时间
     * @return 更新条数，0 表示余额不足或租户不存在
     * @author 余炘洋
     * @date 2026-08-15
     */
    @Modifying
    @Query("""
            UPDATE TenantInfo t
               SET t.quotaBalance = t.quotaBalance - :cost,
                   t.updateTime = :updateTime
             WHERE t.tenantId = :tenantId
               AND t.isDeleted = :isDeleted
               AND t.quotaBalance >= :cost
            """)
    int decreaseBalanceIfSufficient(@Param("tenantId") String tenantId,
                                    @Param("cost") long cost,
                                    @Param("isDeleted") Integer isDeleted,
                                    @Param("updateTime") java.time.LocalDateTime updateTime);

    /**
     * 原子退回租户预留余额。
     *
     * @param tenantId 租户ID
     * @param amount 退回金额
     * @param isDeleted 删除标记
     * @param updateTime 更新时间
     * @return 更新条数
     * @author 余炘洋
     * @date 2026-08-15
     */
    @Modifying
    @Query("""
            UPDATE TenantInfo t
               SET t.quotaBalance = t.quotaBalance + :amount,
                   t.updateTime = :updateTime
             WHERE t.tenantId = :tenantId
               AND t.isDeleted = :isDeleted
            """)
    int increaseBalance(@Param("tenantId") String tenantId,
                        @Param("amount") long amount,
                        @Param("isDeleted") Integer isDeleted,
                        @Param("updateTime") java.time.LocalDateTime updateTime);

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

    /**
     * 按套餐名称统计租户数量。
     *
     * @param planName  套餐名称
     * @param isDeleted 删除标记
     * @return 数量
     * @author 余炘洋
     * @date 2026-01-29
     */
    long countByPlanNameAndIsDeleted(String planName, Integer isDeleted);

    /**
     * 按套餐名称与删除状态查询租户。
     *
     * @param planName  套餐名称
     * @param isDeleted 删除标记
     * @return 租户列表
     * @author 余炘洋
     * @date 2026-04-10
     */
    List<TenantInfo> findByPlanNameAndIsDeleted(String planName, Integer isDeleted);
}
