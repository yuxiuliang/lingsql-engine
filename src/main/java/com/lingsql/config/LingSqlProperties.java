package com.lingsql.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
     * SQL 执行与自愈配置。
     *
     * @author 余炘洋
     * @date 2026-07-29
     */
    @Data
    public static class SqlExecution {
        /**
         * 包含首轮在内的最大执行轮次。
         * 限制上限可避免异常场景重复消耗模型与数据库资源。
         *
         * @author 余炘洋
         * @date 2026-07-29
         */
        @Min(1)
        @Max(5)
        private int selfHealingMaxAttempts = 3;
    }

    @Valid
    private SqlExecution sqlExecution = new SqlExecution();

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

            /**
             * 是否信任代理头中的客户端IP。
             * 默认关闭，避免客户端伪造 X-Forwarded-For 绕过限流。
             *
             * @author 余炘洋
             * @date 2026-02-27
             */
            private boolean trustProxyHeaders = false;

            /**
             * 受信代理 IP 白名单。
             * 仅当 remoteAddr 命中白名单时才会解析代理头。
             *
             * @author 余炘洋
             * @date 2026-02-27
             */
            private List<String> trustedProxyIps = new ArrayList<>();
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
     * 计费相关配置。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Data
    public static class Billing {
        /**
         * 账本结算批量大小。
         *
         * @author 余炘洋
         * @date 2026-01-29
         */
        private int ledgerBatchSize = 200;

        /**
         * 账本结算间隔(毫秒)。
         *
         * @author 余炘洋
         * @date 2026-01-29
         */
        private long ledgerSettleIntervalMs = 5000L;

        /**
         * 对账扫描窗口。
         *
         * @author 余炘洋
         * @date 2026-01-29
         */
        private Duration reconcileWindow = Duration.ofHours(24);

        /**
         * RESERVE 缺少 CLOSE 时的自动恢复等待时间。
         *
         * @author 余炘洋
         * @date 2026-08-15
         */
        private Duration closeRecoveryDelay = Duration.ofMinutes(10);

        /**
         * 待结算账本允许的最长停留时间。
         *
         * @author 余炘洋
         * @date 2026-08-15
         */
        private Duration pendingTimeout = Duration.ofMinutes(10);

        /**
         * 商用就绪窗口内要求的最低真实账本事件数。
         *
         * @author 余炘洋
         * @date 2026-08-15
         */
        private int readinessMinLedgerEvents = 2;

        /**
         * 对账扫描间隔(毫秒)。
         *
         * @author 余炘洋
         * @date 2026-01-29
         */
        private long reconcileIntervalMs = 60000L;

        /**
         * 对账批量大小。
         *
         * @author 余炘洋
         * @date 2026-01-29
         */
        private int reconcileBatchSize = 200;

        /**
         * 对账差异阈值。
         *
         * @author 余炘洋
         * @date 2026-01-29
         */
        private long reconcileDiffThreshold = 0L;

        /**
         * 商用验收窗口（小时）。
         * 用于统计近窗口账本失败率与对账积压。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private int readinessWindowHours = 24;

        /**
         * 商用验收允许的账本失败率阈值。
         * 例如 0.01 表示 1%。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private double readinessFailedRateThreshold = 0.01D;

        /**
         * 商用验收允许的未处理对账日志上限。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private int readinessMaxOpenReconcile = 0;
    }

    /**
     * 数据留存与清理配置。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    @Data
    public static class Retention {
        /**
         * 是否启用留存清理任务。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private boolean enabled = false;

        /**
         * 留存清理任务 Cron。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private String cleanupCron = "0 0 3 * * ?";

        /**
         * 是否启用演练模式。
         * 开启后仅统计不删除。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private boolean dryRun = true;

        /**
         * SQL 执行日志保留天数。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private int sqlLogDays = 90;

        /**
         * 管理审计日志保留天数。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private int auditLogDays = 180;

        /**
         * 聊天历史保留天数。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private int chatHistoryDays = 30;

        /**
         * 仅在删除总量达到阈值时发送告警。
         * 0 表示关闭阈值保护。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private long alertThreshold = 1000L;
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
         * 每次投递批量大小。
         *
         * @author 余炘洋
         * @date 2026-01-28
         */
        private int deliveryBatchSize = 100;

        /**
         * 单条投递最大尝试次数。
         *
         * @author 余炘洋
         * @date 2026-01-28
         */
        private int deliveryMaxAttempts = 10;

        /**
         * 投递补偿基础间隔（毫秒）。
         *
         * @author 余炘洋
         * @date 2026-01-28
         */
        private long deliveryRetryDelayMs = 5000;

        /**
         * PREPARED 操作结果确认超时，超时后标记 UNKNOWN。
         */
        private Duration preparedTimeout = Duration.ofMinutes(5);
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
        private List<String> allowedHeaders = List.of(
                "Authorization",
                "Content-Type",
                "X-Tenant-Id",
                "X-Request-Id",
                "X-Trace-Id",
                "X-Refresh-Token"
        );

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
         * 是否启用 Refresh Token Cookie。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private boolean refreshCookieEnabled = true;

        /**
         * Refresh Token Cookie 名称。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private String refreshCookieName = "lingsql_refresh_token";

        /**
         * Refresh Token Cookie Path。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private String refreshCookiePath = "/api/v1/auth";

        /**
         * Refresh Token Cookie SameSite 策略。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private String refreshCookieSameSite = "Lax";

        /**
         * Refresh Token Cookie 是否仅 HTTPS 传输。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private boolean refreshCookieSecure = false;

        /**
         * 是否启用单设备会话。
         * 开启后同账号再次登录会让历史会话失效。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private boolean singleSessionEnabled = true;

    }

    /**
     * 运维告警配置。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    @Data
    public static class Alert {
        /**
         * 是否启用告警。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private boolean enabled = false;

        /**
         * 告警 Webhook 地址。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private String webhookUrl;

        /**
         * 钉钉机器人 Webhook 地址。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private String dingTalkWebhookUrl;

        /**
         * 企业微信机器人 Webhook 地址。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private String weComWebhookUrl;

        /**
         * 多通道发送策略：
         * true 表示所有已配置通道都必须发送成功；
         * false 表示任意通道成功即可。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private boolean requireAllChannels = false;

        /**
         * 告警轮询间隔（毫秒）。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private long poolCheckIntervalMs = 60000L;

        /**
         * 连接池使用率告警阈值，范围 (0,1]。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private double tenantPoolUtilizationThreshold = 0.9D;

        /**
         * 同类告警冷却时长。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private Duration cooldown = Duration.ofMinutes(5);

        /**
         * 告警请求连接超时。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private Duration connectTimeout = Duration.ofSeconds(3);

        /**
         * 告警请求读取超时。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private Duration readTimeout = Duration.ofSeconds(5);
    }

    /**
     * 租户生命周期配置。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    @Data
    public static class TenantLifecycle {
        /**
         * 是否启用生命周期治理。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private boolean enabled = true;

        /**
         * 到期后的宽限期时长。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private Duration gracePeriod = Duration.ofDays(7);

        /**
         * 触发“即将到期”状态的提前天数。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private int expiringSoonDays = 7;

        /**
         * 试用租户默认有效期（天）。
         * 当租户类型=TRIAL 且未显式设置 expireTime 时生效。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private int trialDefaultDays = 14;

        /**
         * 是否启用到期自动冻结。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private boolean autoSuspendEnabled = true;

        /**
         * 自动冻结任务 Cron。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private String autoSuspendCron = "0 0/30 * * * ?";

        /**
         * 是否启用到期提醒。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private boolean reminderEnabled = true;

        /**
         * 到期提醒任务 Cron。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private String reminderCron = "0 0 9 * * ?";

        /**
         * 到期提醒天数列表（如 7/3/1）。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private List<Integer> reminderDays = new ArrayList<>(List.of(7, 3, 1));

        /**
         * 到期提醒去重 TTL。
         * 用于防止服务重启后重复发送同日提醒。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private Duration reminderDedupeTtl = Duration.ofDays(2);

        /**
         * 是否启用流失风险日报告警。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private boolean riskAlertEnabled = true;

        /**
         * 流失风险日报告警 Cron。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private String riskAlertCron = "0 30 9 * * ?";

        /**
         * 流失风险告警阈值（0-100）。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private int riskAlertThreshold = 70;

        /**
         * 流失风险告警明细 TopN。
         *
         * @author 余炘洋
         * @date 2026-02-27
         */
        private int riskAlertTopN = 20;
    }

    /**
     * 组织执行治理配置。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    @Data
    public static class OrgExecution {
        /**
         * 是否启用组织执行治理。
         */
        private boolean enabled = true;

        /**
         * 是否启用逾期升级。
         */
        private boolean overdueEscalationEnabled = true;

        /**
         * 逾期升级任务 Cron。
         */
        private String overdueEscalationCron = "0 0/15 * * * ?";

        /**
         * 提前多少小时进入“即将逾期”。
         */
        private int dueSoonHours = 4;

        /**
         * 重复升级冷却时间。
         */
        private Duration escalationCooldown = Duration.ofHours(4);
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
     * 计费配置。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private Billing billing = new Billing();

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
     * 数据留存配置。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private Retention retention = new Retention();

    /**
     * 运维告警配置。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private Alert alert = new Alert();

    /**
     * 租户生命周期配置。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private TenantLifecycle tenantLifecycle = new TenantLifecycle();

    /**
     * 组织执行治理配置。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    private OrgExecution orgExecution = new OrgExecution();

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
