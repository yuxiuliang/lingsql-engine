package com.lingsql.billing;

import com.lingsql.audit.repository.SqlExecuteLogRepository;
import com.lingsql.billing.entity.BillingPolicyVersion;
import com.lingsql.billing.entity.TenantBillingOverride;
import com.lingsql.billing.invocation.BillingInvocationRepository;
import com.lingsql.billing.model.BillingLedgerEvidenceView;
import com.lingsql.billing.model.BillingPolicyVersionRequest;
import com.lingsql.billing.model.BillingRequestEvidenceView;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private static final int MAX_EVIDENCE_REQUESTS = 100;

    private final BillingPolicyVersionRepository policyRepository;
    private final TenantBillingOverrideRepository overrideRepository;
    private final BillingOverrideService billingOverrideService;
    private final BillingLedgerEventRepository ledgerEventRepository;
    private final BillingInvocationRepository invocationRepository;
    private final SqlExecuteLogRepository sqlExecuteLogRepository;
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
        if (requestIds.stream().anyMatch(value -> !StringUtils.hasText(value) || value.trim().length() > 64)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "请求ID格式不正确");
        }
        List<String> normalized = requestIds.stream()
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.size() > MAX_EVIDENCE_REQUESTS) {
            throw new BizException(ErrorCode.INVALID_PARAM, "单次最多核验100个请求ID");
        }

        String normalizedTenantId = tenantId.trim();
        Map<String, SqlExecuteLogRepository.SqlAuditEvidenceProjection> sqlEvidence = indexBy(
                sqlExecuteLogRepository.findSqlAuditEvidence(normalizedTenantId, normalized),
                SqlExecuteLogRepository.SqlAuditEvidenceProjection::getRequestId
        );
        Map<String, BillingInvocationRepository.RequestInvocationEvidenceProjection> invocationEvidence = indexBy(
                invocationRepository.findRequestInvocationEvidence(normalizedTenantId, normalized),
                BillingInvocationRepository.RequestInvocationEvidenceProjection::getWorkflowRequestId
        );
        Map<String, BillingLedgerEventRepository.RequestLedgerEvidenceProjection> ledgerEvidence = indexBy(
                ledgerEventRepository.findRequestLedgerEvidence(normalizedTenantId, normalized),
                BillingLedgerEventRepository.RequestLedgerEvidenceProjection::getWorkflowRequestId
        );
        List<BillingRequestEvidenceView> requests = normalized.stream()
                .map(requestId -> buildRequestEvidence(
                        requestId,
                        sqlEvidence.get(requestId),
                        invocationEvidence.get(requestId),
                        ledgerEvidence.get(requestId)
                ))
                .toList();

        long invocationCount = sum(requests, BillingRequestEvidenceView::invocationCount);
        long settledCount = sum(requests, BillingRequestEvidenceView::settledInvocationCount);
        long unknownCount = sum(requests, BillingRequestEvidenceView::unknownInvocationCount);
        long pendingCount = sum(requests, BillingRequestEvidenceView::pendingInvocationCount);
        long cancelledCount = sum(requests, BillingRequestEvidenceView::cancelledInvocationCount);
        long reserveAmount = sum(requests, BillingRequestEvidenceView::reserveAmount);
        long refundAmount = sum(requests, BillingRequestEvidenceView::refundAmount);
        long closeAmount = sum(requests, BillingRequestEvidenceView::closeAmount);
        long completeRequestCount = requests.stream().filter(BillingRequestEvidenceView::complete).count();
        long unbalancedCount = sum(requests, BillingRequestEvidenceView::unbalancedInvocationCount);
        boolean conserved = completeRequestCount == normalized.size()
                && unbalancedCount == 0
                && amountsConserved(reserveAmount, refundAmount, closeAmount);
        return new BillingLedgerEvidenceView(
                normalized.size(),
                requests.stream().filter(item -> item.ledgerInvocationCount() > 0).count(),
                sum(requests, BillingRequestEvidenceView::eventCount),
                sum(requests, BillingRequestEvidenceView::reserveCount),
                sum(requests, BillingRequestEvidenceView::closeCount),
                reserveAmount,
                invocationCount,
                requests.stream().filter(item -> item.invocationCount() > 0).count(),
                settledCount,
                unknownCount,
                pendingCount,
                cancelledCount,
                sum(requests, BillingRequestEvidenceView::sqlAuditCount),
                sum(requests, BillingRequestEvidenceView::successfulSqlAuditCount),
                sum(requests, BillingRequestEvidenceView::refundCount),
                refundAmount,
                closeAmount,
                sum(requests, BillingRequestEvidenceView::conservedInvocationCount),
                unbalancedCount,
                completeRequestCount,
                conserved,
                requests
        );
    }

    private BillingRequestEvidenceView buildRequestEvidence(
            String requestId,
            SqlExecuteLogRepository.SqlAuditEvidenceProjection sql,
            BillingInvocationRepository.RequestInvocationEvidenceProjection invocation,
            BillingLedgerEventRepository.RequestLedgerEvidenceProjection ledger
    ) {
        long sqlAuditCount = value(sql == null ? null : sql.getAuditCount());
        long successfulSqlAuditCount = value(sql == null ? null : sql.getSuccessCount());
        long invocationCount = value(invocation == null ? null : invocation.getInvocationCount());
        long architectCount = value(invocation == null ? null : invocation.getArchitectSettledCount());
        long analystCount = value(invocation == null ? null : invocation.getAnalystSettledCount());
        long settledCount = value(invocation == null ? null : invocation.getSettledCount());
        long unknownCount = value(invocation == null ? null : invocation.getUnknownCount());
        long pendingCount = value(invocation == null ? null : invocation.getPendingCount());
        long cancelledCount = value(invocation == null ? null : invocation.getCancelledCount());
        long ledgerInvocationCount = value(ledger == null ? null : ledger.getLedgerInvocationCount());
        long eventCount = value(ledger == null ? null : ledger.getEventCount());
        long reserveCount = value(ledger == null ? null : ledger.getReserveCount());
        long refundCount = value(ledger == null ? null : ledger.getRefundCount());
        long closeCount = value(ledger == null ? null : ledger.getCloseCount());
        long reserveAmount = value(ledger == null ? null : ledger.getReserveAmount());
        long refundAmount = value(ledger == null ? null : ledger.getRefundAmount());
        long closeAmount = value(ledger == null ? null : ledger.getCloseAmount());
        long conservedCount = value(ledger == null ? null : ledger.getConservedInvocationCount());
        long unbalancedCount = value(ledger == null ? null : ledger.getUnbalancedInvocationCount());

        List<String> failureReasons = new ArrayList<>();
        addFailure(failureReasons, sqlAuditCount != 1, "SQL审计数量必须为1");
        addFailure(failureReasons, successfulSqlAuditCount != 1, "成功SQL审计数量必须为1");
        addFailure(failureReasons, invocationCount <= 0, "缺少物理模型调用");
        addFailure(failureReasons, architectCount <= 0, "缺少已结算SQL架构师调用");
        addFailure(failureReasons, analystCount <= 0, "缺少已结算业务分析师调用");
        addFailure(failureReasons, settledCount != invocationCount, "存在未结算物理模型调用");
        addFailure(failureReasons, unknownCount > 0, "存在UNKNOWN物理模型调用");
        addFailure(failureReasons, pendingCount > 0, "存在RESERVED或DISPATCHED物理模型调用");
        addFailure(failureReasons, cancelledCount > 0, "存在CANCELLED物理模型调用");
        addFailure(failureReasons, ledgerInvocationCount != invocationCount, "物理调用与账本调用数量不一致");
        addFailure(failureReasons, reserveCount != invocationCount, "每次物理调用必须恰有一个RESERVE");
        addFailure(failureReasons, closeCount != invocationCount, "每次物理调用必须恰有一个CLOSE");
        addFailure(failureReasons, conservedCount != invocationCount || unbalancedCount > 0,
                "存在不满足RESERVE=CLOSE+REFUND的物理调用");
        addFailure(failureReasons, !eventsConserved(eventCount, reserveCount, refundCount, closeCount),
                "账本包含非验收事件或事件数量不一致");
        addFailure(failureReasons, !amountsConserved(reserveAmount, refundAmount, closeAmount),
                "请求资金金额不守恒");

        return new BillingRequestEvidenceView(
                requestId,
                sqlAuditCount,
                successfulSqlAuditCount,
                invocationCount,
                architectCount,
                analystCount,
                settledCount,
                unknownCount,
                pendingCount,
                cancelledCount,
                ledgerInvocationCount,
                eventCount,
                reserveCount,
                refundCount,
                closeCount,
                reserveAmount,
                refundAmount,
                closeAmount,
                conservedCount,
                unbalancedCount,
                failureReasons.isEmpty(),
                List.copyOf(failureReasons)
        );
    }

    private <T> Map<String, T> indexBy(List<T> rows, Function<T, String> keyExtractor) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.stream()
                .filter(row -> row != null && StringUtils.hasText(keyExtractor.apply(row)))
                .collect(Collectors.toMap(
                        row -> keyExtractor.apply(row).trim(),
                        Function.identity(),
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    private long sum(List<BillingRequestEvidenceView> requests,
                     java.util.function.ToLongFunction<BillingRequestEvidenceView> extractor) {
        try {
            return requests.stream().mapToLong(extractor).reduce(0L, Math::addExact);
        } catch (ArithmeticException ex) {
            throw new BizException(ErrorCode.BILLING_INVALID, "计费验收汇总超过系统上限", ex);
        }
    }

    private long value(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private void addFailure(List<String> reasons, boolean failed, String reason) {
        if (failed) {
            reasons.add(reason);
        }
    }

    private boolean eventsConserved(long eventCount, long reserveCount, long refundCount, long closeCount) {
        try {
            return eventCount == Math.addExact(Math.addExact(reserveCount, refundCount), closeCount);
        } catch (ArithmeticException ex) {
            return false;
        }
    }

    private boolean amountsConserved(long reserveAmount, long refundAmount, long closeAmount) {
        try {
            return reserveAmount == Math.addExact(refundAmount, closeAmount);
        } catch (ArithmeticException ex) {
            return false;
        }
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
