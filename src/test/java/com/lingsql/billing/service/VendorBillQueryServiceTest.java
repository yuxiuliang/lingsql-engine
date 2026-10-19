package com.lingsql.billing.service;

import com.lingsql.billing.VendorBillEvidenceDigest;
import com.lingsql.billing.entity.VendorBillEvent;
import com.lingsql.billing.invocation.BillingInvocationEntity;
import com.lingsql.billing.invocation.BillingInvocationRepository;
import com.lingsql.billing.model.VendorBillEventView;
import com.lingsql.billing.model.VendorBillMatchState;
import com.lingsql.billing.model.VendorBillQuery;
import com.lingsql.billing.repository.VendorBillEventRepository;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.pagination.PageParam;
import com.lingsql.common.pagination.PageParamResolver;
import com.lingsql.common.result.PageResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 供应商账单查询服务测试。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
class VendorBillQueryServiceTest {

    private final VendorBillEventRepository repository = Mockito.mock(VendorBillEventRepository.class);

    private final BillingInvocationRepository invocationRepository = Mockito.mock(BillingInvocationRepository.class);

    private final PageParamResolver pageParamResolver = Mockito.mock(PageParamResolver.class);

    private final VendorBillQueryService service = new VendorBillQueryService(repository, invocationRepository,
            pageParamResolver);

    @Test
    void shouldReturnEnrichedViewForMatchedBill() {
        when(pageParamResolver.resolve(anyInt(), anyInt())).thenReturn(new PageParam(1, 10));
        VendorBillEvent event = bill(VendorBillMatchState.MISMATCH);
        when(repository.findAll(ArgumentMatchers.<Specification<VendorBillEvent>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));
        BillingInvocationEntity invocation = new BillingInvocationEntity();
        invocation.setInvocationId("inv-1");
        invocation.setState("SETTLED");
        invocation.setActualAmount(100L);
        invocation.setActualTokens(32);
        when(invocationRepository.findByInvocationIdIn(any(Collection.class))).thenReturn(List.of(invocation));

        PageResult<VendorBillEventView> result = service.query(new VendorBillQuery(), 1, 10);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getList().size());
        VendorBillEventView view = result.getList().get(0);
        assertEquals("bill-1", view.externalBillId());
        assertEquals("req-1", view.providerRequestId());
        assertEquals(VendorBillMatchState.MISMATCH.name(), view.matchState());
        assertEquals(VendorBillMatchState.MISMATCH.getDescription(), view.matchStateDescription());
        assertEquals("inv-1", view.matchedInvocationId());
        assertEquals("SETTLED", view.matchedState());
        assertEquals(100L, view.matchedActualAmount());
        assertEquals(32, view.matchedActualTokens());
        assertEquals(VendorBillEvidenceDigest.digest(event), view.evidenceHash());
        assertTrue(view.matchTime() != null);
    }

    @Test
    void shouldSkipInvocationLookupWhenNoMatchedBill() {
        when(pageParamResolver.resolve(anyInt(), anyInt())).thenReturn(new PageParam(1, 10));
        VendorBillEvent event = bill(VendorBillMatchState.NOT_FOUND);
        event.setMatchedInvocationId(null);
        when(repository.findAll(ArgumentMatchers.<Specification<VendorBillEvent>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));

        PageResult<VendorBillEventView> result = service.query(new VendorBillQuery(), 1, 10);

        assertNull(result.getList().get(0).matchedState());
        verify(invocationRepository, never()).findByInvocationIdIn(any(Collection.class));
    }

    @Test
    void shouldReturnEmptyPageWhenNoBills() {
        when(pageParamResolver.resolve(anyInt(), anyInt())).thenReturn(new PageParam(1, 10));
        when(repository.findAll(ArgumentMatchers.<Specification<VendorBillEvent>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResult<VendorBillEventView> result = service.query(new VendorBillQuery(), 1, 10);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
        verify(invocationRepository, never()).findByInvocationIdIn(any(Collection.class));
    }

    @Test
    void shouldRejectInvalidMatchStateFilter() {
        VendorBillQuery query = new VendorBillQuery();
        query.setMatchState("NO_SUCH_STATE");

        assertThrows(BizException.class, () -> service.query(query, 1, 10));
    }

    @Test
    void shouldNormalizeLowerCaseMatchStateFilter() {
        when(pageParamResolver.resolve(anyInt(), anyInt())).thenReturn(new PageParam(1, 10));
        when(repository.findAll(ArgumentMatchers.<Specification<VendorBillEvent>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        VendorBillQuery query = new VendorBillQuery();
        query.setMatchState("matched");

        PageResult<VendorBillEventView> result = service.query(query, 1, 10);

        assertEquals(0L, result.getTotal());
    }

    private VendorBillEvent bill(VendorBillMatchState state) {
        VendorBillEvent event = new VendorBillEvent();
        event.setId(9L);
        event.setTenantId("demo_sales");
        event.setProviderName("openai");
        event.setExternalBillId("bill-1");
        event.setProviderRequestId("req-1");
        event.setAmountMinor(120L);
        event.setVendorTokens(32);
        event.setCurrency("CNY");
        event.setStatus("BILLED");
        event.setMatchedInvocationId("inv-1");
        event.setMatchState(state.name());
        event.setMatchTime(LocalDateTime.now());
        event.setCreatedAt(LocalDateTime.now());
        return event;
    }
}
