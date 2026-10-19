package com.lingsql.web;

import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.billing.BillingAdminService;
import com.lingsql.billing.BillingCommercialReadinessService;
import com.lingsql.billing.BillingReconcileLogService;
import com.lingsql.billing.VendorBillEvidenceDigest;
import com.lingsql.billing.acceptance.IBillingAcceptanceSession;
import com.lingsql.billing.invocation.BillingInvocationState;
import com.lingsql.billing.invocation.BillingInvocationWorkbenchService;
import com.lingsql.billing.invocation.IPhysicalBillingResolution;
import com.lingsql.billing.invocation.PhysicalBillingResolutionCommand;
import com.lingsql.billing.invocation.PhysicalBillingResolutionReceipt;
import com.lingsql.billing.invocation.PhysicalBillingResolutionType;
import com.lingsql.billing.model.BillingInvocationResolutionRequest;
import com.lingsql.billing.model.BillingUnknownInvocationQuery;
import com.lingsql.billing.model.BillingUnknownInvocationView;
import com.lingsql.billing.model.VendorBillEventView;
import com.lingsql.billing.model.VendorBillImportAudit;
import com.lingsql.billing.model.VendorBillImportReport;
import com.lingsql.billing.model.VendorBillImportRequest;
import com.lingsql.billing.model.VendorBillQuery;
import com.lingsql.billing.service.VendorBillImportService;
import com.lingsql.billing.service.VendorBillQueryService;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.common.result.PageResult;
import com.lingsql.rbac.security.RbacUserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 计费管理接口供应商账单导入与 UNKNOWN 裁决工作台测试。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
class BillingAdminControllerTest {

    private static final String CSV = "tenantId,providerName,externalBillId,amountMinor,currency,status,providerRequestId,vendorTokens\n"
            + "demo_sales,openai,bill-1,120,CNY,BILLED,req-1,32\n";

    private final BillingAdminService billingAdminService = Mockito.mock(BillingAdminService.class);

    private final AuditOperationExecutor auditOperationExecutor = Mockito.mock(AuditOperationExecutor.class);

    private final BillingReconcileLogService billingReconcileLogService = Mockito.mock(BillingReconcileLogService.class);

    private final BillingCommercialReadinessService billingCommercialReadinessService =
            Mockito.mock(BillingCommercialReadinessService.class);

    private final IPhysicalBillingResolution physicalBillingResolution = Mockito.mock(IPhysicalBillingResolution.class);

    private final IBillingAcceptanceSession billingAcceptanceSession = Mockito.mock(IBillingAcceptanceSession.class);

    private final VendorBillImportService vendorBillImportService = Mockito.mock(VendorBillImportService.class);

    private final VendorBillQueryService vendorBillQueryService = Mockito.mock(VendorBillQueryService.class);

    private final BillingInvocationWorkbenchService billingInvocationWorkbenchService =
            Mockito.mock(BillingInvocationWorkbenchService.class);

    private final List<Object> auditSuccessDetails = new ArrayList<>();

    private final BillingAdminController controller = new BillingAdminController(
            billingAdminService,
            auditOperationExecutor,
            billingReconcileLogService,
            billingCommercialReadinessService,
            physicalBillingResolution,
            billingAcceptanceSession,
            vendorBillImportService,
            vendorBillQueryService,
            billingInvocationWorkbenchService
    );

