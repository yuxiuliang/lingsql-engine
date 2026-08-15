package com.lingsql.runtime;

import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.agent.ModelRouteFingerprintCalculator;
import com.lingsql.agent.repository.AiModelConfigRepository;
import com.lingsql.runtime.model.RuntimeFingerprintView;
import com.lingsql.runtime.model.RuntimeModelFingerprintView;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 汇总候选制品、数据库和模型路由的运行指纹。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Service
@RequiredArgsConstructor
public class RuntimeFingerprintService {

    private static final Integer ACTIVE_STATUS = 1;

    private final BuildProperties buildProperties;
    private final JdbcTemplate jdbcTemplate;
    private final AiModelConfigRepository modelConfigRepository;
    private final ModelRouteFingerprintCalculator modelRouteFingerprintCalculator;

    @Value("${lingsql.runtime.data-snapshot-id:UNSET}")
    private String dataSnapshotId;

    /**
     * 读取当前实际运行实例的不可歧义指纹。
     *
     * @return 运行指纹
     */
    public RuntimeFingerprintView load() {
        List<AiModelConfig> activeModelConfigs = modelConfigRepository.findByIsActive(ACTIVE_STATUS);
        List<RuntimeModelFingerprintView> activeModels = activeModelConfigs.stream()
                .sorted(Comparator.comparing(AiModelConfig::getPriority, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(AiModelConfig::getId, Comparator.nullsLast(Long::compareTo)))
                .map(this::toModelView)
                .toList();
        String routeFingerprint = modelRouteFingerprintCalculator.calculate(activeModelConfigs);
        String schemaVersion = jdbcTemplate.query(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
                resultSet -> resultSet.next() ? resultSet.getString(1) : "UNMANAGED"
        );
        return new RuntimeFingerprintView(
                buildProperties.get("commit"),
                buildProperties.getVersion(),
                buildProperties.getTime(),
                schemaVersion,
                dataSnapshotId,
                routeFingerprint,
                activeModels
        );
    }

    private RuntimeModelFingerprintView toModelView(AiModelConfig config) {
        return new RuntimeModelFingerprintView(
                config.getId(),
                config.getProviderName(),
                config.getModelName(),
                config.getModelVersion(),
                config.getPriority(),
                config.getWeight()
        );
    }

}
