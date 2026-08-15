package com.lingsql.billing.acceptance;

import com.lingsql.billing.acceptance.BillingAcceptanceLedgerRepository.IntervalEvidence;
import com.lingsql.billing.acceptance.BillingAcceptanceLedgerRepository.InvocationLedgerEvidence;
import com.lingsql.billing.invocation.BillingInvocationEntity;
import com.lingsql.billing.invocation.BillingInvocationRepository;
import com.lingsql.billing.invocation.BillingInvocationState;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 基于 MySQL 一致性边界的计费验收会话。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Service
public class BillingAcceptanceSessionImpl implements IBillingAcceptanceSession {

    private static final int NOT_DELETED = 0;
    private static final int MAX_ID_LENGTH = 64;
    private static final int MAX_REQUESTS = 100;

    private final BillingAcceptanceSessionRepository sessionRepository;
    private final BillingAcceptanceReceiptRepository receiptRepository;
    private final BillingAcceptanceLedgerRepository ledgerRepository;
    private final BillingInvocationRepository invocationRepository;
    private final TenantRepository tenantRepository;
    private final BillingAcceptanceEvidenceCodec evidenceCodec;
    private final TransactionTemplate transactionTemplate;

    public BillingAcceptanceSessionImpl(
            BillingAcceptanceSessionRepository sessionRepository,
            BillingAcceptanceReceiptRepository receiptRepository,
            BillingAcceptanceLedgerRepository ledgerRepository,
            BillingInvocationRepository invocationRepository,
            TenantRepository tenantRepository,
            BillingAcceptanceEvidenceCodec evidenceCodec,
            PlatformTransactionManager transactionManager
    ) {
        this.sessionRepository = sessionRepository;
        this.receiptRepository = receiptRepository;
        this.ledgerRepository = ledgerRepository;
        this.invocationRepository = invocationRepository;
        this.tenantRepository = tenantRepository;
        this.evidenceCodec = evidenceCodec;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public BillingAcceptanceSessionReceipt open(String tenantId, String operator) {
        String normalizedTenantId = normalizeRequired(tenantId, "租户ID");
        String normalizedOperator = normalizeRequired(operator, "操作人");
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            TenantInfo tenant = lockTenant(normalizedTenantId);
            long balanceBefore = validBalance(tenant.getQuotaBalance());
            long watermark = ledgerRepository.currentWatermark(normalizedTenantId);
            LocalDateTime now = now();
            BillingAcceptanceSessionEntity entity = new BillingAcceptanceSessionEntity();
            entity.setSessionId(compactUuid());
            entity.setTenantId(normalizedTenantId);
            entity.setBalanceBefore(balanceBefore);
            entity.setLedgerWatermark(watermark);
            entity.setOpenedBy(normalizedOperator);
            entity.setOpenedAt(now);
            entity.setCreateTime(now);
            return toSessionReceipt(sessionRepository.saveAndFlush(entity));
        }));
    }

    @Override
    public BillingAcceptanceReceipt close(String sessionId, List<String> requestIds, String operator) {
        String normalizedSessionId = normalizeRequired(sessionId, "验收会话ID");
        String normalizedOperator = normalizeRequired(operator, "操作人");
        List<String> normalizedRequestIds = normalizeRequestIds(requestIds);
        String requestIdsHash = evidenceCodec.requestIdsHash(normalizedRequestIds);
        return Objects.requireNonNull(transactionTemplate.execute(status -> closeInTransaction(
                normalizedSessionId,
                normalizedRequestIds,
                requestIdsHash,
                normalizedOperator
        )));
    }

    private BillingAcceptanceReceipt closeInTransaction(String sessionId,
                                                         List<String> requestIds,
                                                         String requestIdsHash,
                                                         String operator) {
        BillingAcceptanceSessionEntity session = sessionRepository.findBySessionIdForUpdate(sessionId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "计费验收会话不存在"));
        BillingAcceptanceReceiptEntity existing = receiptRepository.findBySessionId(sessionId).orElse(null);
        if (existing != null) {
            if (!existing.getRequestIdsHash().equals(requestIdsHash)) {
                throw new BizException(ErrorCode.CONFLICT, "计费验收会话已由不同请求集合关闭");
            }
            return toReceipt(existing);
        }

        TenantInfo tenant = lockTenant(session.getTenantId());
        long balanceAfter = validBalance(tenant.getQuotaBalance());
        long endWatermark = ledgerRepository.currentWatermark(session.getTenantId());
        if (endWatermark <= session.getLedgerWatermark()) {
            throw evidenceConflict("验收区间没有新增计费账本事件");
        }
        if (ledgerRepository.countScopeEventsAtOrBefore(
                session.getTenantId(), session.getLedgerWatermark(), requestIds) > 0) {
            throw evidenceConflict("请求集合在验收会话开启前已经产生账本事件");
        }

        IntervalEvidence interval = ledgerRepository.intervalEvidence(
                session.getTenantId(), session.getLedgerWatermark(), endWatermark, requestIds);
        validateInterval(interval);
        List<BillingInvocationEntity> invocations = invocationRepository
                .findByTenantIdAndWorkflowRequestIdInOrderByInvocationIdAsc(session.getTenantId(), requestIds);
        InvocationCounts invocationCounts = validateInvocations(invocations, requestIds);
        List<InvocationLedgerEvidence> ledgerRows = ledgerRepository.scopeInvocationEvidence(
                session.getTenantId(), session.getLedgerWatermark(), endWatermark, requestIds);
        long scopeNetAmount = validateInvocationLedgers(invocations, ledgerRows);
        long intervalNetAmount = safeNet(interval);
        if (scopeNetAmount != intervalNetAmount) {
            throw evidenceConflict("本批请求净扣款与完整账本区间不一致");
        }
        long balanceDelta = safeSubtract(session.getBalanceBefore(), balanceAfter, "租户余额差额超过系统上限");
        if (balanceDelta != intervalNetAmount) {
            throw evidenceConflict("MySQL 租户余额变化与账本区间净扣款不一致");
        }

        LocalDateTime now = now();
        BillingAcceptanceReceiptEntity receipt = buildReceipt(
                session,
                requestIds,
                requestIdsHash,
                endWatermark,
                balanceAfter,
                scopeNetAmount,
                intervalNetAmount,
                interval.unrelatedCount(),
                invocationCounts,
                operator,
                now
        );
        return toReceipt(receiptRepository.saveAndFlush(receipt));
    }

    private void validateInterval(IntervalEvidence interval) {
        if (interval == null || interval.eventCount() <= 0) {
            throw evidenceConflict("验收区间没有可归因的账本事件");
        }
        if (interval.invalidCount() > 0 || interval.appliedCount() != interval.eventCount()) {
            throw evidenceConflict("验收区间存在未应用、失败或缺少应用时间的账本事件");
        }
        if (interval.unrelatedCount() > 0) {
            throw evidenceConflict("验收区间存在非本批请求的账本事件");
        }
    }

    private InvocationCounts validateInvocations(List<BillingInvocationEntity> invocations, List<String> requestIds) {
        if (invocations == null || invocations.isEmpty()) {
            throw evidenceConflict("本批请求缺少物理模型调用");
        }
        Set<String> matchedRequestIds = invocations.stream()
                .map(BillingInvocationEntity::getWorkflowRequestId)
                .collect(Collectors.toSet());
        if (!matchedRequestIds.equals(Set.copyOf(requestIds))) {
            throw evidenceConflict("物理模型调用未覆盖精确请求集合");
        }
        long settled = invocations.stream()
                .filter(item -> BillingInvocationState.SETTLED.name().equals(item.getState()))
                .count();
        long cancelled = invocations.stream()
                .filter(item -> BillingInvocationState.CANCELLED.name().equals(item.getState()))
                .count();
        if (Math.addExact(settled, cancelled) != invocations.size()) {
            throw evidenceConflict("本批请求存在非终态物理模型调用");
        }
        return new InvocationCounts(invocations.size(), settled, cancelled);
    }

    private long validateInvocationLedgers(List<BillingInvocationEntity> invocations,
                                           List<InvocationLedgerEvidence> ledgerRows) {
        Map<String, InvocationLedgerEvidence> ledgerByInvocation = ledgerRows.stream()
                .filter(row -> StringUtils.hasText(row.invocationId()))
                .collect(Collectors.toMap(
                        InvocationLedgerEvidence::invocationId,
                        Function.identity(),
                        (left, right) -> {
                            throw evidenceConflict("同一物理调用存在重复账本聚合");
                        },
                        LinkedHashMap::new
                ));
        if (ledgerByInvocation.size() != invocations.size()) {
            throw evidenceConflict("物理模型调用与账本调用数量不一致");
        }
        long scopeNetAmount = 0L;
        for (BillingInvocationEntity invocation : invocations) {
            InvocationLedgerEvidence ledger = ledgerByInvocation.get(invocation.getInvocationId());
            if (ledger == null || !invocation.getWorkflowRequestId().equals(ledger.workflowRequestId())) {
                throw evidenceConflict("物理模型调用缺少精确请求账本");
            }
            validateInvocationLedger(invocation, ledger);
            scopeNetAmount = safeAdd(scopeNetAmount, ledger.netAmount(), "本批请求净扣款超过系统上限");
        }
        return scopeNetAmount;
    }

    private void validateInvocationLedger(BillingInvocationEntity invocation, InvocationLedgerEvidence ledger) {
        if (ledger.eventCount() != ledger.appliedCount()) {
            throw evidenceConflict("物理调用账本包含未应用或失败事件");
        }
        if (BillingInvocationState.SETTLED.name().equals(invocation.getState())) {
            boolean valid = ledger.eventCount() == ledger.reserveCount() + ledger.refundCount() + ledger.closeCount()
                    && ledger.reserveCount() == 1
                    && (ledger.refundCount() == 0 || ledger.refundCount() == 1)
                    && ledger.cancelCount() == 0
                    && ledger.closeCount() == 1
                    && ledger.reserveAmount() == invocation.getReservedAmount()
                    && ledger.refundAmount() == value(invocation.getRefundAmount())
                    && ledger.closeAmount() == value(invocation.getActualAmount())
                    && ledger.reserveAmount() == safeAdd(
                            ledger.refundAmount(), ledger.closeAmount(), "物理调用账本金额超过系统上限");
            if (!valid) {
                throw evidenceConflict("已结算物理调用的账本事件或金额不守恒");
            }
            return;
        }
        boolean valid = ledger.eventCount() == ledger.reserveCount() + ledger.cancelCount()
                && ledger.reserveCount() == 1
                && ledger.refundCount() == 0
                && ledger.cancelCount() == 1
                && ledger.closeCount() == 0
                && ledger.reserveAmount() == invocation.getReservedAmount()
                && ledger.cancelAmount() == invocation.getReservedAmount()
                && value(invocation.getActualAmount()) == 0L
                && value(invocation.getRefundAmount()) == invocation.getReservedAmount();
        if (!valid) {
            throw evidenceConflict("已取消物理调用的账本事件或金额不守恒");
        }
    }

    private BillingAcceptanceReceiptEntity buildReceipt(BillingAcceptanceSessionEntity session,
                                                         List<String> requestIds,
                                                         String requestIdsHash,
                                                         long endWatermark,
                                                         long balanceAfter,
                                                         long scopeNetAmount,
                                                         long intervalNetAmount,
                                                         long unrelatedEventCount,
                                                         InvocationCounts counts,
                                                         String operator,
                                                         LocalDateTime now) {
        BillingAcceptanceReceiptEntity receipt = new BillingAcceptanceReceiptEntity();
        receipt.setReceiptId(compactUuid());
        receipt.setSessionId(session.getSessionId());
        receipt.setTenantId(session.getTenantId());
        receipt.setRequestIdsJson(evidenceCodec.writeRequestIds(requestIds));
        receipt.setRequestIdsHash(requestIdsHash);
        receipt.setRequestCount(requestIds.size());
        receipt.setStartLedgerWatermark(session.getLedgerWatermark());
        receipt.setEndLedgerWatermark(endWatermark);
        receipt.setBalanceBefore(session.getBalanceBefore());
        receipt.setBalanceAfter(balanceAfter);
        receipt.setScopeNetAmount(scopeNetAmount);
        receipt.setIntervalNetAmount(intervalNetAmount);
        receipt.setUnrelatedEventCount(unrelatedEventCount);
        receipt.setInvocationCount(counts.total());
        receipt.setSettledInvocationCount(counts.settled());
        receipt.setCancelledInvocationCount(counts.cancelled());
        receipt.setClosedBy(operator);
        receipt.setClosedAt(now);
        receipt.setCreateTime(now);
        receipt.setReceiptHash(evidenceCodec.receiptHash(
                receipt.getReceiptId(), receipt.getSessionId(), receipt.getTenantId(), requestIdsHash,
                receipt.getStartLedgerWatermark(), endWatermark, receipt.getBalanceBefore(), balanceAfter,
                scopeNetAmount, intervalNetAmount, unrelatedEventCount, counts.total(), counts.settled(),
                counts.cancelled(), operator, now
        ));
        return receipt;
    }

    private BillingAcceptanceSessionReceipt toSessionReceipt(BillingAcceptanceSessionEntity entity) {
        return new BillingAcceptanceSessionReceipt(
                entity.getSessionId(), entity.getTenantId(), entity.getBalanceBefore(), entity.getLedgerWatermark(),
                entity.getOpenedBy(), entity.getOpenedAt()
        );
    }

    private BillingAcceptanceReceipt toReceipt(BillingAcceptanceReceiptEntity entity) {
        return new BillingAcceptanceReceipt(
                entity.getReceiptId(), entity.getSessionId(), entity.getTenantId(),
                evidenceCodec.readRequestIds(entity.getRequestIdsJson()), entity.getRequestIdsHash(),
                entity.getStartLedgerWatermark(), entity.getEndLedgerWatermark(), entity.getBalanceBefore(),
                entity.getBalanceAfter(), entity.getScopeNetAmount(), entity.getIntervalNetAmount(),
                entity.getUnrelatedEventCount(), entity.getInvocationCount(), entity.getSettledInvocationCount(),
                entity.getCancelledInvocationCount(), entity.getReceiptHash(), entity.getClosedBy(), entity.getClosedAt()
        );
    }

    private TenantInfo lockTenant(String tenantId) {
        return tenantRepository.findByTenantIdAndIsDeletedForUpdate(tenantId, NOT_DELETED)
                .orElseThrow(() -> new BizException(ErrorCode.TENANT_NOT_FOUND, "计费验收租户不存在"));
    }

    private List<String> normalizeRequestIds(List<String> requestIds) {
        if (requestIds == null || requestIds.isEmpty() || requestIds.size() > MAX_REQUESTS) {
            throw new BizException(ErrorCode.INVALID_PARAM, "计费验收请求ID数量必须为1至100个");
        }
        List<String> normalized = requestIds.stream()
                .map(value -> normalizeRequired(value, "请求ID"))
                .distinct()
                .sorted()
                .toList();
        if (normalized.size() != requestIds.size()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "计费验收请求ID不得重复");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String name) {
        if (!StringUtils.hasText(value) || value.trim().length() > MAX_ID_LENGTH) {
            throw new BizException(ErrorCode.INVALID_PARAM, name + "格式不正确");
        }
        return value.trim();
    }

    private long validBalance(Long balance) {
        if (balance == null || balance < 0) {
            throw evidenceConflict("MySQL 租户余额为空或为负数");
        }
        return balance;
    }

    private long safeNet(IntervalEvidence interval) {
        try {
            return interval.netAmount();
        } catch (ArithmeticException ex) {
            throw new BizException(ErrorCode.BILLING_INVALID, "账本区间净扣款超过系统上限", ex);
        }
    }

    private long safeAdd(long left, long right, String message) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ex) {
            throw new BizException(ErrorCode.BILLING_INVALID, message, ex);
        }
    }

    private long safeSubtract(long left, long right, String message) {
        try {
            return Math.subtractExact(left, right);
        } catch (ArithmeticException ex) {
            throw new BizException(ErrorCode.BILLING_INVALID, message, ex);
        }
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private BizException evidenceConflict(String message) {
        return new BizException(ErrorCode.CONFLICT, message);
    }

    private LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private record InvocationCounts(long total, long settled, long cancelled) {
    }
}
