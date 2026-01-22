package com.lingsql.schema;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.schema.entity.JoinRule;
import com.lingsql.schema.entity.SemanticLexiconItem;
import com.lingsql.schema.repository.JoinRuleRepository;
import com.lingsql.schema.repository.SemanticLexiconRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 语义配置加载与缓存服务。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticConfigService {

    private static final String LEXICON_CACHE_PREFIX = "lingsql:semantic:lexicon:";
    private static final String JOIN_CACHE_PREFIX = "lingsql:semantic:join:";

    private final SemanticLexiconRepository lexiconRepository;
    private final JoinRuleRepository joinRuleRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final LingSqlProperties properties;

    /**
     * 获取语义字典映射。
     *
     * @param tenantId 租户标识
     * @return 词典映射
     * @author 余炘洋
     * @date 2026-01-22
     */
    public Map<String, String> getLexiconMap(String tenantId) {
        String cacheKey = LEXICON_CACHE_PREFIX + normalizeTenant(tenantId);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cached)) {
            return parseLexicon(cached);
        }

        Map<String, String> merged = new LinkedHashMap<>();
        List<SemanticLexiconItem> global = lexiconRepository.findByTenantIdIsNullAndStatus(1);
        global.forEach(item -> merged.put(item.getTerm(), item.getMapping()));

        if (StringUtils.hasText(tenantId)) {
            List<SemanticLexiconItem> tenantItems = lexiconRepository.findByTenantIdAndStatus(tenantId, 1);
            tenantItems.forEach(item -> merged.put(item.getTerm(), item.getMapping()));
        }

        cacheLexicon(cacheKey, merged);
        return merged;
    }

    /**
     * 获取 Join 规则列表。
     *
     * @param tenantId 租户标识
     * @return Join 规则列表
     * @author 余炘洋
     * @date 2026-01-22
     */
    public List<JoinRule> getJoinRules(String tenantId) {
        String cacheKey = JOIN_CACHE_PREFIX + normalizeTenant(tenantId);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cached)) {
            return parseJoinRules(cached);
        }

        List<JoinRule> global = joinRuleRepository.findByTenantIdIsNullAndStatus(1);
        if (StringUtils.hasText(tenantId)) {
            List<JoinRule> tenantItems = joinRuleRepository.findByTenantIdAndStatus(tenantId, 1);
            if (!tenantItems.isEmpty()) {
                global.addAll(tenantItems);
            }
        }

        cacheJoinRules(cacheKey, global);
        return global;
    }

    /**
     * 清理语义缓存。
     *
     * @param tenantId 租户标识
     * @author 余炘洋
     * @date 2026-01-22
     */
    public void clearCache(String tenantId) {
        String tenantKey = normalizeTenant(tenantId);
        redisTemplate.delete(LEXICON_CACHE_PREFIX + tenantKey);
        redisTemplate.delete(JOIN_CACHE_PREFIX + tenantKey);
    }

    /**
     * 缓存语义字典。
     *
     * @param cacheKey 缓存键
     * @param lexicon  字典映射
     * @author 余炘洋
     * @date 2026-01-22
     */
    private void cacheLexicon(String cacheKey, Map<String, String> lexicon) {
        try {
            String json = objectMapper.writeValueAsString(lexicon);
            redisTemplate.opsForValue().set(cacheKey, json, resolveTtl().toSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("缓存语义字典失败", e);
        }
    }

    /**
     * 缓存 Join 规则。
     *
     * @param cacheKey 缓存键
     * @param rules    规则列表
     * @author 余炘洋
     * @date 2026-01-22
     */
    private void cacheJoinRules(String cacheKey, List<JoinRule> rules) {
        try {
            String json = objectMapper.writeValueAsString(rules);
            redisTemplate.opsForValue().set(cacheKey, json, resolveTtl().toSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("缓存 Join 规则失败", e);
        }
    }

    /**
     * 解析语义字典。
     *
     * @param json 缓存内容
     * @return 字典映射
     * @author 余炘洋
     * @date 2026-01-22
     */
    private Map<String, String> parseLexicon(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            log.warn("解析语义字典失败", e);
            return new LinkedHashMap<>();
        }
    }

    /**
     * 解析 Join 规则。
     *
     * @param json 缓存内容
     * @return Join 规则列表
     * @author 余炘洋
     * @date 2026-01-22
     */
    private List<JoinRule> parseJoinRules(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<JoinRule>>() {
            });
        } catch (Exception e) {
            log.warn("解析 Join 规则失败", e);
            return List.of();
        }
    }

    /**
     * 规范化租户键。
     *
     * @param tenantId 租户标识
     * @return 缓存分组键
     * @author 余炘洋
     * @date 2026-01-22
     */
    private String normalizeTenant(String tenantId) {
        return StringUtils.hasText(tenantId) ? tenantId : "global";
    }

    /**
     * 获取缓存 TTL。
     *
     * @return TTL
     * @author 余炘洋
     * @date 2026-01-22
     */
    private Duration resolveTtl() {
        if (properties.getCache().getSemanticTtl() != null) {
            return properties.getCache().getSemanticTtl();
        }
        return properties.getCache().getSchemaTtl();
    }
}
