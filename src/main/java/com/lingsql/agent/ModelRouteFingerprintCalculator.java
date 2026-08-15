package com.lingsql.agent;

import com.lingsql.agent.model.AiModelConfig;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * 生成可跨运行指纹与物理调用证据复用的模型路由摘要。
 *
 * @author 余炘洋
 * @date 2026-08-15
 * @modify 余炘洋 2026-08-16 指纹绑定租户授权与模型端点
 */
@Component
public class ModelRouteFingerprintCalculator {

    /**
     * 计算租户授权路由指纹。租户、授权优先级和权重均属于证据的一部分。
     *
     * @param tenantId 租户标识
     * @param candidates 授权候选
     * @return SHA-256 指纹
     */
    public String calculate(String tenantId, List<ModelRouteCandidate> candidates) {
        String canonicalRoutes = candidates == null ? "" : candidates.stream()
                .sorted(Comparator.comparingInt(ModelRouteCandidate::priority)
                        .thenComparing(candidate -> candidate.modelConfig().getId()))
                .map(candidate -> {
                    AiModelConfig model = candidate.modelConfig();
                    return String.join("|",
                            String.valueOf(model.getId()),
                            normalize(model.getProviderName()),
                            normalize(model.getModelName()),
                            normalize(model.getModelVersion()),
                            normalize(model.getBaseUrl()),
                            String.valueOf(candidate.priority()),
                            String.valueOf(candidate.weight()));
                })
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return sha256(normalize(tenantId) + "\n" + canonicalRoutes);
    }

    private String sha256(String canonical) {
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
