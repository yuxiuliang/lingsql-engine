package com.lingsql.billing.invocation;

import com.lingsql.billing.BillingBalanceService;
import com.lingsql.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UNKNOWN 物理调用裁决：确认未计费时的退款与账本行为。
 *
 * <p>背景（2026-10-27 真机缺陷）：调用失败进入 UNKNOWN 时，账本里已经写过一行 REFUND（event_seq=2）。
 * 而「确认未计费」裁决原本会先 {@code refund()} 再 {@code appendCancel()}（同样用 event_seq=2）：
 * ① 唯一键冲突导致裁决永远 500；② 若键没拦住，客户会被**退两次款**。
 * 修复后：已存在退款行时只收口状态，不再动钱、不再写账本。</p>
 *
 * @author 余炘洋
 * @date 2026-10-27
 */
class PhysicalBillingResolutionImplTest {

    private BillingInvocationRepository invocationRepository;
    private BillingInvocationResolutionRepository resolutionRepository;
    private PhysicalBillingLedgerRepository ledgerRepository;
    private TenantRepository tenantRepository;
    private BillingBalanceService balanceService;
    private PhysicalBillingResolutionImpl impl;

    private static final String INVOCATION_ID = "a".repeat(64);
    private static final String EVIDENCE_HASH = "b".repeat(64);

    @BeforeEach
    void setUp() {
        invocationRepository = mock(BillingInvocationRepository.class);
        resolutionRepository = mock(BillingInvocationResolutionRepository.class);
        ledgerRepository = mock(PhysicalBillingLedgerRepository.class);
        tenantRepository = mock(TenantRepository.class);
        balanceService = mock(BillingBalanceService.class);

        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        impl = new PhysicalBillingResolutionImpl(invocationRepository, resolutionRepository, ledgerRepository,
                tenantRepository, balanceService, txManager);

        BillingInvocationEntity invocation = new BillingInvocationEntity();
        invocation.setInvocationId(INVOCATION_ID);
        invocation.setTenantId("demo_sales");
        invocation.setState(BillingInvocationState.UNKNOWN.name());
        invocation.setReservedAmount(6223L);
        invocation.setMaxBillableTokens(6223);
        invocation.setModelName("LINGSQL-DEMO-STUB");
        invocation.setWorkflowRequestId("wf-1");
        invocation.setStage("SQL_ARCHITECT");
        invocation.setLogicalCallNo(1);
        invocation.setCreateTime(LocalDateTime.now());
        when(invocationRepository.findByInvocationIdForUpdate(INVOCATION_ID)).thenReturn(Optional.of(invocation));
        when(resolutionRepository.findByInvocationId(anyString())).thenReturn(Optional.empty());
        when(resolutionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private PhysicalBillingResolutionCommand notBilledCommand() {
        return new PhysicalBillingResolutionCommand(INVOCATION_ID, "tester", EVIDENCE_HASH,
                "NO-REPLY", PhysicalBillingResolutionType.CONFIRMED_NOT_BILLED, null);
    }

    @Test
    void shouldNotRefundTwiceWhenRefundLedgerRowAlreadyExists() {
        when(ledgerRepository.hasRefundEvent(INVOCATION_ID)).thenReturn(true);

        impl.resolve(notBilledCommand());

        // 关键断言：钱不再动、账本不再写
        verify(tenantRepository, never()).increaseBalance(anyString(), anyLong(), any(), any());
        verify(ledgerRepository, never()).appendCancel(any(), any());
    }

    @Test
    void shouldRefundOnceWhenNoRefundLedgerRowExists() {
        when(ledgerRepository.hasRefundEvent(INVOCATION_ID)).thenReturn(false);
        when(tenantRepository.increaseBalance(anyString(), anyLong(), any(), any())).thenReturn(1);

        impl.resolve(notBilledCommand());

        ArgumentCaptor<Long> amount = ArgumentCaptor.forClass(Long.class);
        verify(tenantRepository).increaseBalance(eq("demo_sales"), amount.capture(), any(), any());
        assertEquals(6223L, amount.getValue(), "应退回预留金额，且只退一次");
        verify(ledgerRepository).appendCancel(any(), any());
    }
}
