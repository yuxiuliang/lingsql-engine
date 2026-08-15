package com.lingsql.web;

import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.agent.ModelConfigService;
import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.TenantDataSourceConfigService;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.security.SecureTenantResolver;
import com.lingsql.schema.entity.JoinRule;
import com.lingsql.schema.entity.SemanticLexiconItem;
import com.lingsql.schema.repository.JoinRuleRepository;
import com.lingsql.schema.repository.SemanticLexiconRepository;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置管理接口，提供租户、模型、语义字典与 Join 规则的管理能力。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@RestController
@RequestMapping("/api/v1/admin/config")
@RequiredArgsConstructor
@Slf4j
public class AdminConfigController {

    private final TenantDataSourceConfigService tenantDataSourceConfigService;
    private final ModelConfigService modelConfigService;
    private final SemanticLexiconRepository semanticLexiconRepository;
    private final JoinRuleRepository joinRuleRepository;
    private final AuditOperationExecutor auditOperationExecutor;

    /**
     * 查询租户配置列表。
     *
     * @return 租户配置列表
     * @author 余炘洋
     * @date 2026-01-23
     */
    @GetMapping("/tenant")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:READ')")
    public ApiResponse<List<TenantDbConfig>> listTenants() {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(tenantDataSourceConfigService.list());
    }

    /**
     * 新增租户配置。
     *
     * @param request 租户配置
     * @param http    请求
     * @return 新增结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @PostMapping("/tenant")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:WRITE')")
    public ApiResponse<TenantDbConfig> createTenant(@RequestBody TenantDbConfig request, HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "TENANT_CREATE",
                "TENANT",
                dataSourceAuditSummary(null, request, "CREATE"),
                http,
                () -> tenantDataSourceConfigService.create(request)
        );
    }

    /**
     * 测试租户数据库连接。
     *
     * @param request 租户配置
     * @return 测试结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @PostMapping("/tenant/test-connection")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:TEST')")
    public ApiResponse<String> testTenantConnection(@RequestBody TenantDbConfig request) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        tenantDataSourceConfigService.testConnection(request);
        return ApiResponse.success("连接成功");
    }

    /**
     * 更新租户配置。
     *
     * @param id      配置ID
     * @param request 租户配置
     * @param http    请求
     * @return 更新结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @PutMapping("/tenant/{id}")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:WRITE')")
    public ApiResponse<TenantDbConfig> updateTenant(@PathVariable Long id, @RequestBody TenantDbConfig request, HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "TENANT_UPDATE",
                "TENANT",
                dataSourceAuditSummary(id, request, "UPDATE"),
                http,
                () -> tenantDataSourceConfigService.update(id, request)
        );
    }

    private Map<String, Object> dataSourceAuditSummary(Long id,
                                                       TenantDbConfig request,
                                                       String operation) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("configId", id);
        summary.put("tenantId", request == null ? null : request.getTenantId());
        summary.put("operation", operation);
        return summary;
    }

    /**
     * 删除租户配置。
     *
     * @param id 配置ID
     * @param http 请求
     * @return 删除结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @DeleteMapping("/tenant/{id}")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:DELETE')")
    public ApiResponse<String> deleteTenant(@PathVariable Long id, HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.executeVoid(
                "TENANT_DELETE",
                "TENANT",
                "id=" + id,
                http,
                () -> tenantDataSourceConfigService.delete(id),
                "租户配置已删除"
        );
    }

    /**
     * 查询模型配置列表。
     *
     * @return 模型配置列表
     * @author 余炘洋
     * @date 2026-01-23
     */
    @GetMapping("/model")
    @PreAuthorize("hasAuthority('ADMIN:MODEL:READ')")
    public ApiResponse<List<AiModelConfig>> listModels() {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(modelConfigService.list());
    }

