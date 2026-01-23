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
    List<ChatHistory> findTop50ByTenantIdOrderByCreateTimeDesc(String tenantId);

    /**
     * 分页查询租户历史记录。
     *
     * @param tenantId 租户标识
     * @param pageable 分页参数
     * @return 分页结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    Page<ChatHistory> findByTenantIdOrderByCreateTimeDesc(String tenantId, Pageable pageable);

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
    Page<ChatHistory> findByTenantIdAndQuestionContainingOrderByCreateTimeDesc(String tenantId, String keyword, Pageable pageable);

    /**
     * 根据租户与ID查询历史记录。
     *
     * @param id 记录ID
     * @param tenantId 租户标识
     * @return 历史记录
     * @author 余炘洋
     * @date 2026-01-23
     */
    Optional<ChatHistory> findByIdAndTenantId(Long id, String tenantId);

    /**
     * 根据租户与ID删除历史记录。
     *
     * @param id 记录ID
     * @param tenantId 租户标识
     * @author 余炘洋
     * @date 2026-01-23
     */
    void deleteByIdAndTenantId(Long id, String tenantId);
}
