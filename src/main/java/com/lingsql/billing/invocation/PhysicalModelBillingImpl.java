package com.lingsql.billing.invocation;

import com.lingsql.billing.BillingBalanceService;
import com.lingsql.billing.BillingPolicy;
import com.lingsql.billing.entity.TenantBillingOverride;
import com.lingsql.billing.model.BillingQuote;
import com.lingsql.billing.repository.TenantBillingOverrideRepository;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.repository.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 逐物理模型调用计费深模块实现。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Slf4j
@Service
public class PhysicalModelBillingImpl implements IPhysicalModelBilling {

    private static final int NOT_DELETED = 0;
    private static final int ACTIVE_OVERRIDE = 1;
    private static final int MAX_ID_LENGTH = 64;
    private static final int MAX_STAGE_LENGTH = 64;
    private static final int MAX_CALLER_LENGTH = 64;
    private static final int MAX_PROVIDER_LENGTH = 64;
    private static final int MAX_MODEL_LENGTH = 64;
    private static final int PROMPT_HASH_LENGTH = 64;
    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");

    private final BillingInvocationRepository invocationRepository;
    private final PhysicalBillingLedgerRepository ledgerRepository;
    private final TenantRepository tenantRepository;
    private final TenantBillingOverrideRepository overrideRepository;
    private final BillingPolicy billingPolicy;
    private final BillingBalanceService balanceService;
    private final TransactionTemplate transactionTemplate;

