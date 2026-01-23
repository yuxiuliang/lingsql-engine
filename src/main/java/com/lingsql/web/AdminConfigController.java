package com.lingsql.web;

import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.agent.repository.AiModelConfigRepository;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.schema.entity.JoinRule;
import com.lingsql.schema.entity.SemanticLexiconItem;
import com.lingsql.schema.repository.JoinRuleRepository;
import com.lingsql.schema.repository.SemanticLexiconRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 查询租户配置列表。
     *
     * @return 租户配置列表
     * @author 余炘洋
     * @date 2026-01-23
     */
    @GetMapping("/tenant")
    public ApiResponse<List<TenantDbConfig>> listTenants() {
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
    public ApiResponse<TenantDbConfig> createTenant(@RequestBody TenantDbConfig request) {
        request.setId(null);
        request.setTenantId(normalizeTenantId(request.getTenantId()));
        return ApiResponse.success(tenantDbConfigRepository.save(request));
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
    public ApiResponse<TenantDbConfig> updateTenant(@PathVariable Long id, @RequestBody TenantDbConfig request) {
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
        return ApiResponse.success(tenantDbConfigRepository.save(config));
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
    public ApiResponse<String> deleteTenant(@PathVariable Long id) {
        tenantDbConfigRepository.deleteById(id);
        return ApiResponse.success("租户配置已删除");
    }

    /**
     * 查询模型配置列表。
     *
     * @return 模型配置列表
     * @author 余炘洋
     * @date 2026-01-23
     */
    @GetMapping("/model")
    public ApiResponse<List<AiModelConfig>> listModels() {
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
    public ApiResponse<AiModelConfig> createModel(@RequestBody AiModelConfig request) {
        request.setId(null);
        return ApiResponse.success(aiModelConfigRepository.save(request));
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
    public ApiResponse<AiModelConfig> updateModel(@PathVariable Long id, @RequestBody AiModelConfig request) {
        AiModelConfig config = aiModelConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模型配置不存在"));
        config.setProviderName(request.getProviderName());
        config.setModelName(request.getModelName());
        config.setBaseUrl(request.getBaseUrl());
        config.setApiKey(request.getApiKey());
        config.setIsActive(request.getIsActive());
        config.setWeight(request.getWeight());
        config.setPriority(request.getPriority());
        config.setAutoRecover(request.getAutoRecover());
        config.setCooldownSeconds(request.getCooldownSeconds());
        return ApiResponse.success(aiModelConfigRepository.save(config));
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
    public ApiResponse<String> deleteModel(@PathVariable Long id) {
        aiModelConfigRepository.deleteById(id);
        return ApiResponse.success("模型配置已删除");
    }

    /**
     * 查询语义字典列表。
     *
     * @return 语义字典列表
     * @author 余炘洋
     * @date 2026-01-23
     */
    @GetMapping("/semantic")
    public ApiResponse<List<SemanticLexiconItem>> listSemantic() {
        return ApiResponse.success(semanticLexiconRepository.findAll());
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
    public ApiResponse<SemanticLexiconItem> createSemantic(@RequestBody SemanticLexiconItem request) {
        request.setId(null);
        request.setTenantId(normalizeTenantId(request.getTenantId()));
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
    public ApiResponse<SemanticLexiconItem> updateSemantic(@PathVariable Long id, @RequestBody SemanticLexiconItem request) {
        SemanticLexiconItem item = semanticLexiconRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("语义字典不存在"));
        item.setTenantId(normalizeTenantId(request.getTenantId()));
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
    public ApiResponse<String> deleteSemantic(@PathVariable Long id) {
        semanticLexiconRepository.deleteById(id);
        return ApiResponse.success("语义字典已删除");
    }

    /**
     * 查询 Join 规则列表。
     *
     * @return Join 规则列表
     * @author 余炘洋
     * @date 2026-01-23
     */
    @GetMapping("/join-rule")
    public ApiResponse<List<JoinRule>> listJoinRules() {
        return ApiResponse.success(joinRuleRepository.findAll());
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
    public ApiResponse<JoinRule> createJoinRule(@RequestBody JoinRule request) {
        request.setId(null);
        request.setTenantId(normalizeTenantId(request.getTenantId()));
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
    public ApiResponse<JoinRule> updateJoinRule(@PathVariable Long id, @RequestBody JoinRule request) {
        JoinRule item = joinRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Join 规则不存在"));
        item.setTenantId(normalizeTenantId(request.getTenantId()));
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
    public ApiResponse<String> deleteJoinRule(@PathVariable Long id) {
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
}

