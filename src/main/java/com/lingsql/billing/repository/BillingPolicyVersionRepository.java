package com.lingsql.billing.repository;

import com.lingsql.billing.entity.BillingPolicyVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

/**
 * 计费策略版本仓库。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
public interface BillingPolicyVersionRepository extends JpaRepository<BillingPolicyVersion, Long> {

    /**
     * 查询最近生效的版本。
     *
     * @param status 状态
     * @return 版本
     * @author 余炘洋
     * @date 2026-01-27
     */
    Optional<BillingPolicyVersion> findFirstByStatusOrderByEffectiveTimeDesc(Integer status);

    /**
     * 按版本号查询。
     *
     * @param versionCode 版本号
     * @return 版本
     * @author 余炘洋
     * @date 2026-01-27
     */
    Optional<BillingPolicyVersion> findByVersionCode(String versionCode);

    /**
     * 查询指定状态的版本列表。
     *
     * @param status 状态
     * @return 版本列表
     * @author 余炘洋
     * @date 2026-01-27
     */
    List<BillingPolicyVersion> findByStatus(Integer status);
}
