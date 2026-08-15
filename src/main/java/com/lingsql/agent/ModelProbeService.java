package com.lingsql.agent;

import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.agent.model.ModelProbeView;
import com.lingsql.agent.repository.AiModelConfigRepository;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 模型即时验证服务，负责归一化健康状态与对外错误信息。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Service
@RequiredArgsConstructor
public class ModelProbeService {

    private final AiModelConfigRepository repository;
    private final ModelProbeClient probeClient;
    private final ModelHealthService healthService;

    public ModelProbeView probe(Long modelId) {
        AiModelConfig config = repository.findById(modelId)
                .orElseThrow(() -> new BizException(ErrorCode.MODEL_NOT_FOUND, "模型不存在"));
        long startedAt = System.nanoTime();
        ModelProbeClient.ProbeResult result = probeClient.probe(config);
        long costMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        LocalDateTime checkedAt = LocalDateTime.now();
        if (result.isSuccess()) {
            healthService.recordSuccess(config, costMs);
            persistProbeResult(config, "HEALTHY", checkedAt, costMs, null);
            return new ModelProbeView(modelId, "HEALTHY", checkedAt, costMs, null, "模型验证通过");
        }
        String errorCode = classifyError(result.getReason());
        String message = resolveMessage(errorCode);
        healthService.recordFailure(config, message);
        persistProbeResult(config, "FAILED", checkedAt, costMs, errorCode);
        return new ModelProbeView(modelId, "FAILED", checkedAt, costMs, errorCode, message);
    }

    private void persistProbeResult(AiModelConfig config,
                                    String status,
                                    LocalDateTime checkedAt,
                                    long costMs,
                                    String errorCode) {
        config.setLastProbeStatus(status);
        config.setLastProbeTime(checkedAt);
        config.setLastProbeCostMs(costMs);
        config.setLastProbeErrorCode(errorCode);
        repository.save(config);
    }

    private String classifyError(String reason) {
        String normalized = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        if (normalized.contains("401") || normalized.contains("403") || normalized.contains("unauthorized")) {
            return "AUTH_FAILED";
        }
        if (normalized.contains("timed out") || normalized.contains("timeout") || normalized.contains("超时")) {
            return "TIMEOUT";
        }
        if (normalized.contains("model") && (normalized.contains("not found") || normalized.contains("404"))) {
            return "MODEL_NOT_FOUND";
        }
        if (normalized.contains("connection") || normalized.contains("connect") || normalized.contains("refused")) {
            return "CONNECTION_FAILED";
        }
        return "PROBE_FAILED";
    }

    private String resolveMessage(String errorCode) {
        return switch (errorCode) {
            case "AUTH_FAILED" -> "模型鉴权失败，请检查访问密钥";
            case "TIMEOUT" -> "模型响应超时，请检查网络和服务状态";
            case "MODEL_NOT_FOUND" -> "目标模型不可用，请检查模型名称";
            case "CONNECTION_FAILED" -> "无法连接模型服务，请检查地址和网络";
            default -> "模型验证失败，请检查配置和服务日志";
        };
    }
}