    @AfterEach
    void clearContext() {
        auditSuccessDetails.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void importVendorBillsShouldAuditDigestInsteadOfRawCsv() {
        bindSession("sys_tenant", List.of("SYS_ADMIN"));
        mockAuditExecuteCommittedResult();
        VendorBillImportReport report = new VendorBillImportReport(
                VendorBillEvidenceDigest.sha256Hex(CSV), 1, 1, 0, 0, Map.of("MATCHED", 1), List.of());
        when(vendorBillImportService.importCsv(CSV)).thenReturn(report);

        ApiResponse<VendorBillImportReport> response = controller.importVendorBills(
                new VendorBillImportRequest(CSV), null);

        assertEquals(200, response.getCode());
        assertEquals(report, response.getData());
        ArgumentCaptor<VendorBillImportAudit> captor = ArgumentCaptor.forClass(VendorBillImportAudit.class);
        verify(auditOperationExecutor).executeCommittedResult(
                eq("BILLING_VENDOR_BILL_IMPORT"),
                eq("BILLING_VENDOR_BILL"),
                captor.capture(),
                any(),
                any(),
                any()
        );
        VendorBillImportAudit intent = captor.getAllValues().get(0);
        assertEquals(VendorBillEvidenceDigest.sha256Hex(CSV), intent.csvSha256());
        assertEquals(CSV.length(), intent.csvLength());
        assertFalse(intent.toString().contains("bill-1"));
        VendorBillImportAudit success = (VendorBillImportAudit) auditSuccessDetails.get(0);
        assertEquals(1, success.dataRows());
        assertEquals(1, success.importedCount());
        assertEquals(Map.of("MATCHED", 1), success.matchSummary());
    }

    @Test
    void importVendorBillsShouldRejectNonSysAdmin() {
        bindSession("demo_sales", List.of("TENANT_ADMIN"));

        ApiResponse<VendorBillImportReport> response = controller.importVendorBills(
                new VendorBillImportRequest(CSV), null);

        assertEquals(403, response.getCode());
        verify(vendorBillImportService, never()).importCsv(anyString());
        verify(auditOperationExecutor, never()).executeCommittedResult(anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    void listVendorBillsShouldBindFilters() {
        bindSession("sys_tenant", List.of("SYS_ADMIN"));
        PageResult<VendorBillEventView> page = new PageResult<>(List.of(), 0L, 1, 10);
        when(vendorBillQueryService.query(any(VendorBillQuery.class), eq(1), eq(10))).thenReturn(page);

        ApiResponse<PageResult<VendorBillEventView>> response =
                controller.listVendorBills("demo_sales", "openai", "MISMATCH", "bill-1", 1, 10);

        assertEquals(200, response.getCode());
        assertSame(page, response.getData());
        ArgumentCaptor<VendorBillQuery> captor = ArgumentCaptor.forClass(VendorBillQuery.class);
        verify(vendorBillQueryService).query(captor.capture(), eq(1), eq(10));
        assertEquals("demo_sales", captor.getValue().getTenantId());
        assertEquals("openai", captor.getValue().getProviderName());
        assertEquals("MISMATCH", captor.getValue().getMatchState());
        assertEquals("bill-1", captor.getValue().getExternalBillId());
    }

    @Test
    void listUnknownInvocationsShouldReturnPage() {
        bindSession("sys_tenant", List.of("SYS_ADMIN"));
        PageResult<BillingUnknownInvocationView> page = new PageResult<>(List.of(), 0L, 1, 10);
        when(billingInvocationWorkbenchService.queryUnknown(any(BillingUnknownInvocationQuery.class), anyInt(), anyInt()))
                .thenReturn(page);

        ApiResponse<PageResult<BillingUnknownInvocationView>> response =
                controller.listUnknownInvocations("demo_sales", "openai", 1, 10);

        assertEquals(200, response.getCode());
        assertSame(page, response.getData());
        ArgumentCaptor<BillingUnknownInvocationQuery> captor =
                ArgumentCaptor.forClass(BillingUnknownInvocationQuery.class);
        verify(billingInvocationWorkbenchService).queryUnknown(captor.capture(), eq(1), eq(10));
        assertEquals("demo_sales", captor.getValue().getTenantId());
        assertEquals("openai", captor.getValue().getProviderName());
    }

    @Test
    void listUnknownInvocationsShouldRejectNonSysAdmin() {
        bindSession("demo_sales", List.of("TENANT_ADMIN"));

        ApiResponse<PageResult<BillingUnknownInvocationView>> response =
                controller.listUnknownInvocations(null, null, 1, 10);

        assertEquals(403, response.getCode());
        verify(billingInvocationWorkbenchService, never()).queryUnknown(any(), anyInt(), anyInt());
    }

    @Test
    void resolveInvocationShouldForwardOperatorAndAudit() {
        bindSession("sys_tenant", List.of("SYS_ADMIN"));
        mockAuditExecuteCommittedResult();
        PhysicalBillingResolutionReceipt receipt = new PhysicalBillingResolutionReceipt(
                "resolution-1",
                "inv-1",
                "sys_tenant",
                PhysicalBillingResolutionType.CONFIRMED_USAGE,
                BillingInvocationState.SETTLED,
                32,
                100L,
                0L,
                "req-1",
                "a".repeat(64),
                "1",
                LocalDateTime.now()
        );
        when(physicalBillingResolution.resolve(any())).thenReturn(receipt);

        ApiResponse<PhysicalBillingResolutionReceipt> response = controller.resolveInvocation(
                "inv-1",
                new BillingInvocationResolutionRequest("a".repeat(64), "req-1",
                        PhysicalBillingResolutionType.CONFIRMED_USAGE, 32),
                null);

        assertEquals(200, response.getCode());
        ArgumentCaptor<PhysicalBillingResolutionCommand> captor =
                ArgumentCaptor.forClass(PhysicalBillingResolutionCommand.class);
        verify(physicalBillingResolution).resolve(captor.capture());
        assertEquals("inv-1", captor.getValue().invocationId());
        assertEquals("1", captor.getValue().operator());
        assertEquals("a".repeat(64), captor.getValue().evidenceHash());
        assertEquals("req-1", captor.getValue().providerRequestId());
        assertEquals(32, captor.getValue().actualTokens());
    }

    private void mockAuditExecuteCommittedResult() {
        when(auditOperationExecutor.executeCommittedResult(anyString(), anyString(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    Function<Object, Object> mapper = castMapper(invocation.getArgument(5));
                    Object result = supplier.get();
                    auditSuccessDetails.add(mapper.apply(result));
                    return ApiResponse.success(result);
                });
    }

    @SuppressWarnings("unchecked")
    private Function<Object, Object> castMapper(Object mapper) {
        return (Function<Object, Object>) mapper;
    }

    private void bindSession(String tenantId, List<String> roles) {
        RbacUserSession session = new RbacUserSession();
        session.setUserId(1L);
        session.setTenantId(tenantId);
        session.setRoles(roles);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(session, "token", List.of())
        );
    }
}
