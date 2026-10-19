package com.lingsql.billing.service;

import com.lingsql.billing.VendorBillEvidenceDigest;
import com.lingsql.billing.entity.VendorBillEvent;
import com.lingsql.billing.invocation.BillingInvocationEntity;
import com.lingsql.billing.invocation.BillingInvocationRepository;
import com.lingsql.billing.invocation.BillingInvocationState;
import com.lingsql.billing.model.VendorBillImportReport;
import com.lingsql.billing.model.VendorBillImportRow;
import com.lingsql.billing.model.VendorBillMatchState;
import com.lingsql.billing.repository.VendorBillEventRepository;
import com.lingsql.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 供应商账单导入服务测试。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
class VendorBillImportServiceTest {

    private static final String HEADER =
            "tenantId,providerName,externalBillId,amountMinor,currency,status,providerRequestId,vendorTokens";

    private final VendorBillEventRepository repository = Mockito.mock(VendorBillEventRepository.class);

    private final BillingInvocationRepository invocationRepository = Mockito.mock(BillingInvocationRepository.class);

    private final VendorBillImportService service = new VendorBillImportService(repository, invocationRepository);

    @Test
    void shouldImportAndMatchSettledInvocation() {
        when(invocationRepository.findFirstByProviderRequestId("req-1"))
                .thenReturn(Optional.of(invocation(BillingInvocationState.SETTLED, 120L, 32)));
        ArgumentCaptor<VendorBillEvent> captor = ArgumentCaptor.forClass(VendorBillEvent.class);

        VendorBillImportReport report = service.importCsv(HEADER + "\n"
                + "demo_sales,openai,bill-1,120,CNY,BILLED,req-1,32\n");

        assertEquals(1, report.totalRows());
        assertEquals(1, report.importedCount());
        assertEquals(0, report.duplicateCount());
        assertEquals(0, report.rejectedCount());
        assertEquals(1, report.matchSummary().get(VendorBillMatchState.MATCHED.name()));
        assertEquals(VendorBillEvidenceDigest.sha256Hex(HEADER + "\n"
                + "demo_sales,openai,bill-1,120,CNY,BILLED,req-1,32\n"), report.csvSha256());
        verify(repository).save(captor.capture());
        VendorBillEvent saved = captor.getValue();
        assertEquals("demo_sales", saved.getTenantId());
        assertEquals("openai", saved.getProviderName());
        assertEquals("bill-1", saved.getExternalBillId());
        assertEquals("req-1", saved.getProviderRequestId());
        assertEquals(120L, saved.getAmountMinor());
        assertEquals(32, saved.getVendorTokens());
        assertEquals("CNY", saved.getCurrency());
        assertEquals("BILLED", saved.getStatus());
        assertEquals("inv-1", saved.getMatchedInvocationId());
        assertEquals(VendorBillMatchState.MATCHED.name(), saved.getMatchState());
        assertTrue(saved.getMatchTime() != null);
        assertTrue(saved.getCreatedAt() != null);
    }

    @Test
    void shouldMarkMismatchWhenAmountOrTokensDiffer() {
        when(invocationRepository.findFirstByProviderRequestId("req-1"))
                .thenReturn(Optional.of(invocation(BillingInvocationState.SETTLED, 100L, 32)));
        ArgumentCaptor<VendorBillEvent> captor = ArgumentCaptor.forClass(VendorBillEvent.class);

        VendorBillImportReport report = service.importCsv(HEADER + "\n"
                + "demo_sales,openai,bill-2,120,CNY,BILLED,req-1,32\n");

        assertEquals(1, report.matchSummary().get(VendorBillMatchState.MISMATCH.name()));
        verify(repository).save(captor.capture());
        assertEquals(VendorBillMatchState.MISMATCH.name(), captor.getValue().getMatchState());
    }

    @Test
    void shouldMarkMismatchWhenTokensDiffer() {
        when(invocationRepository.findFirstByProviderRequestId("req-1"))
                .thenReturn(Optional.of(invocation(BillingInvocationState.SETTLED, 120L, 64)));
        ArgumentCaptor<VendorBillEvent> captor = ArgumentCaptor.forClass(VendorBillEvent.class);

        service.importCsv(HEADER + "\n"
                + "demo_sales,openai,bill-3,120,CNY,BILLED,req-1,32\n");

        verify(repository).save(captor.capture());
        assertEquals(VendorBillMatchState.MISMATCH.name(), captor.getValue().getMatchState());
    }

    @Test
    void shouldMarkUnknownInvocationState() {
        when(invocationRepository.findFirstByProviderRequestId("req-1"))
                .thenReturn(Optional.of(invocation(BillingInvocationState.UNKNOWN, null, null)));
        ArgumentCaptor<VendorBillEvent> captor = ArgumentCaptor.forClass(VendorBillEvent.class);

        VendorBillImportReport report = service.importCsv(HEADER + "\n"
                + "demo_sales,openai,bill-4,120,CNY,BILLED,req-1,32\n");

        assertEquals(1, report.matchSummary().get(VendorBillMatchState.UNKNOWN.name()));
        verify(repository).save(captor.capture());
        assertEquals("inv-1", captor.getValue().getMatchedInvocationId());
    }

