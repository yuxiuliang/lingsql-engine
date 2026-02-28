package com.lingsql.executor;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SQL 执行任务追踪器。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Service
public class SqlTaskTracker {

    private final ConcurrentHashMap<String, AtomicInteger> runningTasks = new ConcurrentHashMap<>();

    /**
     * 标记任务开始。
     *
     * @param tenantId 租户ID
     * @author 余炘洋
     * @date 2026-01-26
     */
    public void start(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return;
        }
        runningTasks.computeIfAbsent(tenantId.trim(), key -> new AtomicInteger(0))
                .incrementAndGet();
    }

    /**
     * 标记任务结束。
     *
     * @param tenantId 租户ID
     * @author 余炘洋
     * @date 2026-01-26
     */
    public void finish(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return;
        }
        String key = tenantId.trim();
        runningTasks.compute(key, (ignored, counter) -> {
            if (counter == null) {
                return null;
            }
            return counter.decrementAndGet() <= 0 ? null : counter;
        });
    }

    /**
     * 判断是否存在运行中的任务。
     *
     * @param tenantId 租户ID
     * @return 是否运行中
     * @author 余炘洋
     * @date 2026-01-26
     */
    public boolean hasRunning(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return false;
        }
        AtomicInteger counter = runningTasks.get(tenantId.trim());
        return counter != null && counter.get() > 0;
    }
}
