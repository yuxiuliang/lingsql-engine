package com.lingsql.billing.invocation;

import com.lingsql.billing.model.BillingUnknownInvocationQuery;
import com.lingsql.billing.model.BillingUnknownInvocationView;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * UNKNOWN 物理调用裁决工作台查询服务测试。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
class BillingInvocationWorkbenchServiceTest {

    private final BillingInvocationRepository invocationRepository = Mockito.mock(BillingInvocationRepository.class);

    private final PageParamResolver pageParamResolver = Mockito.mock(PageParamResolver.class);

    private final BillingInvocationWorkbenchService service = new BillingInvocationWorkbenchService(invocationRepository,
            pageParamResolver);

    @Test
    void shouldReturnUnknownInvocationsWithMinimalFields() {
        when(pageParamResolver.resolve(anyInt(), anyInt())).thenReturn(new PageParam(1, 10));
        when(invocationRepository.findAll(ArgumentMatchers.<Specification<BillingInvocationEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity())));

        PageResult<BillingUnknownInvocationView> result = service.queryUnknown(new BillingUnknownInvocationQuery(), 1, 10);

        assertEquals(1L, result.getTotal());
        BillingUnknownInvocationView view = result.getList().get(0);
        assertEquals("inv-1", view.invocationId());
        assertEquals("demo_sales", view.tenantId());
        assertEquals("workflow-1", view.workflowRequestId());
        assertEquals(2, view.logicalCallNo());
        assertEquals("SQL_ARCHITECT", view.stage());
        assertEquals("agent", view.caller());
        assertEquals("openai", view.providerName());
        assertEquals("gpt-4o", view.modelName());
        assertEquals("2026-08", view.modelVersion());
        assertEquals(1000, view.maxBillableTokens());
        assertEquals(10L, view.unitCost());
        assertEquals(100L, view.reservedAmount());
        assertEquals("USAGE_MISSING", view.failureCode());
        assertTrue(view.reservedAt() != null);
        assertTrue(view.dispatchedAt() != null);
    }

    @Test
    void shouldReturnEmptyPageWhenNoUnknownInvocations() {
        when(pageParamResolver.resolve(anyInt(), anyInt())).thenReturn(new PageParam(1, 10));
        when(invocationRepository.findAll(ArgumentMatchers.<Specification<BillingInvocationEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        BillingUnknownInvocationQuery query = new BillingUnknownInvocationQuery();
        query.setTenantId("demo_sales");
        query.setProviderName("openai");
        PageResult<BillingUnknownInvocationView> result = service.queryUnknown(query, 1, 10);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }

    private BillingInvocationEntity entity() {
        BillingInvocationEntity entity = new BillingInvocationEntity();
        entity.setInvocationId("inv-1");
        entity.setTenantId("demo_sales");
        entity.setWorkflowRequestId("workflow-1");
        entity.setLogicalCallNo(2);
        entity.setStage("SQL_ARCHITECT");
        entity.setCaller("agent");
        entity.setProviderName("openai");
        entity.setModelName("gpt-4o");
        entity.setModelVersion("2026-08");
        entity.setMaxBillableTokens(1000);
        entity.setUnitCost(10L);
        entity.setReservedAmount(100L);
        entity.setFailureCode("USAGE_MISSING");
        entity.setState(BillingInvocationState.UNKNOWN.name());
        entity.setReservedAt(LocalDateTime.now());
        entity.setDispatchedAt(LocalDateTime.now());
        return entity;
    }
}
