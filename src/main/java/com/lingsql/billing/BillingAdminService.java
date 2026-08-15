package com.lingsql.billing;

import com.lingsql.billing.entity.BillingPolicyVersion;
import com.lingsql.billing.entity.TenantBillingOverride;
import com.lingsql.billing.model.BillingLedgerEvidenceView;
import com.lingsql.billing.model.BillingPolicyVersionRequest;
import com.lingsql.billing.model.TenantBillingOverrideRequest;
import com.lingsql.billing.repository.BillingLedgerEventRepository;
import com.lingsql.billing.repository.BillingPolicyVersionRepository;
import com.lingsql.billing.repository.TenantBillingOverrideRepository;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import tools.jackson.databind.ObjectMapper;

/**
 * 计费配置管理服务。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingAdminService {

    private static final int ACTIVE_STATUS = 1;
    private static final int INACTIVE_STATUS = 0;

    private final BillingPolicyVersionRepository policyRepository;
    private final TenantBillingOverrideRepository overrideRepository;
    private final BillingOverrideService billingOverrideService;
    private final BillingLedgerEventRepository ledgerEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * 查询策略版本列表。
     *
     * @return 版本列表
     * @author 余炘洋
     * @date 2026-01-27
     */
    public List<BillingPolicyVersion> listPolicyVersions() {
        return policyRepository.findAll(Sort.by(Sort.Direction.DESC, "effectiveTime"));
    }

    /**
     * 创建策略版本。
     *
     * @param request 创建请求
     * @return 版本实体
     * @author 余炘洋
     * @date 2026-01-27
     */
    public BillingPolicyVersion createPolicyVersion(BillingPolicyVersionRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "计费策略不能为空");
        }
        String versionCode = normalizeCode(request.getVersionCode());
        if (!StringUtils.hasText(versionCode)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "计费版本号不能为空");
        }
        if (policyRepository.findByVersionCode(versionCode).isPresent()) {
            throw new BizException(ErrorCode.CONFLICT, "计费版本已存在");
        }
        BillingPolicyVersion entity = new BillingPolicyVersion();
        entity.setVersionCode(versionCode);
        applyPolicyRequest(entity, request, true);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        BillingPolicyVersion saved = policyRepository.save(entity);
        if (ACTIVE_STATUS == resolveStatus(saved.getStatus())) {
            deactivateOtherPolicies(saved.getId());
        }
        return saved;
    }

    /**
     * 更新策略版本。
     *
     * @param id      版本ID
     * @param request 更新请求
     * @return 版本实体
     * @author 余炘洋
     * @date 2026-01-27
     */
    public BillingPolicyVersion updatePolicyVersion(Long id, BillingPolicyVersionRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "计费策略不能为空");
        }
        BillingPolicyVersion entity = policyRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "计费版本不存在"));
        if (StringUtils.hasText(request.getVersionCode())) {
            String versionCode = normalizeCode(request.getVersionCode());
                if (!versionCode.equals(entity.getVersionCode())
                        && policyRepository.findByVersionCode(versionCode).isPresent()) {
                    throw new BizException(ErrorCode.CONFLICT, "计费版本已存在");
                }
            entity.setVersionCode(versionCode);
        }
        applyPolicyRequest(entity, request, false);
        entity.setUpdateTime(LocalDateTime.now());
        BillingPolicyVersion saved = policyRepository.save(entity);
        if (ACTIVE_STATUS == resolveStatus(saved.getStatus())) {
            deactivateOtherPolicies(saved.getId());
        }
        return saved;
    }

    /**
     * 立即生效指定版本。
     *
     * @param id 版本ID
     * @return 版本实体
     * @author 余炘洋
     * @date 2026-01-27
     */
    public BillingPolicyVersion activatePolicyVersion(Long id) {
        BillingPolicyVersion entity = policyRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "计费版本不存在"));
        entity.setStatus(ACTIVE_STATUS);
        if (entity.getEffectiveTime() == null) {
            entity.setEffectiveTime(LocalDateTime.now());
        }
        entity.setUpdateTime(LocalDateTime.now());
        BillingPolicyVersion saved = policyRepository.save(entity);
        deactivateOtherPolicies(saved.getId());
        return saved;
    }

    /**
     * 查询租户覆盖列表。
     *
     * @return 覆盖列表
     * @author 余炘洋
     * @date 2026-01-27
     */
    public List<TenantBillingOverride> listOverrides() {
        return overrideRepository.findAll(Sort.by(Sort.Direction.DESC, "updateTime"));
    }

    /**
     * 查询租户覆盖。
     *
     * @param tenantId 租户ID
     * @return 覆盖配置
     * @author 余炘洋
     * @date 2026-01-27
     */
    public TenantBillingOverride getOverride(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户ID不能为空");
        }
        return overrideRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "租户覆盖不存在"));
    }

    /**
     * 新增或更新租户覆盖。
     *
     * @param tenantId 租户ID
     * @param request  覆盖请求
     * @return 覆盖实体
     * @author 余炘洋
     * @date 2026-01-27
     */
    public TenantBillingOverride upsertOverride(String tenantId, TenantBillingOverrideRequest request) {
        return billingOverrideService.saveTenantOverride(tenantId, request);
    }

    /**
     * 查询一组真实请求对应的账本证据。
     *
     * @param tenantId 租户ID
     * @param requestIds 请求ID
     * @return 账本证据
     */
    public BillingLedgerEvidenceView ledgerEvidence(String tenantId, List<String> requestIds) {
        if (!StringUtils.hasText(tenantId) || requestIds == null || requestIds.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户ID和请求ID不能为空");
        }
        List<String> normalized = requestIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(value -> value.length() <= 64)
                .distinct()
                .limit(20)
                .toList();
        if (normalized.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "请求ID格式不正确");
        }
        BillingLedgerEventRepository.LedgerEvidenceProjection evidence =
                ledgerEventRepository.findLedgerEvidence(tenantId.trim(), normalized);
        return new BillingLedgerEvidenceView(
                normalized.size(),
                projectionValue(evidence == null ? null : evidence.getMatchedRequestCount()),
                projectionValue(evidence == null ? null : evidence.getEventCount()),
                projectionValue(evidence == null ? null : evidence.getReserveCount()),
                projectionValue(evidence == null ? null : evidence.getCloseCount()),
                projectionValue(evidence == null ? null : evidence.getReserveAmount())
        );
    }

    private long projectionValue(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private void applyPolicyRequest(BillingPolicyVersion entity, BillingPolicyVersionRequest request, boolean isCreate) {
        if (request.getStatus() != null || isCreate) {
            entity.setStatus(resolveStatus(request.getStatus()));
        }
        if (request.getEffectiveTime() != null || (entity.getStatus() != null && entity.getEffectiveTime() == null)) {
            entity.setEffectiveTime(resolveEffectiveTime(request.getEffectiveTime(), entity.getStatus()));
        }
        if (request.getModelUnitCost() != null) {
            entity.setModelUnitCost(writeJson(request.getModelUnitCost()));
        }
        if (request.getServiceLevelLimit() != null) {
            entity.setServiceLevelLimit(writeJson(request.getServiceLevelLimit()));
        }
        if (request.getTenantTypeWeight() != null) {
            entity.setTenantTypeWeight(writeJson(request.getTenantTypeWeight()));
        }
        if (request.getDefaultUnitCost() != null || isCreate) {
            entity.setDefaultUnitCost(request.getDefaultUnitCost());
        }
        if (request.getDefaultDailyLimit() != null || isCreate) {
            entity.setDefaultDailyLimit(request.getDefaultDailyLimit());
        }
        if (request.getDefaultDailyTokenLimit() != null || isCreate) {
            entity.setDefaultDailyTokenLimit(request.getDefaultDailyTokenLimit());
        }
        if (request.getDefaultTenantWeight() != null || isCreate) {
            entity.setDefaultTenantWeight(request.getDefaultTenantWeight());
        }
        if (request.getDescription() != null || isCreate) {
            entity.setDescription(request.getDescription());
        }
    }

    private void deactivateOtherPolicies(Long currentId) {
        List<BillingPolicyVersion> actives = policyRepository.findByStatus(ACTIVE_STATUS);
        if (actives == null || actives.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        actives.stream()
                .filter(item -> item.getId() != null && !item.getId().equals(currentId))
                .forEach(item -> {
                    item.setStatus(INACTIVE_STATUS);
                    item.setUpdateTime(now);
                });
        policyRepository.saveAll(actives);
    }

    private LocalDateTime resolveEffectiveTime(String value, Integer status) {
        if (StringUtils.hasText(value)) {
            String text = value.trim();
            try {
                return LocalDateTime.parse(text, DateTimeFormatter.ISO_DATE_TIME);
            } catch (DateTimeParseException ignored) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.SIMPLIFIED_CHINESE);
                return LocalDateTime.parse(text, formatter);
            }
        }
        if (status != null && status == ACTIVE_STATUS) {
            return LocalDateTime.now();
        }
        return null;
    }

    private Integer resolveStatus(Integer status) {
        return status == null ? ACTIVE_STATUS : status;
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return code.trim().toUpperCase();
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("序列化计费配置失败: {}", ex.getMessage());
            throw new BizException(ErrorCode.BILLING_INVALID, "计费配置格式错误", ex);
        }
    }
}
