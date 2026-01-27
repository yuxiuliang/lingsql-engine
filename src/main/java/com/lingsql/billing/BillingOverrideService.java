package com.lingsql.billing;

import com.lingsql.billing.entity.TenantBillingOverride;
import com.lingsql.billing.model.TenantBillingOverrideRequest;
import com.lingsql.billing.repository.TenantBillingOverrideRepository;
import com.lingsql.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 租户计费覆盖配置服务。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@Service
@RequiredArgsConstructor
public class BillingOverrideService {

    private static final int ACTIVE_STATUS = 1;
    private static final int NOT_DELETED = 0;

    private final TenantRepository tenantRepository;
    private final TenantBillingOverrideRepository overrideRepository;

    /**
     * 保存租户计费覆盖配置。
     *
     * @param tenantId 租户ID
     * @param request  覆盖请求
     * @return 覆盖实体
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Transactional
    public TenantBillingOverride saveTenantOverride(String tenantId, TenantBillingOverrideRequest request) {
        if (!StringUtils.hasText(tenantId)) {
            throw new RuntimeException("租户ID不能为空");
        }
        if (request == null) {
            throw new RuntimeException("计费覆盖不能为空");
        }
        if (!tenantRepository.existsByTenantIdAndIsDeleted(tenantId, NOT_DELETED)) {
            throw new RuntimeException("租户不存在");
        }
        TenantBillingOverride override = overrideRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    TenantBillingOverride created = new TenantBillingOverride();
                    created.setTenantId(tenantId);
                    created.setStatus(ACTIVE_STATUS);
                    created.setCreateTime(LocalDateTime.now());
                    return created;
                });
        if (request.getPriceMultiplier() != null) {
            override.setPriceMultiplier(request.getPriceMultiplier());
        }
        if (request.getStatus() != null) {
            override.setStatus(request.getStatus());
        }
        if (request.getRemark() != null) {
            override.setRemark(request.getRemark());
        }
        override.setUpdateTime(LocalDateTime.now());
        return overrideRepository.save(override);
    }
}
