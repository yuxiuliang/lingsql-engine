package com.lingsql.agent.repository;

import com.lingsql.agent.entity.AgentMemoryFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Agent 长期记忆仓储。
 *
 * @author 余炘洋
 * @date 2026-10-18
 */
@Repository
public interface AgentMemoryFactRepository extends JpaRepository<AgentMemoryFact, Long> {

    /**
     * 查询指定范围内的记忆，用于纠正和撤销前的位置定位。
     *
     * @param id       记忆主键
     * @param tenantId 租户标识
     * @return 记忆
     */
    Optional<AgentMemoryFact> findByIdAndTenantId(Long id, String tenantId);

    /**
     * 查询租户级同层同文生效记忆，用于写入去重。
     *
     * @param tenantId 租户标识
     * @param tier     记忆层级
     * @param factText 记忆正文
     * @param status   状态
     * @return 记忆
     */
    Optional<AgentMemoryFact> findFirstByTenantIdAndUserIdIsNullAndTierAndFactTextAndStatus(
            String tenantId, String tier, String factText, String status);

    /**
     * 查询用户级同层同文生效记忆，用于写入去重。
     *
     * @param tenantId 租户标识
     * @param userId   用户标识
     * @param tier     记忆层级
     * @param factText 记忆正文
     * @param status   状态
     * @return 记忆
     */
    Optional<AgentMemoryFact> findFirstByTenantIdAndUserIdAndTierAndFactTextAndStatus(
            String tenantId, Long userId, String tier, String factText, String status);

    /**
     * 查询租户级指定层级生效记忆。
     *
     * @param tenantId 租户标识
     * @param tier     记忆层级
     * @param status   状态
     * @return 记忆列表
     */
    List<AgentMemoryFact> findTop50ByTenantIdAndUserIdIsNullAndTierAndStatusOrderByUpdatedAtDesc(
            String tenantId, String tier, String status);

    /**
     * 查询用户级指定状态记忆。
     *
     * @param tenantId 租户标识
     * @param userId   用户标识
     * @param status   状态
     * @return 记忆列表
     */
    List<AgentMemoryFact> findTop50ByTenantIdAndUserIdAndStatusOrderByUpdatedAtDesc(
            String tenantId, Long userId, String status);

    /**
     * 查询租户内最近更新的记忆，用于管理端治理列表。
     *
     * @param tenantId 租户标识
     * @return 记忆列表
     */
    List<AgentMemoryFact> findTop200ByTenantIdOrderByUpdatedAtDesc(String tenantId);
}
