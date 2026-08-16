package com.lingsql.web;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.agent.ModelHealthService;
import com.lingsql.agent.ModelProbeService;
import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.agent.model.ModelProbeView;
import com.lingsql.agent.repository.AiModelConfigRepository;
import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.schema.MetadataCacheService;
import com.lingsql.schema.SchemaSynchronizationService;
import com.lingsql.schema.SemanticConfigService;
import com.lingsql.schema.model.SchemaSynchronizationReceipt;
import com.lingsql.schema.model.TableInfo;
import com.lingsql.web.model.ModelHealthView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 管理接口，负责手动触发元数据刷新。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController extends BaseController {

    private final SchemaSynchronizationService schemaSynchronizationService;
    private final MetadataCacheService cacheService;
    private final SemanticConfigService semanticConfigService;
    private final ModelHealthService modelHealthService;
    private final AiModelConfigRepository modelConfigRepository;
    private final ModelProbeService modelProbeService;
    private final AuditOperationExecutor auditOperationExecutor;

    /**
     * 对已保存模型执行一次真实聊天调用验证。
     *
     * @param modelId 模型ID
     * @param http 请求
     * @return 验证结果
     * @author 余炘洋
     * @date 2026-08-15
     */
    @PostMapping("/model/{modelId}/probe")
    @PreAuthorize("hasAuthority('ADMIN:MODEL:HEALTH')")
    public ApiResponse<ModelProbeView> probeModel(@PathVariable Long modelId, HttpServletRequest http) {
        return auditOperationExecutor.execute(
                "MODEL_PROBE",
                "MODEL",
                "id=" + modelId,
                http,
                () -> modelProbeService.probe(modelId)
        );
    }

    /**
     * 手动触发指定租户的元数据同步。
     *
     * @param tenantId 租户标识
     * @return 执行结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    @PostMapping("/sync-schema/{tenantId}")
    @PreAuthorize("hasAuthority('ADMIN:SCHEMA:SYNC')")
    public ApiResponse<SchemaSynchronizationReceipt> syncSchema(@PathVariable String tenantId) {
        String resolvedTenantId = resolveSecureTenantId(tenantId);
        return ApiResponse.success(schemaSynchronizationService.synchronize(resolvedTenantId));
    }

    /**
     * 清理语义配置缓存，便于即时生效。
     *
     * @param tenantId 租户标识
     * @return 清理结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    @PostMapping("/refresh-semantic/{tenantId}")
    @PreAuthorize("hasAuthority('ADMIN:SEMANTIC:REFRESH')")
    public ApiResponse<String> refreshSemantic(@PathVariable String tenantId) {
        String resolvedTenantId = resolveSecureTenantId(tenantId);
        semanticConfigService.clearCache(resolvedTenantId);
        return ApiResponse.success("租户 [" + resolvedTenantId + "] 语义字典与 Join 规则缓存已清理");
    }

    /**
     * 手动启用模型并清空健康状态。
     *
     * @param modelId 模型ID
     * @return 执行结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    @PostMapping("/model/enable/{modelId}")
    @PreAuthorize("hasAuthority('ADMIN:MODEL:ENABLE')")
    public ApiResponse<String> enableModel(@PathVariable Long modelId) {
        modelHealthService.enableModel(modelId);
        return ApiResponse.success("模型 [" + modelId + "] 已启用");
    }

    /**
     * 手动禁用模型并清空健康状态。
     *
     * @param modelId 模型ID
     * @return 执行结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    @PostMapping("/model/disable/{modelId}")
    @PreAuthorize("hasAuthority('ADMIN:MODEL:DISABLE')")
    public ApiResponse<String> disableModel(@PathVariable Long modelId) {
        modelHealthService.disableModel(modelId);
        return ApiResponse.success("模型 [" + modelId + "] 已禁用");
    }

    /**
     * 查询模型健康状态。
     *
     * @return 模型健康列表
     * @author 余炘洋
     * @date 2026-01-22
     */
    @GetMapping("/model/health")
    @PreAuthorize("hasAuthority('ADMIN:MODEL:HEALTH')")
    public ApiResponse<List<ModelHealthView>> listModelHealth() {
        List<AiModelConfig> models = modelConfigRepository.findAll();
        List<ModelHealthView> views = models.stream()
                .map(model -> {
                    ModelHealthService.ModelHealthSnapshot snapshot = modelHealthService.getSnapshot(model.getId());
                    return ModelHealthView.from(model, snapshot);
                })
                .toList();
        return ApiResponse.success(views);
    }

    /**
     * 获取缓存中的 Schema 信息。
     *
     * @param tenantId 租户标识
     * @return 表结构列表
     * @author 余炘洋
     * @date 2026-01-23
     */
    @GetMapping("/schema/{tenantId}")
    @PreAuthorize("hasAuthority('ADMIN:SCHEMA:VIEW')")
    public ApiResponse<List<TableInfo>> getSchema(@PathVariable String tenantId) {
        String resolvedTenantId = resolveSecureTenantId(tenantId);
        List<TableInfo> tables = cacheService.getCachedSchema(resolvedTenantId);
        if (tables == null) {
            tables = List.of();
        }
        return ApiResponse.success(tables);
    }
}