    public PhysicalModelBillingImpl(BillingInvocationRepository invocationRepository,
                                    PhysicalBillingLedgerRepository ledgerRepository,
                                    TenantRepository tenantRepository,
                                    TenantBillingOverrideRepository overrideRepository,
                                    BillingPolicy billingPolicy,
                                    BillingBalanceService balanceService,
                                    PlatformTransactionManager transactionManager) {
        this.invocationRepository = invocationRepository;
        this.ledgerRepository = ledgerRepository;
        this.tenantRepository = tenantRepository;
        this.overrideRepository = overrideRepository;
        this.billingPolicy = billingPolicy;
        this.balanceService = balanceService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> BilledModelResponse<T> execute(
            PhysicalModelInvocation invocation,
            IPhysicalModelCall<T> physicalCall
    ) {
        validate(invocation, physicalCall);
        BillingInvocationEntity reserved = reserve(invocation);
        dispatch(invocation.invocationId());

        PhysicalModelResponse<T> response;
        try {
            response = physicalCall.invoke();
        } catch (RuntimeException ex) {
            BillingInvocationUnknownException unknown = new BillingInvocationUnknownException(
                    "物理模型调用结果不确定，禁止自动退款或切换模型", ex);
            markUnknownOrAttach(invocation.invocationId(), "CALLBACK_FAILED", "物理模型调用结果不确定", unknown);
            throw unknown;
        }
        if (response == null || response.actualTokens() == null) {
            BillingInvocationUnknownException unknown = new BillingInvocationUnknownException(
                    "供应商未返回可信用量，调用已进入人工核对");
            markUnknownOrAttach(invocation.invocationId(), "USAGE_MISSING", "供应商未返回可信用量", unknown);
            throw unknown;
        }
        if (response.actualTokens() < 0 || response.actualTokens() > invocation.maxBillableTokens()) {
            BillingInvocationUnknownException unknown = new BillingInvocationUnknownException(
                    "实际用量超出已预留范围，调用已进入人工核对");
            markUnknownOrAttach(invocation.invocationId(), "USAGE_OUT_OF_RANGE", "实际用量超出已预留范围", unknown);
            throw unknown;
        }

        BillingInvocationEntity settled;
        try {
            settled = settle(invocation.invocationId(), response.actualTokens(), response.providerRequestId());
        } catch (RuntimeException ex) {
            BillingPersistenceException failure = new BillingPersistenceException(
                    "模型已返回但资金结算失败，结果不会交付", ex);
            markUnknownOrAttach(invocation.invocationId(), "SETTLEMENT_FAILED", "模型返回后资金结算失败", failure);
            throw failure;
        }
        return new BilledModelResponse<>(
                response.payload(),
                settled.getInvocationId(),
                settled.getReservationId(),
                settled.getActualTokens(),
                settled.getActualAmount(),
                settled.getBillingVersion()
        );
    }

    void recoverStale(LocalDateTime reservedCutoff, LocalDateTime dispatchedCutoff, int batchSize) {
        invocationRepository.findByStateAndReservedAtBeforeOrderByIdAsc(
                BillingInvocationState.RESERVED.name(),
                reservedCutoff,
                org.springframework.data.domain.PageRequest.of(0, batchSize)
        ).forEach(entity -> cancelReserved(entity.getInvocationId()));
        invocationRepository.findByStateAndDispatchedAtBeforeOrderByIdAsc(
                BillingInvocationState.DISPATCHED.name(),
                dispatchedCutoff,
                org.springframework.data.domain.PageRequest.of(0, batchSize)
        ).forEach(entity -> markUnknown(
                entity.getInvocationId(),
                "DISPATCH_TIMEOUT",
                "物理模型调用超过确认时限"
        ));
    }

    private BillingInvocationEntity reserve(PhysicalModelInvocation invocation) {
        try {
            return Objects.requireNonNull(transactionTemplate.execute(status -> reserveInTransaction(invocation)));
        } catch (BillingAccessDeniedException | BillingInvocationConflictException ex) {
            throw ex;
        } catch (DataIntegrityViolationException ex) {
            BillingInvocationEntity existing = invocationRepository.findByInvocationId(invocation.invocationId())
                    .orElseThrow(() -> new BillingPersistenceException("物理调用预留发生唯一约束冲突", ex));
            assertReplay(existing, invocation);
            return existing;
        } catch (RuntimeException ex) {
            throw new BillingPersistenceException("物理调用资金预留失败", ex);
        }
    }

    private BillingInvocationEntity reserveInTransaction(PhysicalModelInvocation invocation) {
        BillingInvocationEntity existing = invocationRepository.findByInvocationId(invocation.invocationId()).orElse(null);
        if (existing != null) {
            assertReplay(existing, invocation);
            return existing;
        }
        TenantInfo tenant = tenantRepository.findByTenantIdAndIsDeleted(invocation.tenantId(), NOT_DELETED)
                .orElseThrow(() -> new BillingAccessDeniedException("租户不存在或不可计费"));
        TenantBillingOverride override = overrideRepository
                .findByTenantIdAndStatus(invocation.tenantId(), ACTIVE_OVERRIDE)
                .orElse(null);
        BillingQuote quote = billingPolicy.quote(
                invocation.modelName(),
                invocation.maxBillableTokens(),
                tenant.getTenantType(),
                override == null ? null : override.getPriceMultiplier()
        );
        if (quote.costAmount() <= 0) {
            throw new BillingAccessDeniedException("模型调用缺少有效计费价格");
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = tenantRepository.decreaseBalanceIfSufficient(
                invocation.tenantId(), quote.costAmount(), NOT_DELETED, now);
        if (updated != 1) {
            throw new BillingAccessDeniedException("余额不足，请升级套餐");
        }
        BillingInvocationEntity entity = buildReserved(invocation, quote, now);
        BillingInvocationEntity saved = invocationRepository.saveAndFlush(entity);
        ledgerRepository.appendReserve(saved, now);
        evictBalanceAfterCommit(saved.getTenantId());
        return saved;
    }

    private void dispatch(String invocationId) {
        try {
            Integer updated = transactionTemplate.execute(status -> invocationRepository.transitionToDispatched(
                    invocationId,
                    BillingInvocationState.RESERVED.name(),
                    BillingInvocationState.DISPATCHED.name(),
                    LocalDateTime.now()
            ));
            if (updated == null || updated != 1) {
                String state = invocationRepository.findByInvocationId(invocationId)
                        .map(BillingInvocationEntity::getState)
                        .orElse("MISSING");
                throw new BillingInvocationConflictException("物理调用不可派发，当前状态: " + state);
            }
        } catch (BillingInvocationConflictException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BillingPersistenceException("物理调用派发状态持久化失败", ex);
        }
    }

    private BillingInvocationEntity settle(String invocationId, int actualTokens, String providerRequestId) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            BillingInvocationEntity entity = invocationRepository.findByInvocationIdForUpdate(invocationId)
                    .orElseThrow(() -> new BillingInvocationConflictException("物理调用资金记录不存在"));
            requireState(entity, BillingInvocationState.DISPATCHED);
            long actualAmount = PhysicalBillingAmountCalculator.calculate(entity, actualTokens);
            long refundAmount = Math.subtractExact(entity.getReservedAmount(), actualAmount);
            LocalDateTime now = LocalDateTime.now();
            if (refundAmount > 0) {
                int updated = tenantRepository.increaseBalance(entity.getTenantId(), refundAmount, NOT_DELETED, now);
                if (updated != 1) {
                    throw new BillingPersistenceException("退回模型调用预留差额失败", null);
                }
                ledgerRepository.appendRefund(entity, refundAmount, now);
            }
            ledgerRepository.appendClose(entity, actualTokens, actualAmount, now);
            entity.setActualTokens(actualTokens);
            entity.setActualAmount(actualAmount);
            entity.setRefundAmount(refundAmount);
            entity.setProviderRequestId(normalizeOptional(providerRequestId, 128));
            entity.setState(BillingInvocationState.SETTLED.name());
            entity.setFinalizedAt(now);
            entity.setUpdateTime(now);
            BillingInvocationEntity saved = invocationRepository.saveAndFlush(entity);
            evictBalanceAfterCommit(entity.getTenantId());
            return saved;
        }));
    }

    private void cancelReserved(String invocationId) {
        transactionTemplate.executeWithoutResult(status -> {
            BillingInvocationEntity entity = invocationRepository.findByInvocationIdForUpdate(invocationId).orElse(null);
            if (entity == null || !BillingInvocationState.RESERVED.name().equals(entity.getState())) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            int updated = tenantRepository.increaseBalance(
                    entity.getTenantId(), entity.getReservedAmount(), NOT_DELETED, now);
            if (updated != 1) {
                throw new BillingPersistenceException("取消未派发预留时退回余额失败", null);
            }
            ledgerRepository.appendCancel(entity, now);
            entity.setRefundAmount(entity.getReservedAmount());
            entity.setState(BillingInvocationState.CANCELLED.name());
            entity.setFailureCode("RESERVE_TIMEOUT");
            entity.setFailureMessage("预留超过安全派发时限");
            entity.setFinalizedAt(now);
            entity.setUpdateTime(now);
            invocationRepository.saveAndFlush(entity);
            evictBalanceAfterCommit(entity.getTenantId());
        });
    }

    private void markUnknown(String invocationId, String failureCode, String failureMessage) {
        transactionTemplate.executeWithoutResult(status -> {
            BillingInvocationEntity entity = invocationRepository.findByInvocationIdForUpdate(invocationId)
                    .orElseThrow(() -> new BillingInvocationConflictException("物理调用资金记录不存在"));
            if (BillingInvocationState.UNKNOWN.name().equals(entity.getState())) {
                return;
            }
            requireState(entity, BillingInvocationState.DISPATCHED);
            LocalDateTime now = LocalDateTime.now();
            entity.setState(BillingInvocationState.UNKNOWN.name());
            entity.setFailureCode(failureCode);
            entity.setFailureMessage(failureMessage);
            entity.setFinalizedAt(now);
            entity.setUpdateTime(now);
            invocationRepository.saveAndFlush(entity);
        });
    }

    private void markUnknownOrAttach(String invocationId,
                                     String failureCode,
                                     String failureMessage,
                                     RuntimeException primaryFailure) {
        try {
            markUnknown(invocationId, failureCode, failureMessage);
        } catch (RuntimeException persistenceFailure) {
            log.error("物理模型调用转入UNKNOWN失败: invocationId={}", invocationId, persistenceFailure);
            primaryFailure.addSuppressed(new BillingPersistenceException(
                    "物理调用UNKNOWN状态持久化失败", persistenceFailure));
        }
    }

    private BillingInvocationEntity buildReserved(PhysicalModelInvocation invocation,
                                                   BillingQuote quote,
                                                   LocalDateTime now) {
        BillingInvocationEntity entity = new BillingInvocationEntity();
        entity.setReservationId(compactUuid());
        entity.setInvocationId(invocation.invocationId().trim());
        entity.setTenantId(invocation.tenantId().trim());
        entity.setWorkflowRequestId(invocation.workflowRequestId().trim());
        entity.setLogicalCallNo(invocation.logicalCallNo());
        entity.setStage(invocation.stage().trim());
        entity.setCaller(invocation.caller().trim());
        entity.setRouteAttempt(invocation.routeAttempt());
        entity.setProviderName(invocation.providerName().trim());
        entity.setModelName(invocation.modelName().trim().toUpperCase());
        entity.setPromptHash(invocation.promptHash().trim().toLowerCase());
        entity.setMaxBillableTokens(invocation.maxBillableTokens());
        entity.setUnitCost(quote.unitCost());
        entity.setBillingWeight(quote.tenantWeight());
        entity.setReservedAmount(quote.costAmount());
        entity.setBillingVersion(quote.policyVersion());
        entity.setState(BillingInvocationState.RESERVED.name());
        entity.setReservedAt(now);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setLockVersion(0L);
        return entity;
    }

    private void assertReplay(BillingInvocationEntity entity, PhysicalModelInvocation invocation) {
        boolean same = entity.getTenantId().equals(invocation.tenantId().trim())
                && entity.getWorkflowRequestId().equals(invocation.workflowRequestId().trim())
                && entity.getLogicalCallNo() == invocation.logicalCallNo()
                && entity.getStage().equals(invocation.stage().trim())
                && entity.getCaller().equals(invocation.caller().trim())
                && entity.getRouteAttempt() == invocation.routeAttempt()
                && entity.getProviderName().equals(invocation.providerName().trim())
                && entity.getModelName().equals(invocation.modelName().trim().toUpperCase())
                && entity.getMaxBillableTokens() == invocation.maxBillableTokens()
                && entity.getPromptHash().equals(invocation.promptHash().trim().toLowerCase());
        if (!same) {
            throw new BillingInvocationConflictException("物理调用幂等ID已存在但内容不一致");
        }
        if (!BillingInvocationState.RESERVED.name().equals(entity.getState())) {
            throw new BillingInvocationConflictException("物理调用幂等ID已处于状态: " + entity.getState());
        }
    }

    private void requireState(BillingInvocationEntity entity, BillingInvocationState expected) {
        if (!expected.name().equals(entity.getState())) {
            throw new BillingInvocationConflictException(
                    "物理调用状态冲突，期望 " + expected.name() + "，实际 " + entity.getState());
        }
    }

    private <T> void validate(PhysicalModelInvocation invocation, IPhysicalModelCall<T> physicalCall) {
        if (invocation == null || physicalCall == null
                || invalidText(invocation.invocationId(), MAX_ID_LENGTH)
                || invalidText(invocation.tenantId(), MAX_ID_LENGTH)
                || invalidText(invocation.workflowRequestId(), MAX_ID_LENGTH)
                || invalidText(invocation.stage(), MAX_STAGE_LENGTH)
                || invalidText(invocation.caller(), MAX_CALLER_LENGTH)
                || invalidText(invocation.providerName(), MAX_PROVIDER_LENGTH)
                || invalidText(invocation.modelName(), MAX_MODEL_LENGTH)
                || invalidText(invocation.promptHash(), PROMPT_HASH_LENGTH)
                || !SHA256_PATTERN.matcher(invocation.promptHash().trim()).matches()
                || invocation.logicalCallNo() <= 0
                || invocation.routeAttempt() <= 0
                || invocation.maxBillableTokens() <= 0) {
            throw new BillingInvocationConflictException("物理模型计费参数不完整");
        }
    }

    private boolean invalidText(String value, int maxLength) {
        return !StringUtils.hasText(value) || value.trim().length() > maxLength;
    }

    private String normalizeOptional(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private void evictBalanceAfterCommit(String tenantId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            balanceService.evictBalance(tenantId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                balanceService.evictBalance(tenantId);
            }
        });
    }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
