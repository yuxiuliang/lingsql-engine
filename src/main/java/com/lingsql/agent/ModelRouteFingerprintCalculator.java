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
 */
@Component
public class ModelRouteFingerprintCalculator {

    public String calculate(List<AiModelConfig> models) {
        String canonical = models == null ? "" : models.stream()
                .sorted(Comparator.comparing(AiModelConfig::getPriority, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(AiModelConfig::getId, Comparator.nullsLast(Long::compareTo)))
                .map(model -> String.join("|",
                        String.valueOf(model.getId()),
                        normalize(model.getProviderName()),
                        normalize(model.getModelName()),
                        normalize(model.getModelVersion()),
                        String.valueOf(model.getPriority()),
                        String.valueOf(model.getWeight())))
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