    @Test
    void shouldMarkPendingForDispatchedInvocation() {
        when(invocationRepository.findFirstByProviderRequestId("req-1"))
                .thenReturn(Optional.of(invocation(BillingInvocationState.DISPATCHED, null, null)));
        ArgumentCaptor<VendorBillEvent> captor = ArgumentCaptor.forClass(VendorBillEvent.class);

        VendorBillImportReport report = service.importCsv(HEADER + "\n"
                + "demo_sales,openai,bill-5,120,CNY,BILLED,req-1,32\n");

        assertEquals(1, report.matchSummary().get(VendorBillMatchState.PENDING.name()));
        verify(repository).save(captor.capture());
        assertEquals(VendorBillMatchState.PENDING.name(), captor.getValue().getMatchState());
    }

    @Test
    void shouldMarkConflictForCancelledInvocation() {
        when(invocationRepository.findFirstByProviderRequestId("req-1"))
                .thenReturn(Optional.of(invocation(BillingInvocationState.CANCELLED, 0L, 0)));
        ArgumentCaptor<VendorBillEvent> captor = ArgumentCaptor.forClass(VendorBillEvent.class);

        VendorBillImportReport report = service.importCsv(HEADER + "\n"
                + "demo_sales,openai,bill-6,120,CNY,BILLED,req-1,32\n");

        assertEquals(1, report.matchSummary().get(VendorBillMatchState.CONFLICT.name()));
        verify(repository).save(captor.capture());
        assertEquals(VendorBillMatchState.CONFLICT.name(), captor.getValue().getMatchState());
    }

    @Test
    void shouldMarkNotFoundWhenInvocationMissing() {
        when(invocationRepository.findFirstByProviderRequestId("req-unknown")).thenReturn(Optional.empty());
        ArgumentCaptor<VendorBillEvent> captor = ArgumentCaptor.forClass(VendorBillEvent.class);

        VendorBillImportReport report = service.importCsv(HEADER + "\n"
                + "demo_sales,openai,bill-7,120,CNY,BILLED,req-unknown,32\n");

        assertEquals(1, report.matchSummary().get(VendorBillMatchState.NOT_FOUND.name()));
        verify(repository).save(captor.capture());
        assertNull(captor.getValue().getMatchedInvocationId());
    }

    @Test
    void shouldMarkNoReferenceWhenOldHeaderWithoutReferenceColumn() {
        ArgumentCaptor<VendorBillEvent> captor = ArgumentCaptor.forClass(VendorBillEvent.class);

        VendorBillImportReport report = service.importCsv(
                "tenantId,providerName,externalBillId,amountMinor,currency,status\n"
                        + "demo_sales,openai,bill-8,120,CNY,BILLED\n");

        assertEquals(1, report.importedCount());
        assertEquals(1, report.matchSummary().get(VendorBillMatchState.NO_REFERENCE.name()));
        verify(repository).save(captor.capture());
        assertNull(captor.getValue().getProviderRequestId());
        assertNull(captor.getValue().getVendorTokens());
    }

    @Test
    void shouldRejectInvalidRowsAndKeepValidOnes() {
        when(invocationRepository.findFirstByProviderRequestId("req-1"))
                .thenReturn(Optional.of(invocation(BillingInvocationState.SETTLED, 120L, 32)));

        VendorBillImportReport report = service.importCsv(HEADER + "\n"
                + "demo_sales,openai,bill-9,abc,CNY,BILLED,,\n"
                + "demo_sales,openai,bill-10,120,CNY,BILLED,req-1,32\n"
                + "bad tenant!,openai,bill-11,120,CNY,BILLED,,\n");

        assertEquals(3, report.totalRows());
        assertEquals(1, report.importedCount());
        assertEquals(2, report.rejectedCount());
        List<VendorBillImportRow> rows = report.rows();
        assertEquals(3, rows.size());
        assertEquals(VendorBillImportService.OUTCOME_REJECTED, rows.get(0).outcome());
        assertEquals(2, rows.get(0).lineNumber());
        assertTrue(rows.get(0).message().contains("整数"));
        assertEquals(VendorBillImportService.OUTCOME_IMPORTED, rows.get(1).outcome());
        assertEquals(VendorBillMatchState.MATCHED.name(), rows.get(1).matchState());
        assertEquals(VendorBillImportService.OUTCOME_REJECTED, rows.get(2).outcome());
        assertEquals("bill-11", rows.get(2).externalBillId());
        assertTrue(rows.get(2).message().contains("租户标识"));
    }

