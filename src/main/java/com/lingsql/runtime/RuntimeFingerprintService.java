package com.lingsql.runtime;

import com.lingsql.agent.ModelRoutePlan;
import com.lingsql.agent.TenantModelRouting;
import com.lingsql.runtime.model.RuntimeFingerprintView;
import com.lingsql.runtime.model.RuntimeModelFingerprintView;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 汇总候选制品、数据库和模型路由的运行指纹。
 *
 * @author 余炘洋
 * @date 2026-08-15
 * @modify 余炘洋 2026-08-16 输出指定租户的模型授权路由指纹
 */
@Service
@RequiredArgsConstructor
public class RuntimeFingerprintService {

    private final BuildProperties buildProperties;
    private final JdbcTemplate jdbcTemplate;
    private final TenantModelRouting tenantModelRouting;

    @Value("${lingsql.runtime.data-snapshot-id:UNSET}")
    private String dataSnapshotId;

    @Value("${lingsql.runtime.slot:UNSET}")
    private String slot;

    @Value("${lingsql.runtime.release-id:UNSET}")
    private String releaseId;

    /**
     * 读取当前实际运行实例的不可歧义指纹。
     *
     * @return 运行指纹
     */
    public RuntimeFingerprintView load(String modelTenantId) {
        ModelRoutePlan routePlan = tenantModelRouting.planFor(modelTenantId);
        List<RuntimeModelFingerprintView> activeModels = routePlan.candidates().stream()
                .map(candidate -> new RuntimeModelFingerprintView(
                        candidate.modelConfig().getId(),
                        candidate.modelConfig().getProviderName(),
                        candidate.modelConfig().getModelName(),
                        candidate.modelConfig().getModelVersion(),
                        candidate.priority(),
                        candidate.weight()))
                .toList();
        String routeFingerprint = routePlan.routeFingerprint();
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
                activeModels,
                slot,
                releaseId
        );
    }

}
