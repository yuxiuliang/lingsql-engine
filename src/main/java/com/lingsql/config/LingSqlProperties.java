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

        /**
         * 聊天历史缓存 TTL。
         *
         * @author 余炘洋
         * @date 2026-01-23
         */
        private Duration chatHistoryTtl = Duration.ofMinutes(30);
    }

    /**
     * AI 记忆配置。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Data
    public static class Ai {
        /**
         * 记忆配置。
         *
         * @author 余炘洋
         * @date 2026-01-26
         */
        @Data
        public static class Memory {
            /**
             * 记忆条数。
             *
             * @author 余炘洋
             * @date 2026-01-26
             */
            private int limit = 10;

            /**
             * 记忆 TTL。
             *
             * @author 余炘洋
             * @date 2026-01-26
             */
            private Duration ttl = Duration.ofMinutes(30);
        }

        /**
         * 记忆参数。
         *
         * @author 余炘洋
         * @date 2026-01-26
         */
        private Memory memory = new Memory();
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
     * 聊天配置。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Data
    public static class Chat {
        /**
         * 用户问题最大长度。
         *
         * @author 余炘洋
         * @date 2026-01-23
         */
        private int maxQuestionLength = 2000;
    }

    /**
     * 跨域配置。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Data
    public static class Cors {
        /**
         * 是否启用跨域白名单。
         *
         * @author 余炘洋
         * @date 2026-01-23
         */
        private boolean enabled = true;

        /**
         * 允许的来源列表。
         *
         * @author 余炘洋
         * @date 2026-01-23
         */
        private List<String> allowedOrigins = new ArrayList<>();

        /**
         * 允许的方法列表。
         *
         * @author 余炘洋
         * @date 2026-01-23
         */
        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");

        /**
         * 允许的请求头列表。
         *
         * @author 余炘洋
         * @date 2026-01-23
         */
        private List<String> allowedHeaders = List.of("Authorization", "Content-Type", "X-Tenant-Id");

        /**
         * 暴露给前端的响应头列表。
         *
         * @author 余炘洋
         * @date 2026-01-23
         */
        private List<String> exposedHeaders = new ArrayList<>();

        /**
         * 是否允许携带凭证。
         *
         * @author 余炘洋
         * @date 2026-01-23
         */
        private boolean allowCredentials = true;

        /**
         * 预检请求缓存时间（秒）。
         *
         * @author 余炘洋
         * @date 2026-01-23
         */
        private Long maxAge = 3600L;
    }

    /**
     * 认证配置。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Data
    public static class Auth {
        /**
         * JWT 密钥(至少 32 字符)。
         *
         * @author 余炘洋
         * @date 2026-01-26
         */
        private String jwtSecret = "change-me-to-a-secure-secret-key-32";

        /**
         * JWT 签发方。
         *
         * @author 余炘洋
         * @date 2026-01-26
         */
        private String jwtIssuer = "lingsql-engine";

        /**
         * JWT 有效期。
         *
         * @author 余炘洋
         * @date 2026-01-26
         */
        private Duration jwtTtl = Duration.ofHours(24);
    }

    /**
     * 超级管理员初始化配置。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Data
    public static class Admin {
        /**
         * 是否启用初始化。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private boolean enabled = true;

        /**
         * 默认租户ID。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private String tenantId = "1";

        /**
         * 默认租户名称。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private String tenantName = "系统租户";

        /**
         * 管理员账号。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private String username = "admin";

        /**
         * 管理员密码。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private String password = "Admin123456";

        /**
         * 管理员昵称。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private String nickname = "超级管理员";

        /**
         * 初始化数据资源路径。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private String seedResource = "classpath:rbac-seed.json";
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
     * 聊天配置。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private Chat chat = new Chat();

    /**
     * AI 配置。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Ai ai = new Ai();

    /**
     * 语义配置已迁移至数据库，不再使用配置文件维护。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */

    /**
     * 跨域配置。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private Cors cors = new Cors();

    /**
     * 认证配置。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Auth auth = new Auth();

    /**
     * 超级管理员初始化配置。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Admin admin = new Admin();
}
