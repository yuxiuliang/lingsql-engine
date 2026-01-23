package com.lingsql.web;

import com.lingsql.common.result.ApiResponse;
import com.lingsql.metadata.model.AiSuggestRequest;
import com.lingsql.metadata.model.AiSuggestResponse;
import com.lingsql.metadata.model.IsolationUpdateRequest;
import com.lingsql.metadata.model.MetadataTableView;
import com.lingsql.metadata.model.MetadataUpdateRequest;
import com.lingsql.metadata.service.MetadataExtService;
import com.lingsql.schema.MetadataCacheService;
import com.lingsql.schema.model.TableInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 元数据字典管理接口。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@RestController
@RequestMapping("/api/v1/admin/metadata")
@RequiredArgsConstructor
public class MetadataController extends BaseController {

    private final MetadataCacheService metadataCacheService;
    private final MetadataExtService metadataExtService;

    /**
     * 获取缓存 Schema 列表。
     *
     * @param tenantId 租户标识
     * @return Schema 列表
     * @author 余炘洋
     * @date 2026-01-23
     */
    @GetMapping("/schema")
    @PreAuthorize("hasAuthority('ADMIN:METADATA:READ')")
    public ApiResponse<List<TableInfo>> listSchema(@RequestParam(required = false) String tenantId) {
        String resolvedTenantId = resolveTenantId(tenantId);
        List<TableInfo> tables = metadataCacheService.getCachedSchema(resolvedTenantId);
        if (tables == null) {
            tables = List.of();
        }
        return ApiResponse.success(tables);
    }

    /**
     * 获取表字段详情。
     *
     * @param tenantId 租户标识
     * @param tableName 表名
     * @return 表详情
     * @author 余炘洋
     * @date 2026-01-23
     */
    @GetMapping("/table")
    @PreAuthorize("hasAuthority('ADMIN:METADATA:READ')")
    public ApiResponse<MetadataTableView> getTableDetail(
            @RequestParam(required = false) String tenantId,
            @RequestParam String tableName
    ) {
        String resolvedTenantId = resolveTenantId(tenantId);
        List<TableInfo> tables = metadataCacheService.getCachedSchema(resolvedTenantId);
        if (tables == null) {
            return ApiResponse.success(null);
        }
        TableInfo target = tables.stream()
                .filter(table -> table.getTableName().equalsIgnoreCase(tableName))
                .findFirst()
                .orElse(null);
        return ApiResponse.success(metadataExtService.buildTableView(resolvedTenantId, target));
    }

    /**
     * 更新字段业务注释。
     *
     * @param request 请求体
     * @return 更新结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @PostMapping("/field")
    @PreAuthorize("hasAuthority('ADMIN:METADATA:WRITE')")
    public ApiResponse<String> updateField(@RequestBody MetadataUpdateRequest request) {
        String resolvedTenantId = resolveTenantId(request.getTenantId());
        metadataExtService.updateBusinessComment(resolvedTenantId, request.getTableName(), request.getColumnName(),
                request.getBusinessComment());
        return ApiResponse.success("已更新");
    }

    /**
     * 更新隔离字段。
     *
     * @param request 请求体
     * @return 更新结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @PutMapping("/isolated")
    @PreAuthorize("hasAuthority('ADMIN:METADATA:WRITE')")
    public ApiResponse<String> updateIsolation(@RequestBody IsolationUpdateRequest request) {
        String resolvedTenantId = resolveTenantId(request.getTenantId());
        metadataExtService.updateIsolationField(resolvedTenantId, request.getTableName(), request.getColumnName(),
                request.isIsolated());
        return ApiResponse.success("已更新");
    }

    /**
     * AI 语义解析。
     *
     * @param request 请求体
     * @return 解析结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @PostMapping("/ai-suggest")
    @PreAuthorize("hasAuthority('ADMIN:METADATA:AI')")
    public ApiResponse<AiSuggestResponse> aiSuggest(@RequestBody AiSuggestRequest request) {
        request.setTenantId(resolveTenantId(request.getTenantId()));
        return ApiResponse.success(metadataExtService.aiSuggest(request));
    }

    /**
     * 获取租户标识。
     *
     * @param tenantId 请求租户
     * @return 租户标识
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String resolveTenantId(String tenantId) {
        return resolveSecureTenantId(tenantId);
    }
}
