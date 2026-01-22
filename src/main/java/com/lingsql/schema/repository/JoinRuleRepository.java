package com.lingsql.schema.repository;

import com.lingsql.schema.entity.JoinRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Join 规则仓库。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
public interface JoinRuleRepository extends JpaRepository<JoinRule, Long> {

    /**
     * 查询租户级别 Join 规则。
     *
     * @param tenantId 租户标识
     * @param status   状态
     * @return Join 规则列表
     * @author 余炘洋
     * @date 2026-01-22
     */
    List<JoinRule> findByTenantIdAndStatus(String tenantId, Integer status);

    /**
     * 查询全局 Join 规则。
     *
     * @param status 状态
     * @return Join 规则列表
     * @author 余炘洋
     * @date 2026-01-22
     */
    List<JoinRule> findByTenantIdIsNullAndStatus(Integer status);
}
