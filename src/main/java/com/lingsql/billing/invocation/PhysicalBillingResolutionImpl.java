package com.lingsql.billing.invocation;

import com.lingsql.billing.BillingBalanceService;
import com.lingsql.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * UNKNOWN 物理调用资金裁决实现。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Service
public class PhysicalBillingResolutionImpl implements IPhysicalBillingResolution {

    private static final int NOT_DELETED = 0;
    private static final int MAX_INVOCATION_ID_LENGTH = 64;
    private static final int MAX_OPERATOR_LENGTH = 64;
    private static final int MAX_PROVIDER_REQUEST_ID_LENGTH = 128;
    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");

    private final BillingInvocationRepository invocationRepository;
    private final BillingInvocationResolutionRepository resolutionRepository;
    private final PhysicalBillingLedgerRepository ledgerRepository;
    private final TenantRepository tenantRepository;
    private final BillingBalanceService balanceService;
    private final TransactionTemplate transactionTemplate;

    public PhysicalBillingResolutionImpl(
            BillingInvocationRepository invocationRepository,
            BillingInvocationResolutionRepository resolutionRepository,
            PhysicalBillingLedgerRepository ledgerRepository,
            TenantRepository tenantRepository,
            BillingBalanceService balanceService,
            PlatformTransactionManager transactionManager
    ) {
        this.invocationRepository = invocationRepository;
        this.resolutionRepository = resolutionRepository;
        this.ledgerRepository = ledgerRepository;
        this.tenantRepository = tenantRepository;
        this.balanceService = balanceService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public PhysicalBillingResolutionReceipt resolve(PhysicalBillingResolutionCommand command) {
        ValidatedCommand validated = validate(command);
        try {
            BillingInvocationResolutionEntity resolution = Objects.requireNonNull(
                    transactionTemplate.execute(status -> resolveInTransaction(validated))
            );
            balanceService.evictBalance(resolution.getTenantId());
            return toReceipt(resolution);
        } catch (PhysicalBillingException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BillingPersistenceException("物理调用资金裁决持久化失败", ex);
        }
    }

    private BillingInvocationResolutionEntity resolveInTransaction(ValidatedCommand command) {
        BillingInvocationEntity invocation = invocationRepository.findByInvocationIdForUpdate(command.invocationId())
                .orElseThrow(() -> new BillingInvocationConflictException("物理调用资金记录不存在"));
        BillingInvocationResolutionEntity existing = resolutionRepository
                .findByInvocationId(command.invocationId())
                .orElse(null);
        if (existing != null) {
            assertReplay(existing, command);
            return existing;
        }
        if (!BillingInvocationState.UNKNOWN.name().equals(invocation.getState())) {
            throw new BillingInvocationConflictException(
                    "仅 UNKNOWN 物理调用允许裁决，当前状态: " + invocation.getState());
        }

        LocalDateTime now = LocalDateTime.now();
        ResolutionAmounts amounts = command.resolutionType() == PhysicalBillingResolutionType.CONFIRMED_USAGE
                ? settleConfirmedUsage(invocation, command, now)
                : cancelConfirmedNotBilled(invocation, command, now);
        BillingInvocationResolutionEntity resolution = buildResolution(invocation, command, amounts, now);
        return resolutionRepository.saveAndFlush(resolution);
    }

    private ResolutionAmounts settleConfirmedUsage(BillingInvocationEntity invocation,
                                                    ValidatedCommand command,
                                                    LocalDateTime now) {
        int actualTokens = Objects.requireNonNull(command.actualTokens());
        if (actualTokens > invocation.getMaxBillableTokens()) {
            throw new BillingInvocationConflictException("已确认 Token 超出预留上限，拒绝裁决");
        }
        long actualAmount = PhysicalBillingAmountCalculator.calculate(invocation, actualTokens);
        if (actualAmount > invocation.getReservedAmount()) {
            throw new BillingInvocationConflictException("已确认费用超出预留金额，拒绝裁决");
        }
        long refundAmount = Math.subtractExact(invocation.getReservedAmount(), actualAmount);
        if (refundAmount > 0) {
            refund(invocation, refundAmount, now);
            ledgerRepository.appendRefund(invocation, refundAmount, now);
        }
        ledgerRepository.appendClose(invocation, actualTokens, actualAmount, now);
        invocation.setActualTokens(actualTokens);
        invocation.setActualAmount(actualAmount);
        invocation.setRefundAmount(refundAmount);
        invocation.setProviderRequestId(command.providerRequestId());
        invocation.setState(BillingInvocationState.SETTLED.name());
        invocation.setFinalizedAt(now);
        invocation.setUpdateTime(now);
        invocationRepository.saveAndFlush(invocation);
        return new ResolutionAmounts(actualTokens, actualAmount, refundAmount);
    }

    private ResolutionAmounts cancelConfirmedNotBilled(BillingInvocationEntity invocation,
                                                        ValidatedCommand command,
                                                        LocalDateTime now) {
        long refundAmount = invocation.getReservedAmount();
        refund(invocation, refundAmount, now);
        ledgerRepository.appendCancel(invocation, now);
        invocation.setActualTokens(null);
        invocation.setActualAmount(0L);
        invocation.setRefundAmount(refundAmount);
        invocation.setProviderRequestId(command.providerRequestId());
        invocation.setState(BillingInvocationState.CANCELLED.name());
        invocation.setFinalizedAt(now);
        invocation.setUpdateTime(now);
        invocationRepository.saveAndFlush(invocation);
        return new ResolutionAmounts(null, 0L, refundAmount);
    }

    private void refund(BillingInvocationEntity invocation, long refundAmount, LocalDateTime now) {
        int updated = tenantRepository.increaseBalance(
                invocation.getTenantId(), refundAmount, NOT_DELETED, now);
        if (updated != 1) {
            throw new BillingPersistenceException("裁决退款写入租户余额失败", null);
        }
    }

    private BillingInvocationResolutionEntity buildResolution(
            BillingInvocationEntity invocation,
            ValidatedCommand command,
            ResolutionAmounts amounts,
            LocalDateTime now
    ) {
        BillingInvocationResolutionEntity resolution = new BillingInvocationResolutionEntity();
        resolution.setResolutionId(compactUuid());
        resolution.setInvocationId(invocation.getInvocationId());
        resolution.setTenantId(invocation.getTenantId());
        resolution.setResolutionType(command.resolutionType().name());
        resolution.setActualTokens(amounts.actualTokens());
        resolution.setActualAmount(amounts.actualAmount());
        resolution.setRefundAmount(amounts.refundAmount());
        resolution.setProviderRequestId(command.providerRequestId());
        resolution.setEvidenceHash(command.evidenceHash());
        resolution.setOperator(command.operator());
        resolution.setResolvedAt(now);
        resolution.setCreateTime(now);
        return resolution;
    }

    private void assertReplay(BillingInvocationResolutionEntity existing, ValidatedCommand command) {
        boolean same = existing.getInvocationId().equals(command.invocationId())
                && existing.getOperator().equals(command.operator())
                && existing.getEvidenceHash().equals(command.evidenceHash())
                && existing.getProviderRequestId().equals(command.providerRequestId())
                && existing.getResolutionType().equals(command.resolutionType().name())
                && Objects.equals(existing.getActualTokens(), command.actualTokens());
        if (!same) {
            throw new BillingInvocationConflictException("物理调用已存在不同的不可变裁决记录");
        }
    }

    private ValidatedCommand validate(PhysicalBillingResolutionCommand command) {
        if (command == null
                || invalidText(command.invocationId(), MAX_INVOCATION_ID_LENGTH)
                || invalidText(command.operator(), MAX_OPERATOR_LENGTH)
                || invalidText(command.providerRequestId(), MAX_PROVIDER_REQUEST_ID_LENGTH)
                || !StringUtils.hasText(command.evidenceHash())
                || !SHA256_PATTERN.matcher(command.evidenceHash().trim()).matches()
                || command.resolutionType() == null) {
            throw new BillingInvocationConflictException("物理调用裁决参数或证据不完整");
        }
        if (command.resolutionType() == PhysicalBillingResolutionType.CONFIRMED_USAGE) {
            if (command.actualTokens() == null || command.actualTokens() < 0) {
                throw new BillingInvocationConflictException("确认用量裁决必须提供非负实际 Token 数");
            }
        } else if (command.actualTokens() != null) {
            throw new BillingInvocationConflictException("确认未计费裁决不得携带实际 Token 数");
        }
        return new ValidatedCommand(
                command.invocationId().trim(),
                command.operator().trim(),
                command.evidenceHash().trim().toLowerCase(),
                command.providerRequestId().trim(),
                command.resolutionType(),
                command.actualTokens()
        );
    }

    private boolean invalidText(String value, int maxLength) {
        return !StringUtils.hasText(value) || value.trim().length() > maxLength;
    }

    private PhysicalBillingResolutionReceipt toReceipt(BillingInvocationResolutionEntity resolution) {
        PhysicalBillingResolutionType type = PhysicalBillingResolutionType.valueOf(resolution.getResolutionType());
        BillingInvocationState state = type == PhysicalBillingResolutionType.CONFIRMED_USAGE
                ? BillingInvocationState.SETTLED
                : BillingInvocationState.CANCELLED;
        return new PhysicalBillingResolutionReceipt(
                resolution.getResolutionId(),
                resolution.getInvocationId(),
                resolution.getTenantId(),
                type,
                state,
                resolution.getActualTokens(),
                resolution.getActualAmount(),
                resolution.getRefundAmount(),
                resolution.getProviderRequestId(),
                resolution.getEvidenceHash(),
                resolution.getOperator(),
                resolution.getResolvedAt()
        );
    }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private record ValidatedCommand(
            String invocationId,
            String operator,
            String evidenceHash,
            String providerRequestId,
            PhysicalBillingResolutionType resolutionType,
            Integer actualTokens
    ) {
    }

    private record ResolutionAmounts(Integer actualTokens, long actualAmount, long refundAmount) {
    }
}
