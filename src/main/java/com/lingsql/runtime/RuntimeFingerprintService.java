package com.lingsql.runtime;

import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.agent.repository.AiModelConfigRepository;
import com.lingsql.runtime.model.RuntimeFingerprintView;
import com.lingsql.runtime.model.RuntimeModelFingerprintView;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
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

    @Value("${lingsql.runtime.data-snapshot-id:UNSET}")
    private String dataSnapshotId;

    /**
     * 读取当前实际运行实例的不可歧义指纹。
     *
     * @return 运行指纹
     */
    public RuntimeFingerprintView load() {
        List<RuntimeModelFingerprintView> activeModels = modelConfigRepository.findByIsActive(ACTIVE_STATUS).stream()
                .sorted(Comparator.comparing(AiModelConfig::getPriority, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(AiModelConfig::getId, Comparator.nullsLast(Long::compareTo)))
                .map(this::toModelView)
                .toList();
        String routeFingerprint = calculateModelRouteFingerprint(activeModels);
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

    private String calculateModelRouteFingerprint(List<RuntimeModelFingerprintView> models) {
        String canonical = models.stream()
                .map(model -> String.join("|",
                        String.valueOf(model.id()),
                        normalize(model.providerName()),
                        normalize(model.modelName()),
                        normalize(model.modelVersion()),
                        String.valueOf(model.priority()),
                        String.valueOf(model.weight())))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("运行环境不支持 SHA-256", ex);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
