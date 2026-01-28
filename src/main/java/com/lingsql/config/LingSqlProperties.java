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

        /**
         * 是否启用敏感字段加密。
         *
         * @author 余炘洋
         * @date 2026-01-28
         */
        private boolean dataEncryptionEnabled = true;

        /**
         * 敏感字段加密密钥。
         *
         * @author 余炘洋
         * @date 2026-01-28
         */
        private String dataEncryptionKey;

        /**
         * 是否执行敏感数据加密迁移。
         *
         * @author 余炘洋
         * @date 2026-01-28
         */
        private boolean dataEncryptionMigrate = false;

        /**
         * 是否启用安全响应头。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private boolean securityHeadersEnabled = true;

        /**
         * Content-Security-Policy 策略。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private String contentSecurityPolicy = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

        /**
         * HSTS 最大有效期。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private Duration hstsMaxAge = Duration.ofDays(365);

        /**
         * HSTS 是否包含子域名。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private boolean hstsIncludeSubDomains = true;

        /**
         * HSTS 是否允许预加载。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private boolean hstsPreload = false;

        /**
         * 是否在非 HTTPS 场景也返回 HSTS。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private boolean hstsAlways = false;

        /**
         * 登录限流配置。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        @Data
        public static class RateLimit {
            /**
             * 是否启用登录限流。
             *
             * @author 余炘洋
             * @date 2026-01-27
             */
            private boolean enabled = true;

            /**
             * 单窗口最大请求数。
             *
             * @author 余炘洋
             * @date 2026-01-27
             */
            private int loginMaxRequests = 20;

            /**
             * 登录限流窗口。
             *
             * @author 余炘洋
             * @date 2026-01-27
             */
            private Duration loginWindow = Duration.ofMinutes(1);

            /**
             * 触发限流后的封禁时间。
             *
             * @author 余炘洋
             * @date 2026-01-27
             */
            private Duration loginBlock = Duration.ofMinutes(10);

            /**
             * 是否启用敏感接口限流。
             *
             * @author 余炘洋
             * @date 2026-01-28
             */
            private boolean sensitiveEnabled = true;

            /**
             * 敏感接口单窗口最大请求数。
             *
             * @author 余炘洋
             * @date 2026-01-28
             */
            private int sensitiveMaxRequests = 60;

            /**
             * 敏感接口限流窗口。
             *
             * @author 余炘洋
             * @date 2026-01-28
             */
            private Duration sensitiveWindow = Duration.ofMinutes(1);

            /**
             * 敏感接口触发限流后的封禁时间。
             *
             * @author 余炘洋
             * @date 2026-01-28
             */
            private Duration sensitiveBlock = Duration.ofMinutes(5);
        }

        /**
         * 登录限流配置。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private RateLimit rateLimit = new RateLimit();
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

        /**
         * 租户数据源缓存存活时间。
         *
         * @author 余炘洋
         * @date 2026-01-28
         */
        private Duration tenantCacheTtl = Duration.ofMinutes(30);

        /**
         * 租户连接池最大缓存数量。
         *
         * @author 余炘洋
         * @date 2026-01-28
         */
        private int maxTenantPools = 200;

        /**
         * 租户缓存清理间隔（毫秒）。
         *
         * @author 余炘洋
         * @date 2026-01-28
         */
        private long tenantCacheCleanIntervalMs = 300000L;
    }

    /**
     * Web 层通用配置。
     *
     * @author 余炘洋
     * @date 2026-01-28
     */
    @Data
    public static class Web {
        /**
         * 列表最大分页大小。
         *
         * @author 余炘洋
         * @date 2026-01-28
         */
        private int maxPageSize = 100;

        /**
         * 导出最大行数。
         *
         * @author 余炘洋
         * @date 2026-01-28
         */
        private int exportMaxRows = 10000;
    }

    /**
     * 审计日志重试配置。
     *
     * @author 余炘洋
     * @date 2026-01-28
     */
    @Data
    public static class Audit {
        /**
         * 是否启用审计写入重试。
         *
         * @author 余炘洋
         * @date 2026-01-28
         */
        private boolean retryEnabled = true;

        /**
         * 重试队列最大长度。
         *
         * @author 余炘洋
         * @date 2026-01-28
         */
        private int retryMaxQueue = 1000;

        /**
         * 每次重试批量大小。
         *
         * @author 余炘洋
         * @date 2026-01-28
         */
        private int retryBatchSize = 100;

        /**
         * 单条日志最大重试次数。
         *
         * @author 余炘洋
         * @date 2026-01-28
         */
        private int retryMaxAttempts = 3;

        /**
         * 重试间隔（毫秒）。
         *
         * @author 余炘洋
         * @date 2026-01-28
         */
        private long retryDelayMs = 60000;
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
        private List<String> allowedHeaders = List.of("Authorization", "Content-Type", "X-Tenant-Id", "X-Request-Id", "X-Trace-Id");

        /**
         * 暴露给前端的响应头列表。
         *
         * @author 余炘洋
         * @date 2026-01-23
         */
        private List<String> exposedHeaders = new ArrayList<>(List.of("X-Request-Id", "X-Trace-Id"));

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
        private String jwtSecret;

        /**
         * JWT 签发方。
         *
         * @author 余炘洋
         * @date 2026-01-26
         */
        private String jwtIssuer = "lingsql-engine";

        /**
         * JWT 受众。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private String jwtAudience = "lingsql-engine";

        /**
         * JWT 有效期。
         *
         * @author 余炘洋
         * @date 2026-01-26
         */
        private Duration jwtTtl = Duration.ofHours(24);

        /**
         * 超级管理员 JWT 有效期。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private Duration superAdminJwtTtl = Duration.ofHours(2);

        /**
         * Refresh Token 有效期。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private Duration refreshTtl = Duration.ofDays(7);

        /**
         * 登录 Cookie 名称。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private String cookieName = "LINGSQL_TOKEN";

        /**
         * Cookie 域名(可选)。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private String cookieDomain;

        /**
         * Cookie Path。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private String cookiePath = "/";

        /**
         * Cookie SameSite 策略(Lax/Strict/None)。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private String cookieSameSite = "Lax";

        /**
         * Cookie Secure 标记(为空时根据请求协议自动判断)。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private Boolean cookieSecure;

        /**
         * Refresh Token Cookie 名称。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private String refreshCookieName = "LINGSQL_REFRESH";
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
        private boolean enabled = false;

        /**
         * 默认租户ID。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private String tenantId;

        /**
         * 默认租户名称。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private String tenantName;

        /**
         * 管理员账号。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private String username;

        /**
         * 管理员密码。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private String password;

        /**
         * 管理员昵称。
         *
         * @author 余炘洋
         * @date 2026-01-27
         */
        private String nickname;

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
     * Web 配置。
     *
     * @author 余炘洋
     * @date 2026-01-28
     */
    private Web web = new Web();

    /**
     * 审计配置。
     *
     * @author 余炘洋
     * @date 2026-01-28
     */
    private Audit audit = new Audit();

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
