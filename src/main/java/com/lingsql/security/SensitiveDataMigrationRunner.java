package com.lingsql.security;

import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.agent.repository.AiModelConfigRepository;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 敏感字段加密迁移执行器。
 *
 * @author 余炘洋
 * @date 2026-01-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensitiveDataMigrationRunner implements ApplicationRunner {

    private final TenantDbConfigRepository tenantDbConfigRepository;
    private final AiModelConfigRepository aiModelConfigRepository;
    private final SensitiveStringEncryptor encryptor;
    private final LingSqlProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        LingSqlProperties.Security security = properties.getSecurity();
        if (security == null || !security.isDataEncryptionMigrate()) {
            return;
        }
        if (!security.isDataEncryptionEnabled()) {
            log.warn("敏感数据迁移已启用，但 data-encryption-enabled=false，已跳过。");
            return;
        }
        int tenantUpdated = migrateTenantDbConfigs();
        int modelUpdated = migrateModelConfigs();
        log.info("敏感字段加密迁移完成: tenantDbConfigUpdated={}, modelConfigUpdated={}", tenantUpdated, modelUpdated);
    }

    private int migrateTenantDbConfigs() {
        List<TenantDbConfig> configs = tenantDbConfigRepository.findAll();
        int updated = 0;
        for (TenantDbConfig config : configs) {
            String password = config.getPassword();
            if (!StringUtils.hasText(password)) {
                continue;
            }
            String encrypted = encryptor.encrypt(password);
            if (!encrypted.equals(password)) {
                config.setPassword(encrypted);
                tenantDbConfigRepository.save(config);
                updated++;
            }
        }
        return updated;
    }

    private int migrateModelConfigs() {
        List<AiModelConfig> configs = aiModelConfigRepository.findAll();
        int updated = 0;
        for (AiModelConfig config : configs) {
            String apiKey = config.getApiKey();
            if (!StringUtils.hasText(apiKey)) {
                continue;
            }
            String encrypted = encryptor.encrypt(apiKey);
            if (!encrypted.equals(apiKey)) {
                config.setApiKey(encrypted);
                aiModelConfigRepository.save(config);
                updated++;
            }
        }
        return updated;
    }
}