    /**
     * 新增模型配置。
     *
     * @param request 模型配置
     * @param http    请求
     * @return 新增结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @PostMapping("/model")
    @PreAuthorize("hasAuthority('ADMIN:MODEL:WRITE')")
    public ApiResponse<AiModelConfig> createModel(@RequestBody AiModelConfig request, HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "MODEL_CREATE",
                "MODEL",
                request,
                http,
                () -> modelConfigService.create(request)
        );
    }

    /**
     * 更新模型配置。
     *
     * @param id      模型ID
     * @param request 模型配置
     * @param http    请求
     * @return 更新结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @PutMapping("/model/{id}")
    @PreAuthorize("hasAuthority('ADMIN:MODEL:WRITE')")
    public ApiResponse<AiModelConfig> updateModel(@PathVariable Long id, @RequestBody AiModelConfig request, HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "MODEL_UPDATE",
                "MODEL",
                request,
                http,
                () -> modelConfigService.update(id, request)
        );
    }

    /**
     * 删除模型配置。
     *
     * @param id 模型ID
     * @param http 请求
     * @return 删除结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @DeleteMapping("/model/{id}")
    @PreAuthorize("hasAuthority('ADMIN:MODEL:DELETE')")
    public ApiResponse<String> deleteModel(@PathVariable Long id, HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.executeVoid(
                "MODEL_DELETE",
                "MODEL",
                "id=" + id,
                http,
                () -> modelConfigService.delete(id),
                "模型配置已删除"
        );
    }

    /**
     * 判断是否系统管理员。
     *
     * @return 是否系统管理员
     * @author 余炘洋
     * @date 2026-01-26
     */
    private boolean isSysAdmin() {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        return com.lingsql.rbac.security.SecureTenantResolver.isSysAdmin(session);
    }

