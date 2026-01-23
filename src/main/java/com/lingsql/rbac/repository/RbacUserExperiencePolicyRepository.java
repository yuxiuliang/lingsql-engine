package com.lingsql.rbac.repository;

import com.lingsql.rbac.entity.RbacUserExperiencePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 体验账号策略仓库。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
public interface RbacUserExperiencePolicyRepository extends JpaRepository<RbacUserExperiencePolicy, Long> {

    /**
     * 查询用户体验账号策略。
     *
     * @param userId 用户ID
     * @return 策略
     * @author 余炘洋
     * @date 2026-01-23
     */
    Optional<RbacUserExperiencePolicy> findByUserId(Long userId);
}
