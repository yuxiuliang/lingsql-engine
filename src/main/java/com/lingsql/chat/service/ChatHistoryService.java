package com.lingsql.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.chat.entity.ChatHistory;
import com.lingsql.chat.model.ChatHistoryDetailView;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    public ChatHistory saveHistory(String tenantId, Long userId, String question, String answer, String sqlText, List<Map<String, Object>> data) {
        ChatHistory history = new ChatHistory();
        history.setTenantId(tenantId);
        history.setUserId(userId);
        history.setQuestion(question);
        history.setAnswer(answer);
        history.setSqlText(sqlText);
        history.setStatus(sqlText != null && sqlText.startsWith("ERROR:") ? 0 : 1);
        history.setCreateTime(LocalDateTime.now());
        history.setDataJson(toJson(data));
        ChatHistory saved = chatHistoryRepository.save(history);
        evictListCache(tenantId, userId);
        cacheDetail(tenantId, userId, saved);
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
        Page<ChatHistory> historyPage;
        if (keyword == null || keyword.isBlank()) {
            historyPage = chatHistoryRepository.findByTenantIdAndUserIdOrderByCreateTimeDesc(tenantId, userId, pageable);
        } else {
            historyPage = chatHistoryRepository.findByTenantIdAndUserIdAndQuestionContainingOrderByCreateTimeDesc(
                    tenantId,
                    userId,
                    keyword.trim(),
                    pageable
            );
        }
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
        String cacheKey = buildDetailKey(tenantId, userId, id);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            ChatHistoryDetailView cachedView = fromJsonDetail(cached);
            if (cachedView != null) {
                return cachedView;
            }
        }
        ChatHistory history = chatHistoryRepository.findByIdAndTenantIdAndUserId(id, tenantId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "历史记录不存在"));
        ChatHistoryDetailView view = toDetailView(history);
        cacheDetail(tenantId, userId, history);
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
    public ChatHistoryView renameHistory(String tenantId, Long userId, Long id, String question) {
        if (question == null || question.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "标题不能为空");
        }
        ChatHistory history = chatHistoryRepository.findByIdAndTenantIdAndUserId(id, tenantId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "历史记录不存在"));
        history.setQuestion(question.trim());
        ChatHistory saved = chatHistoryRepository.save(history);
        evictListCache(tenantId, userId);
        cacheDetail(tenantId, userId, saved);
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
    public void deleteHistory(String tenantId, Long userId, Long id) {
        chatHistoryRepository.deleteByIdAndTenantIdAndUserId(id, tenantId, userId);
        evictListCache(tenantId, userId);
        evictDetailCache(tenantId, userId, id);
    }

    /**
     * 将数据序列化为 JSON。
     *
     * @param data 数据
     * @return JSON
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String toJson(List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception ex) {
            log.warn("历史记录数据序列化失败: {}", ex.getMessage());
            return "[]";
        }
    }

    /**
     * 解析 JSON 数据。
     *
     * @param json JSON 字符串
     * @return 数据列表
     * @author 余炘洋
     * @date 2026-01-23
     */
    private List<Map<String, Object>> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception ex) {
            log.warn("历史记录数据解析失败: {}", ex.getMessage());
            return Collections.emptyList();
        }
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
            return objectMapper.readValue(json, new TypeReference<PageResult<ChatHistoryView>>() {
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
     * @param history  记录实体
     * @author 余炘洋
     * @date 2026-01-23
     */
    private void cacheDetail(String tenantId, Long userId, ChatHistory history) {
        try {
            ChatHistoryDetailView view = toDetailView(history);
            String json = objectMapper.writeValueAsString(view);
            stringRedisTemplate.opsForValue().set(buildDetailKey(tenantId, userId, history.getId()), json, resolveTtl());
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
            return objectMapper.readValue(json, new TypeReference<ChatHistoryDetailView>() {
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
    private ChatHistoryDetailView toDetailView(ChatHistory history) {
        ChatHistoryDetailView view = new ChatHistoryDetailView();
        view.setId(history.getId());
        view.setQuestion(history.getQuestion());
        view.setAnswer(history.getAnswer());
        view.setSql(history.getSqlText());
        view.setData(fromJson(history.getDataJson()));
        view.setCreateTime(history.getCreateTime());
        return view;
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
     * @param id       记录ID
     * @return 缓存键
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String buildDetailKey(String tenantId, Long userId, Long id) {
        return "lingsql:chat:history:detail:" + tenantId + ":" + userId + ":" + id;
    }

    /**
     * 清理列表缓存。
     *
     * @param tenantId 租户标识
     * @author 余炘洋
     * @date 2026-01-23
     */
    private void evictListCache(String tenantId, Long userId) {
        var keys = stringRedisTemplate.keys("lingsql:chat:history:list:" + tenantId + ":" + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    /**
     * 清理详情缓存。
     *
     * @param tenantId 租户标识
     * @param id       记录ID
     * @author 余炘洋
     * @date 2026-01-23
     */
    private void evictDetailCache(String tenantId, Long userId, Long id) {
        stringRedisTemplate.delete(buildDetailKey(tenantId, userId, id));
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
}
