package com.lingsql.billing.repository;

import com.lingsql.billing.entity.BillingPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 套餐仓库。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
public interface BillingPlanRepository extends JpaRepository<BillingPlan, Long> {

    /**
     * 按删除状态查询套餐。
     *
     * @param isDeleted 删除标记
     * @return 套餐列表
     * @author 余炘洋
     * @date 2026-01-29
     */
    List<BillingPlan> findByIsDeleted(Integer isDeleted);

    /**
     * 按ID与删除状态查询套餐。
     *
     * @param id        套餐ID
     * @param isDeleted 删除标记
     * @return 套餐信息
     * @author 余炘洋
     * @date 2026-01-29
     */
    Optional<BillingPlan> findByIdAndIsDeleted(Long id, Integer isDeleted);

    /**
     * 判断套餐编码是否存在。
     *
     * @param planCode  套餐编码
     * @param isDeleted 删除标记
     * @return 是否存在
     * @author 余炘洋
     * @date 2026-01-29
     */
    boolean existsByPlanCodeAndIsDeleted(String planCode, Integer isDeleted);

    /**
     * 判断套餐名称是否存在。
     *
     * @param planName  套餐名称
     * @param isDeleted 删除标记
     * @return 是否存在
     * @author 余炘洋
     * @date 2026-01-29
     */
    boolean existsByPlanNameAndIsDeleted(String planName, Integer isDeleted);

    /**
     * 按套餐名称与删除状态查询套餐。
     *
     * @param planName  套餐名称
     * @param isDeleted 删除标记
     * @return 套餐信息
     * @author 余炘洋
     * @date 2026-04-10
     */
    Optional<BillingPlan> findByPlanNameAndIsDeleted(String planName, Integer isDeleted);
}
