package com.lingsql.chat.repository;

import com.lingsql.chat.entity.ChatHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    @Query(
            value = """
                    select ch.*
                    from t_lingsql_engine_chat_history ch
                    join (
                        select max(id) as latest_id
                        from t_lingsql_engine_chat_history
                        where tenant_id = :tenantId
                          and user_id = :userId
                          and (:keyword is null or :keyword = '' or coalesce(conversation_title, question) like concat('%', :keyword, '%'))
                        group by coalesce(conversation_id, concat('legacy-', id))
                    ) latest on latest.latest_id = ch.id
                    order by ch.create_time desc, ch.id desc
                    """,
            countQuery = """
                    select count(distinct coalesce(conversation_id, concat('legacy-', id)))
                    from t_lingsql_engine_chat_history
                    where tenant_id = :tenantId
                      and user_id = :userId
                      and (:keyword is null or :keyword = '' or coalesce(conversation_title, question) like concat('%', :keyword, '%'))
                    """,
            nativeQuery = true
    )
    Page<ChatHistory> findConversationSummaries(
            @Param("tenantId") String tenantId,
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

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
    Optional<ChatHistory> findTopByTenantIdAndUserIdAndConversationIdOrderByIdDesc(String tenantId, Long userId, String conversationId);

    /**
     * 查询单个会话的完整消息列表。
     *
     * @param tenantId       租户标识
     * @param userId         用户ID
     * @param conversationId 会话标识
     * @return 会话消息列表
     * @author 余炘洋
     * @date 2026-04-10
     */
    List<ChatHistory> findByTenantIdAndUserIdAndConversationIdOrderByCreateTimeAscIdAsc(
            String tenantId,
            Long userId,
            String conversationId
    );

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
     * 批量更新会话标题。
     *
     * @param tenantId       租户标识
     * @param userId         用户ID
     * @param conversationId 会话标识
     * @param title          新标题
     * @return 更新数量
     * @author 余炘洋
     * @date 2026-04-10
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ChatHistory h
            set h.conversationTitle = :title
            where h.tenantId = :tenantId
              and h.userId = :userId
              and h.conversationId = :conversationId
            """)
    int updateConversationTitle(
            @Param("tenantId") String tenantId,
            @Param("userId") Long userId,
            @Param("conversationId") String conversationId,
            @Param("title") String title
    );

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
     * 按会话删除全部聊天历史。
     *
     * @param tenantId       租户标识
     * @param userId         用户ID
     * @param conversationId 会话标识
     * @return 删除数量
     * @author 余炘洋
     * @date 2026-04-10
     */
    long deleteByTenantIdAndUserIdAndConversationId(String tenantId, Long userId, String conversationId);

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
