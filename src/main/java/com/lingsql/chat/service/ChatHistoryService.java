package com.lingsql.chat.service;

import tools.jackson.databind.ObjectMapper;
import com.lingsql.chat.entity.ChatHistory;
import com.lingsql.chat.model.ChatHistoryDetailView;
import com.lingsql.chat.model.ChatHistoryMessageView;
import com.lingsql.chat.model.ChatTraceStep;
import com.lingsql.chat.model.ChatHistoryView;
import com.lingsql.chat.repository.ChatHistoryRepository;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.common.pagination.PageParam;
import com.lingsql.common.pagination.PageParamResolver;
import com.lingsql.common.result.PageResult;
import com.lingsql.config.LingSqlProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.Cursor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 聊天历史服务，负责持久化与视图转换。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final LingSqlProperties properties;
    private final PageParamResolver pageParamResolver;
    private final ChatHistoryPayloadCodec chatHistoryPayloadCodec;

    /**
     * 保存历史记录。
     *
     * @param tenantId 租户标识
     * @param question 问题
     * @param answer   答复
     * @param sqlText  SQL 文本
     * @param data     原始数据
     * @return 保存后的记录
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Transactional
    public ChatHistory saveHistory(
            String tenantId,
            Long userId,
            String conversationId,
            String question,
            String answer,
            String sqlText,
            List<Map<String, Object>> data,
            List<ChatTraceStep> trace
    ) {
        ChatHistory history = new ChatHistory();
        history.setTenantId(tenantId);
        history.setUserId(userId);
        history.setConversationId(conversationId);
        history.setConversationTitle(resolveConversationTitle(tenantId, userId, conversationId, question));
        history.setQuestion(question);
        history.setAnswer(answer);
        history.setSqlText(sqlText);
        history.setStatus(sqlText != null && sqlText.startsWith("ERROR:") ? 0 : 1);
        history.setCreateTime(LocalDateTime.now());
        history.setDataJson(chatHistoryPayloadCodec.encode(data, trace));
        ChatHistory saved = chatHistoryRepository.save(history);
        evictListCache(tenantId, userId);
        evictConversationDetailCache(tenantId, userId, saved);
        return saved;
    }

    /**
     * 查询历史记录列表。
     *
     * @param tenantId 租户标识
     * @return 历史记录视图
     * @author 余炘洋
     * @date 2026-01-23
     */
    public PageResult<ChatHistoryView> listHistory(String tenantId, Long userId, String keyword, int page, int size) {
        PageParam param = pageParamResolver.resolve(page, size);
        int safePage = param.page();
        int safeSize = param.size();
        String cacheKey = buildListKey(tenantId, userId, safePage, safeSize, keyword);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            PageResult<ChatHistoryView> cachedResult = fromJsonPage(cached);
            if (cachedResult != null) {
                return cachedResult;
            }
        }
        Pageable pageable = PageRequest.of(safePage - 1, safeSize);
        Page<ChatHistory> historyPage = chatHistoryRepository.findConversationSummaries(
                tenantId,
                userId,
                normalizeKeyword(keyword),
                pageable
        );
        List<ChatHistoryView> views = historyPage.getContent()
                .stream()
                .map(ChatHistoryView::from)
                .toList();
        PageResult<ChatHistoryView> result = new PageResult<>(views, historyPage.getTotalElements(), safePage, safeSize);
        cachePage(tenantId, userId, safePage, safeSize, keyword, result);
        return result;
    }

    /**
     * 查询历史详情。
     *
     * @param tenantId 租户标识
     * @param id       记录ID
     * @return 详情视图
     * @author 余炘洋
     * @date 2026-01-23
     */
    public ChatHistoryDetailView getHistoryDetail(String tenantId, Long userId, Long id) {
        ChatHistory history = chatHistoryRepository.findByIdAndTenantIdAndUserId(id, tenantId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "历史记录不存在"));
        String conversationKey = resolveConversationCacheKey(history);
        String cacheKey = buildDetailKey(tenantId, userId, conversationKey);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            ChatHistoryDetailView cachedView = fromJsonDetail(cached);
            if (cachedView != null) {
                return cachedView;
            }
        }
        ChatHistoryDetailView view = buildDetailView(tenantId, userId, history);
        cacheDetail(tenantId, userId, conversationKey, view);
        return view;
    }

    /**
     * 更新历史记录标题。
     *
     * @param tenantId 租户标识
     * @param id       记录ID
     * @param question 新标题
     * @return 更新后的视图
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Transactional
    public ChatHistoryView renameHistory(String tenantId, Long userId, Long id, String question) {
        if (question == null || question.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "标题不能为空");
        }
        ChatHistory history = chatHistoryRepository.findByIdAndTenantIdAndUserId(id, tenantId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "历史记录不存在"));
        String title = question.trim();
        if (StringUtils.hasText(history.getConversationId())) {
            chatHistoryRepository.updateConversationTitle(tenantId, userId, history.getConversationId(), title);
        } else {
            history.setConversationTitle(title);
            chatHistoryRepository.save(history);
        }
        ChatHistory saved = chatHistoryRepository.findByIdAndTenantIdAndUserId(id, tenantId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "历史记录不存在"));
        evictListCache(tenantId, userId);
        evictConversationDetailCache(tenantId, userId, saved);
        return ChatHistoryView.from(saved);
    }

    /**
     * 删除历史记录。
     *
     * @param tenantId 租户标识
     * @param id       记录ID
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Transactional
    public void deleteHistory(String tenantId, Long userId, Long id) {
        ChatHistory history = chatHistoryRepository.findByIdAndTenantIdAndUserId(id, tenantId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "历史记录不存在"));
        if (StringUtils.hasText(history.getConversationId())) {
            chatHistoryRepository.deleteByTenantIdAndUserIdAndConversationId(tenantId, userId, history.getConversationId());
        } else {
            chatHistoryRepository.deleteByIdAndTenantIdAndUserId(id, tenantId, userId);
        }
        evictListCache(tenantId, userId);
        evictConversationDetailCache(tenantId, userId, history);
    }

    /**
     * 序列化列表缓存。
     *
     * @param tenantId 租户标识
     * @param views    列表视图
     * @author 余炘洋
     * @date 2026-01-23
     */
    private void cachePage(String tenantId, Long userId, int page, int size, String keyword, PageResult<ChatHistoryView> result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            stringRedisTemplate.opsForValue().set(buildListKey(tenantId, userId, page, size, keyword), json, resolveTtl());
        } catch (Exception ex) {
            log.warn("缓存历史列表失败: {}", ex.getMessage());
        }
    }

    /**
     * 读取列表缓存。
     *
     * @param json JSON
     * @return 列表视图
     * @author 余炘洋
     * @date 2026-01-23
     */
    private PageResult<ChatHistoryView> fromJsonPage(String json) {
        try {
            return objectMapper.readValue(json, new tools.jackson.core.type.TypeReference<PageResult<ChatHistoryView>>() {
            });
        } catch (Exception ex) {
            log.warn("历史列表缓存解析失败: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 缓存详情。
     *
     * @param tenantId 租户标识
     * @param conversationKey 会话缓存键
     * @author 余炘洋
     * @date 2026-01-23
     */
    private void cacheDetail(String tenantId, Long userId, String conversationKey, ChatHistoryDetailView view) {
        try {
            String json = objectMapper.writeValueAsString(view);
            stringRedisTemplate.opsForValue().set(buildDetailKey(tenantId, userId, conversationKey), json, resolveTtl());
        } catch (Exception ex) {
            log.warn("缓存历史详情失败: {}", ex.getMessage());
        }
    }

    /**
     * 读取详情缓存。
     *
     * @param json JSON
     * @return 详情视图
     * @author 余炘洋
     * @date 2026-01-23
     */
    private ChatHistoryDetailView fromJsonDetail(String json) {
        try {
            return objectMapper.readValue(json, new tools.jackson.core.type.TypeReference<ChatHistoryDetailView>() {
            });
        } catch (Exception ex) {
            log.warn("历史详情缓存解析失败: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 构建详情视图。
     *
     * @param history 记录实体
     * @return 详情视图
     * @author 余炘洋
     * @date 2026-01-23
     */
    private ChatHistoryDetailView buildDetailView(String tenantId, Long userId, ChatHistory history) {
        if (!StringUtils.hasText(history.getConversationId())) {
            return toLegacyDetailView(history);
        }
        List<ChatHistory> histories = chatHistoryRepository.findByTenantIdAndUserIdAndConversationIdOrderByCreateTimeAscIdAsc(
                tenantId,
                userId,
                history.getConversationId()
        );
        if (histories == null || histories.isEmpty()) {
            return toLegacyDetailView(history);
        }
        return toConversationDetailView(histories);
    }

    private ChatHistoryDetailView toLegacyDetailView(ChatHistory history) {
        ChatHistoryDetailView view = new ChatHistoryDetailView();
        view.setId(history.getId());
        view.setConversationId(resolveConversationCacheKey(history));
        view.setQuestion(resolveConversationTitle(history));
        view.setAnswer(history.getAnswer());
        view.setSql(history.getSqlText());
        view.setData(chatHistoryPayloadCodec.decodeData(history.getDataJson()));
        view.setTrace(chatHistoryPayloadCodec.decodeTrace(history.getDataJson()));
        view.setCreateTime(history.getCreateTime());
        view.setMessages(buildMessages(List.of(history)));
        return view;
    }

    private ChatHistoryDetailView toConversationDetailView(List<ChatHistory> histories) {
        ChatHistory latest = histories.get(histories.size() - 1);
        ChatHistoryDetailView view = new ChatHistoryDetailView();
        view.setId(latest.getId());
        view.setConversationId(resolveConversationCacheKey(latest));
        view.setQuestion(resolveConversationTitle(latest));
        view.setAnswer(latest.getAnswer());
        view.setSql(latest.getSqlText());
        view.setData(chatHistoryPayloadCodec.decodeData(latest.getDataJson()));
        view.setTrace(chatHistoryPayloadCodec.decodeTrace(latest.getDataJson()));
        view.setCreateTime(latest.getCreateTime());
        view.setMessages(buildMessages(histories));
        return view;
    }

    private List<ChatHistoryMessageView> buildMessages(List<ChatHistory> histories) {
        List<ChatHistoryMessageView> messages = new ArrayList<>();
        for (ChatHistory history : histories) {
            if (StringUtils.hasText(history.getQuestion())) {
                ChatHistoryMessageView userMessage = new ChatHistoryMessageView();
                userMessage.setRole("user");
                userMessage.setContent(history.getQuestion());
                userMessage.setCreateTime(history.getCreateTime());
                messages.add(userMessage);
            }
            if (StringUtils.hasText(history.getAnswer())) {
                ChatHistoryMessageView assistantMessage = new ChatHistoryMessageView();
                assistantMessage.setRole("assistant");
                assistantMessage.setContent(history.getAnswer());
                assistantMessage.setSql(history.getSqlText());
                assistantMessage.setData(chatHistoryPayloadCodec.decodeData(history.getDataJson()));
                assistantMessage.setTrace(chatHistoryPayloadCodec.decodeTrace(history.getDataJson()));
                assistantMessage.setCreateTime(history.getCreateTime());
                messages.add(assistantMessage);
            }
        }
        return messages;
    }

    private String resolveConversationTitle(String tenantId, Long userId, String conversationId, String question) {
        if (StringUtils.hasText(conversationId)) {
            Optional<ChatHistory> latest = chatHistoryRepository.findTopByTenantIdAndUserIdAndConversationIdOrderByIdDesc(
                    tenantId,
                    userId,
                    conversationId
            );
            if (latest.isPresent() && StringUtils.hasText(latest.get().getConversationTitle())) {
                return latest.get().getConversationTitle();
            }
        }
        return question;
    }

    private String resolveConversationTitle(ChatHistory history) {
        if (StringUtils.hasText(history.getConversationTitle())) {
            return history.getConversationTitle();
        }
        return history.getQuestion();
    }

    private String resolveConversationCacheKey(ChatHistory history) {
        if (StringUtils.hasText(history.getConversationId())) {
            return history.getConversationId();
        }
        return "legacy-" + history.getId();
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return keyword.trim();
    }

    /**
     * 获取列表缓存键。
     *
     * @param tenantId 租户标识
     * @return 缓存键
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String buildListKey(String tenantId, Long userId, int page, int size, String keyword) {
        String keyToken = (keyword == null || keyword.isBlank()) ? "all" : String.valueOf(keyword.trim().hashCode());
        return "lingsql:chat:history:list:" + tenantId + ":" + userId + ":" + page + ":" + size + ":" + keyToken;
    }

    /**
     * 获取详情缓存键。
     *
     * @param tenantId 租户标识
     * @param conversationKey 会话缓存键
     * @return 缓存键
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String buildDetailKey(String tenantId, Long userId, String conversationKey) {
        return "lingsql:chat:history:detail:" + tenantId + ":" + userId + ":" + conversationKey;
    }

    /**
     * 清理列表缓存。
     *
     * @param tenantId 租户标识
     * @author 余炘洋
     * @date 2026-01-23
     */
    private void evictListCache(String tenantId, Long userId) {
        Set<String> keys = scanKeys("lingsql:chat:history:list:" + tenantId + ":" + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    /**
     * 清理详情缓存。
     *
     * @param tenantId 租户标识
     * @param history 记录实体
     * @author 余炘洋
     * @date 2026-01-23
     */
    private void evictConversationDetailCache(String tenantId, Long userId, ChatHistory history) {
        stringRedisTemplate.delete(buildDetailKey(tenantId, userId, resolveConversationCacheKey(history)));
    }

    /**
     * 解析缓存 TTL。
     *
     * @return TTL
     * @author 余炘洋
     * @date 2026-01-23
     */
    private Duration resolveTtl() {
        Duration ttl = properties.getCache().getChatHistoryTtl();
        return ttl == null ? Duration.ofMinutes(30) : ttl;
    }

    /**
     * 使用 SCAN 获取匹配键，避免 KEYS 带来的阻塞风险。
     *
     * @param pattern 键模式
     * @return 键集合
     * @author 余炘洋
     * @date 2026-02-28
     */
    private Set<String> scanKeys(String pattern) {
        if (pattern == null || pattern.isBlank() || stringRedisTemplate.getConnectionFactory() == null) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(500)
                .build();
        try (var connection = stringRedisTemplate.getConnectionFactory().getConnection();
             Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
            while (cursor.hasNext()) {
                byte[] key = cursor.next();
                if (key == null || key.length == 0) {
                    continue;
                }
                result.add(new String(key, StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            log.warn("扫描历史列表缓存键失败: pattern={}, reason={}", pattern, ex.getMessage());
            return Collections.emptySet();
        }
        return result;
    }
}
