package com.lingsql.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.agent.repository.AiModelConfigRepository;
import com.lingsql.audit.model.AuditLogCommand;
import com.lingsql.audit.service.AuditLogService;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.security.SecureTenantResolver;
import com.lingsql.schema.entity.JoinRule;
import com.lingsql.schema.entity.SemanticLexiconItem;
import com.lingsql.schema.repository.JoinRuleRepository;
import com.lingsql.schema.repository.SemanticLexiconRepository;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
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

import java.time.LocalDateTime;
import java.util.List;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * 配置管理接口，提供租户、模型、语义字典与 Join 规则的管理能力。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@RestController
@RequestMapping("/api/v1/admin/config")
@RequiredArgsConstructor
public class AdminConfigController {

    private final TenantDbConfigRepository tenantDbConfigRepository;
    private final AiModelConfigRepository aiModelConfigRepository;
    private final SemanticLexiconRepository semanticLexiconRepository;
    private final JoinRuleRepository joinRuleRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

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
        return ApiResponse.success(tenantDbConfigRepository.findAll());
    }

    /**
     * 新增租户配置。
     *
     * @param request 租户配置
     * @return 新增结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @PostMapping("/tenant")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:WRITE')")
    public ApiResponse<TenantDbConfig> createTenant(@RequestBody TenantDbConfig request) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        request.setId(null);
        request.setTenantId(normalizeTenantId(request.getTenantId()));
        try {
            TenantDbConfig saved = tenantDbConfigRepository.save(request);
            recordAudit("TENANT_CREATE", "TENANT", request, true, null);
            return ApiResponse.success(saved);
        } catch (Exception ex) {
            recordAudit("TENANT_CREATE", "TENANT", request, false, ex.getMessage());
            return ApiResponse.error(ex.getMessage());
        }
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
        String driverClass = request.getDriverClass();
        String url = request.getJdbcUrl();
        if (driverClass == null || driverClass.isBlank()) {
            driverClass = "com.mysql.cj.jdbc.Driver";
        }
        try {
            Class.forName(driverClass);
            try (Connection ignored = DriverManager.getConnection(url, request.getUsername(), request.getPassword())) {
                return ApiResponse.success("连接成功");
            }
        } catch (Exception ex) {
            return ApiResponse.error("连接失败: " + ex.getMessage());
        }
    }

    /**
     * 更新租户配置。
     *
     * @param id      配置ID
     * @param request 租户配置
     * @return 更新结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @PutMapping("/tenant/{id}")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:WRITE')")
    public ApiResponse<TenantDbConfig> updateTenant(@PathVariable Long id, @RequestBody TenantDbConfig request) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        TenantDbConfig config = tenantDbConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("租户配置不存在"));
        config.setTenantId(normalizeTenantId(request.getTenantId()));
        config.setJdbcUrl(request.getJdbcUrl());
        config.setUsername(request.getUsername());
        config.setPassword(request.getPassword());
        config.setDriverClass(request.getDriverClass());
        config.setIsolationMode(request.getIsolationMode());
        config.setLogicColumn(request.getLogicColumn());
        config.setWhiteListTables(request.getWhiteListTables());
        config.setWhiteListColumns(request.getWhiteListColumns());
        config.setSensitiveColumns(request.getSensitiveColumns());
        config.setStatus(request.getStatus());
        try {
            TenantDbConfig saved = tenantDbConfigRepository.save(config);
            recordAudit("TENANT_UPDATE", "TENANT", request, true, null);
            return ApiResponse.success(saved);
        } catch (Exception ex) {
            recordAudit("TENANT_UPDATE", "TENANT", request, false, ex.getMessage());
            return ApiResponse.error(ex.getMessage());
        }
    }

    /**
     * 删除租户配置。
     *
     * @param id 配置ID
     * @return 删除结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @DeleteMapping("/tenant/{id}")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:DELETE')")
    public ApiResponse<String> deleteTenant(@PathVariable Long id) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        try {
            tenantDbConfigRepository.deleteById(id);
            recordAudit("TENANT_DELETE", "TENANT", "id=" + id, true, null);
            return ApiResponse.success("租户配置已删除");
        } catch (Exception ex) {
            recordAudit("TENANT_DELETE", "TENANT", "id=" + id, false, ex.getMessage());
            return ApiResponse.error(ex.getMessage());
        }
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
        return ApiResponse.success(aiModelConfigRepository.findAll());
    }

    /**
     * 新增模型配置。
     *
     * @param request 模型配置
     * @return 新增结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @PostMapping("/model")
    @PreAuthorize("hasAuthority('ADMIN:MODEL:WRITE')")
    public ApiResponse<AiModelConfig> createModel(@RequestBody AiModelConfig request) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        request.setId(null);
        try {
            AiModelConfig saved = aiModelConfigRepository.save(request);
            recordAudit("MODEL_CREATE", "MODEL", request, true, null);
            return ApiResponse.success(saved);
        } catch (Exception ex) {
            recordAudit("MODEL_CREATE", "MODEL", request, false, ex.getMessage());
            return ApiResponse.error(ex.getMessage());
        }
    }

    /**
     * 更新模型配置。
     *
     * @param id      模型ID
     * @param request 模型配置
     * @return 更新结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @PutMapping("/model/{id}")
    @PreAuthorize("hasAuthority('ADMIN:MODEL:WRITE')")
    public ApiResponse<AiModelConfig> updateModel(@PathVariable Long id, @RequestBody AiModelConfig request) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        AiModelConfig config = aiModelConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模型配置不存在"));
        config.setProviderName(request.getProviderName());
        config.setModelName(request.getModelName());
        config.setModelVersion(request.getModelVersion());
        config.setBaseUrl(request.getBaseUrl());
        config.setApiKey(request.getApiKey());
        config.setIsActive(request.getIsActive());
        config.setWeight(request.getWeight());
        config.setPriority(request.getPriority());
        config.setAutoRecover(request.getAutoRecover());
        config.setCooldownSeconds(request.getCooldownSeconds());
        try {
            AiModelConfig saved = aiModelConfigRepository.save(config);
            recordAudit("MODEL_UPDATE", "MODEL", request, true, null);
            return ApiResponse.success(saved);
        } catch (Exception ex) {
            recordAudit("MODEL_UPDATE", "MODEL", request, false, ex.getMessage());
            return ApiResponse.error(ex.getMessage());
        }
    }

    /**
     * 删除模型配置。
     *
     * @param id 模型ID
     * @return 删除结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @DeleteMapping("/model/{id}")
    @PreAuthorize("hasAuthority('ADMIN:MODEL:DELETE')")
    public ApiResponse<String> deleteModel(@PathVariable Long id) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        try {
            aiModelConfigRepository.deleteById(id);
            recordAudit("MODEL_DELETE", "MODEL", "id=" + id, true, null);
            return ApiResponse.success("模型配置已删除");
        } catch (Exception ex) {
            recordAudit("MODEL_DELETE", "MODEL", "id=" + id, false, ex.getMessage());
            return ApiResponse.error(ex.getMessage());
        }
    }

    /**
     * 记录审计日志。
     *
     * @param action   动作
     * @param resource 资源
     * @param request  请求体
     * @param success  是否成功
     * @param error    错误信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    private void recordAudit(String action, String resource, Object request, boolean success, String error) {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        if (session == null) {
            return;
        }
        AuditLogCommand command = new AuditLogCommand();
        command.setTenantId(session.getTenantId());
        command.setUserId(session.getUserId());
        command.setUsername(session.getUsername());
        command.setAction(action);
        command.setResource(resource);
        command.setDetail(serialize(request));
        command.setSuccess(success);
        command.setErrorMessage(error);
        auditLogService.record(command);
    }

    /**
     * 序列化对象。
     *
     * @param value 对象
     * @return JSON 字符串
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
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
                .orElseThrow(() -> new RuntimeException("语义字典不存在"));
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
                .orElseThrow(() -> new RuntimeException("语义字典不存在"));
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
                .orElseThrow(() -> new RuntimeException("Join 规则不存在"));
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
                .orElseThrow(() -> new RuntimeException("Join 规则不存在"));
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
            throw new RuntimeException("映射表达式不能为空");
        }
        try {
            CCJSqlParserUtil.parse("SELECT " + mapping + " FROM dual");
        } catch (Exception ex) {
            throw new RuntimeException("映射表达式语法错误: " + ex.getMessage());
        }
    }
}
