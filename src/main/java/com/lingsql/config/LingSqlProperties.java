package com.lingsql.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * LingSQL 全局配置聚合类。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Data
@Validated
@ConfigurationProperties(prefix = "lingsql")
public class LingSqlProperties {

    /**
     * 安全与 SQL 规则配置。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Data
    public static class Security {
        /**
         * 默认 LIMIT 上限。
         *
         * @author 余炘洋
         * @date 2026-01-22
         */
        private int defaultLimit = 100;

        /**
         * Prompt 注入检测关键词列表。
         *
         * @author 余炘洋
         * @date 2026-01-22
         */
        private List<String> promptInjectionKeywords = new ArrayList<>();
    }

    /**
     * Redis 缓存相关配置。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Data
    public static class Cache {
        /**
         * 元数据缓存 TTL。
         *
         * @author 余炘洋
         * @date 2026-01-22
         */
        private Duration schemaTtl = Duration.ofHours(24);

        /**
         * LLM 补全的注释缓存 TTL。
         *
         * @author 余炘洋
         * @date 2026-01-22
         */
        private Duration commentTtl = Duration.ofDays(7);

        /**
         * 语义字典与 Join 规则缓存 TTL。
         *
         * @author 余炘洋
         * @date 2026-01-22
         */
        private Duration semanticTtl = Duration.ofHours(6);
    }

    /**
     * Hikari 连接池配置。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Data
    public static class Hikari {
        /**
         * 最大连接数。
         *
         * @author 余炘洋
         * @date 2026-01-22
         */
        private int maximumPoolSize = 10;

        /**
         * 最小空闲连接数。
         *
         * @author 余炘洋
         * @date 2026-01-22
         */
        private int minimumIdle = 2;

        /**
         * 空闲连接超时。
         *
         * @author 余炘洋
         * @date 2026-01-22
         */
        private Duration idleTimeout = Duration.ofMinutes(5);

        /**
         * 连接获取超时。
         *
         * @author 余炘洋
         * @date 2026-01-22
         */
        private Duration connectionTimeout = Duration.ofSeconds(20);
    }

    /**
     * 模型资源池配置。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Data
    public static class Model {
        /**
         * 熔断后默认冷却时间。
         *
         * @author 余炘洋
         * @date 2026-01-22
         */
        private Duration cooldown = Duration.ofMinutes(10);
    }

    /**
     * 安全配置。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private Security security = new Security();

    /**
     * 缓存配置。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private Cache cache = new Cache();

    /**
     * Hikari 连接池配置。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private Hikari hikari = new Hikari();

    /**
     * 模型资源池配置。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private Model model = new Model();

    /**
     * 语义配置已迁移至数据库，不再使用配置文件维护。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
}
