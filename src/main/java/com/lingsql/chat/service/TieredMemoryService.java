package com.lingsql.chat.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 分层记忆服务：会话内短期记忆由 Redis 会话窗口承担，临时事实与程序/永久记忆由此服务按层分离持久化。
 *
 * <p>当前实现使用进程内存储，完整生产级持久化需落位到 Redis/DB 并纳入保留期治理。</p>
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Service
public class TieredMemoryService {

    /**
     * 记忆层级。
     */
    public enum Tier {
        TEMPORARY,
        PROCEDURAL,
        PERMANENT
    }

    private final Map<String, List<String>> store = new ConcurrentHashMap<>();

    /**
     * 记录一条分层事实记忆。
     */
    public void remember(String tenantId, String conversationId, Tier tier, String fact) {
        store.computeIfAbsent(key(tenantId, conversationId, tier), k -> new CopyOnWriteArrayList<>()).add(fact);
    }

    /**
     * 列出指定层级的记忆。
     */
    public List<String> list(String tenantId, String conversationId, Tier tier) {
        return List.copyOf(store.getOrDefault(key(tenantId, conversationId, tier), List.of()));
    }

    private String key(String tenantId, String conversationId, Tier tier) {
        return tenantId + ":" + (conversationId == null ? "global" : conversationId) + ":" + tier.name();
    }
}