    /**
     * 查询语义字典列表。
     *
     * @param tenantId 租户标识
     * @return 语义字典列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    @GetMapping("/semantic")
    @PreAuthorize("hasAuthority('ADMIN:SEMANTIC:READ')")
    public ApiResponse<List<SemanticLexiconItem>> listSemantic(@RequestParam(required = false) String tenantId) {
        String resolvedTenantId = SecureTenantResolver.resolveSecureTenantId(tenantId);
        if (isSysAdmin() && !StringUtils.hasText(tenantId)) {
            return ApiResponse.success(semanticLexiconRepository.findAll());
        }
        return ApiResponse.success(semanticLexiconRepository.findByTenantIdOrTenantIdIsNull(resolvedTenantId));
    }

    /**
     * 新增语义字典。
     *
     * @param request 语义字典
     * @return 新增结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @PostMapping("/semantic")
    @PreAuthorize("hasAuthority('ADMIN:SEMANTIC:WRITE')")
    public ApiResponse<SemanticLexiconItem> createSemantic(@RequestBody SemanticLexiconItem request) {
        validateSemanticMapping(request.getMapping());
        request.setId(null);
        String resolvedTenantId = SecureTenantResolver.resolveSecureTenantId(request.getTenantId());
        request.setTenantId(normalizeTenantId(resolvedTenantId));
        request.setCreateTime(LocalDateTime.now());
        request.setUpdateTime(LocalDateTime.now());
        return ApiResponse.success(semanticLexiconRepository.save(request));
    }

    /**
     * 更新语义字典。
     *
     * @param id      记录ID
     * @param request 语义字典
     * @return 更新结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @PutMapping("/semantic/{id}")
    @PreAuthorize("hasAuthority('ADMIN:SEMANTIC:WRITE')")
    public ApiResponse<SemanticLexiconItem> updateSemantic(@PathVariable Long id, @RequestBody SemanticLexiconItem request) {
        validateSemanticMapping(request.getMapping());
        SemanticLexiconItem item = semanticLexiconRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "语义字典不存在"));
        String resolvedTenantId = SecureTenantResolver.resolveSecureTenantId(request.getTenantId());
        if (!isSysAdmin()) {
            if (!StringUtils.hasText(item.getTenantId()) || !item.getTenantId().equalsIgnoreCase(resolvedTenantId)) {
                throw new AccessDeniedException("无权限访问");
            }
        }
        item.setTenantId(normalizeTenantId(isSysAdmin() ? request.getTenantId() : resolvedTenantId));
        item.setTerm(request.getTerm());
        item.setMapping(request.getMapping());
        item.setDescription(request.getDescription());
        item.setStatus(request.getStatus());
        item.setUpdateTime(LocalDateTime.now());
        return ApiResponse.success(semanticLexiconRepository.save(item));
    }

    /**
     * 删除语义字典。
     *
     * @param id 记录ID
     * @return 删除结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @DeleteMapping("/semantic/{id}")
    @PreAuthorize("hasAuthority('ADMIN:SEMANTIC:DELETE')")
    public ApiResponse<String> deleteSemantic(@PathVariable Long id) {
        SemanticLexiconItem item = semanticLexiconRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "语义字典不存在"));
        if (!isSysAdmin()) {
            String resolvedTenantId = SecureTenantResolver.resolveSecureTenantId(null);
            if (!StringUtils.hasText(item.getTenantId()) || !item.getTenantId().equalsIgnoreCase(resolvedTenantId)) {
                throw new AccessDeniedException("无权限访问");
            }
        }
        semanticLexiconRepository.deleteById(id);
        return ApiResponse.success("语义字典已删除");
    }

    /**
     * 查询 Join 规则列表。
     *
     * @param tenantId 租户标识
     * @return Join 规则列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    @GetMapping("/join-rule")
    @PreAuthorize("hasAuthority('ADMIN:JOIN:READ')")
    public ApiResponse<List<JoinRule>> listJoinRules(@RequestParam(required = false) String tenantId) {
        String resolvedTenantId = SecureTenantResolver.resolveSecureTenantId(tenantId);
        if (isSysAdmin() && !StringUtils.hasText(tenantId)) {
            return ApiResponse.success(joinRuleRepository.findAll());
        }
        return ApiResponse.success(joinRuleRepository.findByTenantIdOrTenantIdIsNull(resolvedTenantId));
    }

    /**
     * 新增 Join 规则。
     *
     * @param request Join 规则
     * @return 新增结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @PostMapping("/join-rule")
    @PreAuthorize("hasAuthority('ADMIN:JOIN:WRITE')")
    public ApiResponse<JoinRule> createJoinRule(@RequestBody JoinRule request) {
        request.setId(null);
        String resolvedTenantId = SecureTenantResolver.resolveSecureTenantId(request.getTenantId());
        request.setTenantId(normalizeTenantId(resolvedTenantId));
        request.setCreateTime(LocalDateTime.now());
        request.setUpdateTime(LocalDateTime.now());
        return ApiResponse.success(joinRuleRepository.save(request));
    }

    /**
     * 更新 Join 规则。
     *
     * @param id      记录ID
     * @param request Join 规则
     * @return 更新结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @PutMapping("/join-rule/{id}")
    @PreAuthorize("hasAuthority('ADMIN:JOIN:WRITE')")
    public ApiResponse<JoinRule> updateJoinRule(@PathVariable Long id, @RequestBody JoinRule request) {
        JoinRule item = joinRuleRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Join 规则不存在"));
        String resolvedTenantId = SecureTenantResolver.resolveSecureTenantId(request.getTenantId());
        if (!isSysAdmin()) {
            if (!StringUtils.hasText(item.getTenantId()) || !item.getTenantId().equalsIgnoreCase(resolvedTenantId)) {
                throw new AccessDeniedException("无权限访问");
            }
        }
        item.setTenantId(normalizeTenantId(isSysAdmin() ? request.getTenantId() : resolvedTenantId));
        item.setLeftTable(request.getLeftTable());
        item.setRightTable(request.getRightTable());
        item.setOnExpr(request.getOnExpr());
        item.setDescription(request.getDescription());
        item.setStatus(request.getStatus());
        item.setUpdateTime(LocalDateTime.now());
        return ApiResponse.success(joinRuleRepository.save(item));
    }

    /**
     * 删除 Join 规则。
     *
     * @param id 记录ID
     * @return 删除结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @DeleteMapping("/join-rule/{id}")
    @PreAuthorize("hasAuthority('ADMIN:JOIN:DELETE')")
    public ApiResponse<String> deleteJoinRule(@PathVariable Long id) {
        JoinRule item = joinRuleRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Join 规则不存在"));
        if (!isSysAdmin()) {
            String resolvedTenantId = SecureTenantResolver.resolveSecureTenantId(null);
            if (!StringUtils.hasText(item.getTenantId()) || !item.getTenantId().equalsIgnoreCase(resolvedTenantId)) {
                throw new AccessDeniedException("无权限访问");
            }
        }
        joinRuleRepository.deleteById(id);
        return ApiResponse.success("Join 规则已删除");
    }

    /**
     * 规范化租户ID，空字符串返回 null。
     *
     * @param tenantId 租户标识
     * @return 规范化后的租户标识
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String normalizeTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        return tenantId.trim();
    }

    /**
     * 校验语义映射表达式语法。
     *
     * @param mapping 映射表达式
     * @author 余炘洋
     * @date 2026-01-23
     */
    private void validateSemanticMapping(String mapping) {
        if (mapping == null || mapping.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "映射表达式不能为空");
        }
        try {
            CCJSqlParserUtil.parse("SELECT " + mapping + " FROM dual");
        } catch (Exception ex) {
            log.warn("映射表达式语法校验失败: mapping={}", mapping, ex);
            throw new BizException(ErrorCode.INVALID_PARAM, "映射表达式语法错误", ex);
        }
    }

}
