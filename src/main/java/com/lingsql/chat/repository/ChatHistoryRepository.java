package com.lingsql.chat.repository;

import com.lingsql.chat.entity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * 聊天历史记录仓库。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    /**
     * 查询租户最近记录。
     *
     * @param tenantId 租户标识
     * @return 历史记录
     * @author 余炘洋
     * @date 2026-01-23
     */
    List<ChatHistory> findTop50ByTenantIdAndUserIdOrderByCreateTimeDesc(String tenantId, Long userId);

    /**
     * 分页查询租户历史记录。
     *
     * @param tenantId 租户标识
     * @param pageable 分页参数
     * @return 分页结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    Page<ChatHistory> findByTenantIdAndUserIdOrderByCreateTimeDesc(String tenantId, Long userId, Pageable pageable);

    /**
     * 分页查询租户历史记录（关键词过滤）。
     *
     * @param tenantId 租户标识
     * @param keyword  关键词
     * @param pageable 分页参数
     * @return 分页结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    Page<ChatHistory> findByTenantIdAndUserIdAndQuestionContainingOrderByCreateTimeDesc(String tenantId, Long userId, String keyword, Pageable pageable);

    /**
     * 根据租户与ID查询历史记录。
     *
     * @param id 记录ID
     * @param tenantId 租户标识
     * @return 历史记录
     * @author 余炘洋
     * @date 2026-01-23
     */
    Optional<ChatHistory> findByIdAndTenantIdAndUserId(Long id, String tenantId, Long userId);

    /**
     * 根据租户与ID删除历史记录。
     *
     * @param id 记录ID
     * @param tenantId 租户标识
     * @author 余炘洋
     * @date 2026-01-23
     */
    void deleteByIdAndTenantIdAndUserId(Long id, String tenantId, Long userId);

    /**
     * 统计指定时间之前的聊天历史数量。
     *
     * @param createTime 截止时间
     * @return 数量
     * @author 余炘洋
     * @date 2026-02-27
     */
    long countByCreateTimeBefore(java.time.LocalDateTime createTime);

    /**
     * 删除指定时间之前的聊天历史。
     *
     * @param createTime 截止时间
     * @return 删除数量
     * @author 余炘洋
     * @date 2026-02-27
     */
    long deleteByCreateTimeBefore(java.time.LocalDateTime createTime);
}
