package com.lingsql.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import com.lingsql.security.SensitiveStringAttributeConverter;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 模型配置实体，用于管理私有大模型或兼容 OpenAI 的模型资源。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_ai_model_config")
public class AiModelConfig {

    /**
     * 主键 ID。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 供应商名称。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "provider_name")
    private String providerName;

    /**
     * 模型名称。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "model_name")
    private String modelName;

    /**
     * 模型版本。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "model_version")
    private String modelVersion;

    /**
     * 模型基础地址。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "base_url")
    private String baseUrl;

    /**
     * 访问密钥。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "api_key")
    @Convert(converter = SensitiveStringAttributeConverter.class)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String apiKey;

    /**
     * 判断访问密钥是否已配置，不返回密钥内容。
     *
     * @return 是否已配置访问密钥
     * @author 余炘洋
     * @date 2026-08-15
     */
    @Transient
    public boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 是否启用。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "is_active")
    private Integer isActive;

    /**
     * 负载均衡权重。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "weight")
    private Integer weight;

    /**
     * 优先级。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "priority")
    private Integer priority;

    /**
     * 是否自动恢复。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "auto_recover")
    private Integer autoRecover;

    /**
     * 熔断冷却秒数。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "cooldown_seconds")
    private Integer cooldownSeconds;

    /**
     * 熔断时间。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "fuse_time")
    private LocalDateTime fuseTime;

    /**
     * 熔断原因。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "fuse_reason")
    private String fuseReason;
}
