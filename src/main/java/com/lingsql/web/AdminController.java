package com.lingsql.web;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.agent.ModelHealthService;
import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.agent.repository.AiModelConfigRepository;
import com.lingsql.datasource.DataSourceManager;
import com.lingsql.datasource.TenantDbConfigErrors;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.schema.MetadataCacheService;
import com.lingsql.schema.SchemaScanner;
import com.lingsql.schema.SemanticConfigService;
import com.lingsql.schema.model.TableInfo;
import com.lingsql.web.model.ModelHealthView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import javax.sql.DataSource;
import java.time.LocalDateTime;
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

    private final TenantDbConfigRepository repository;
    private final DataSourceManager dataSourceManager;
    private final SchemaScanner schemaScanner;
    private final MetadataCacheService cacheService;
    private final SemanticConfigService semanticConfigService;
    private final ModelHealthService modelHealthService;
    private final AiModelConfigRepository modelConfigRepository;

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
    public ApiResponse<String> syncSchema(@PathVariable String tenantId) {
        String resolvedTenantId = resolveSecureTenantId(tenantId);
        TenantDbConfig config = repository.findByTenantId(resolvedTenantId)
                .orElseThrow(TenantDbConfigErrors::missingConfig);

        DataSource ds = dataSourceManager.getDataSource(resolvedTenantId);
        List<TableInfo> tables = schemaScanner.scanSchema(resolvedTenantId, ds, config.getWhiteListTables());
        cacheService.cacheSchema(resolvedTenantId, tables);

        config.setSchemaSyncStatus(1);
        config.setLastSyncTime(LocalDateTime.now());
        repository.save(config);

        return ApiResponse.success("租户 [" + resolvedTenantId + "] 元数据同步完成，共扫描 " + tables.size() + " 张表");
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