    @Test
    void shouldRejectRowWhenColumnCountMismatch() {
        VendorBillImportReport report = service.importCsv(HEADER + "\n"
                + "demo_sales,openai,bill-12,120,CNY\n");

        assertEquals(1, report.rejectedCount());
        assertTrue(report.rows().get(0).message().contains("列数"));
        verify(repository, never()).save(any(VendorBillEvent.class));
    }

    @Test
    void shouldReportDuplicatesInsideFileAndAgainstDatabase() {
        VendorBillEvent existing = new VendorBillEvent();
        existing.setProviderName("openai");
        existing.setExternalBillId("bill-14");
        when(repository.findFirstByProviderNameAndExternalBillId("openai", "bill-14"))
                .thenReturn(Optional.of(existing));

        VendorBillImportReport report = service.importCsv(HEADER + "\n"
                + "demo_sales,openai,bill-13,120,CNY,BILLED,,\n"
                + "demo_sales,openai,bill-13,120,CNY,BILLED,,\n"
                + "demo_sales,openai,bill-14,120,CNY,BILLED,,\n");

        assertEquals(3, report.totalRows());
        assertEquals(1, report.importedCount());
        assertEquals(2, report.duplicateCount());
        assertEquals(2, report.rows().stream()
                .filter(row -> VendorBillImportService.OUTCOME_DUPLICATE.equals(row.outcome()))
                .count());
        verify(repository, Mockito.times(1)).save(any(VendorBillEvent.class));
    }

    @Test
    void shouldSupportQuotedValuesAndCommentLines() {
        VendorBillImportReport report = service.importCsv(HEADER + "\n"
                + "# 本月账单\n"
                + "\"demo_sales\", \"openai\", \"bill-15\" , 120, CNY, BILLED,,\n"
                + "\n");

        assertEquals(1, report.totalRows());
        assertEquals(1, report.importedCount());
        assertEquals("bill-15", report.rows().get(0).externalBillId());
    }

    @Test
    void shouldRejectUnclosedQuote() {
        VendorBillImportReport report = service.importCsv(HEADER + "\n"
                + "\"demo_sales,openai,bill-16,120,CNY,BILLED,,\n");

        assertEquals(1, report.rejectedCount());
        assertTrue(report.rows().get(0).message().contains("引号"));
        verify(repository, never()).save(any(VendorBillEvent.class));
    }

    @Test
    void shouldAcceptHeaderAliases() {
        VendorBillImportReport report = service.importCsv(
                "tenant_id,provider_name,external_bill_id,amount_minor,currency,status,provider_request_id,vendor_tokens\n"
                        + "demo_sales,openai,bill-17,120,CNY,BILLED,,\n");

        assertEquals(1, report.importedCount());
    }

    @Test
    void shouldRejectMissingRequiredHeader() {
        BizException exception = assertThrows(BizException.class,
                () -> service.importCsv("tenantId,providerName,externalBillId\n"
                        + "demo_sales,openai,bill-18\n"));

        assertTrue(exception.getMessage().contains("缺少必需列"));
    }

    @Test
    void shouldRejectBlankCsv() {
        assertThrows(BizException.class, () -> service.importCsv("   "));
        assertThrows(BizException.class, () -> service.importCsv(null));
    }

    @Test
    void shouldRejectCsvWithoutDataRows() {
        assertThrows(BizException.class, () -> service.importCsv(HEADER + "\n\n# 无数据\n"));
    }

    @Test
    void shouldRejectOversizedCsv() {
        String huge = "x".repeat(VendorBillImportService.MAX_CSV_LENGTH + 1);

        BizException exception = assertThrows(BizException.class, () -> service.importCsv(huge));

        assertTrue(exception.getMessage().contains("长度超过上限"));
    }

    @Test
    void shouldRejectTooManyRows() {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (int i = 0; i <= VendorBillImportService.MAX_ROWS; i++) {
            csv.append("demo_sales,openai,bill-").append(i).append(",1,CNY,BILLED,,\n");
        }

        BizException exception = assertThrows(BizException.class, () -> service.importCsv(csv.toString()));

        assertTrue(exception.getMessage().contains("行数超过单次导入上限"));
    }

    @Test
    void shouldRejectInvalidReferenceCharset() {
        VendorBillImportReport report = service.importCsv(HEADER + "\n"
                + "demo_sales,openai,bill-19,120,CNY,BILLED,req 1;drop,\n");

        assertEquals(1, report.rejectedCount());
        assertTrue(report.rows().get(0).message().contains("供应商请求标识"));
        verify(invocationRepository, never()).findFirstByProviderRequestId(anyString());
    }

    private BillingInvocationEntity invocation(BillingInvocationState state, Long actualAmount, Integer actualTokens) {
        BillingInvocationEntity entity = new BillingInvocationEntity();
        entity.setInvocationId("inv-1");
        entity.setState(state.name());
        entity.setActualAmount(actualAmount);
        entity.setActualTokens(actualTokens);
        return entity;
    }
}
