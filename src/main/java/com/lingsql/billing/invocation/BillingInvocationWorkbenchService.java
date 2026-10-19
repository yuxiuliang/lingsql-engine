package com.lingsql.billing.invocation;

import com.lingsql.billing.model.BillingUnknownInvocationQuery;
import com.lingsql.billing.model.BillingUnknownInvocationView;
import com.lingsql.common.pagination.PageParam;
import com.lingsql.common.pagination.PageParamResolver;
import com.lingsql.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * UNKNOWN 物理调用裁决工作台查询服务。
 *
 * <p>只读列出处于 UNKNOWN 的物理调用，供管理员核对供应商账单后发起裁决；
 * 本服务不修改资金状态，裁决仍只能经 {@link IPhysicalBillingResolution} 执行。</p>
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingInvocationWorkbenchService {

    private final BillingInvocationRepository invocationRepository;

    private final PageParamResolver pageParamResolver;

    /**
     * 分页查询 UNKNOWN 物理调用。
     *
     * @param query 查询条件
     * @param page  页码
     * @param size  每页大小
     * @return 分页工作台视图
     * @author 余炘洋
     * @date 2026-10-17
     */
    public PageResult<BillingUnknownInvocationView> queryUnknown(BillingUnknownInvocationQuery query, int page, int size) {
        BillingUnknownInvocationQuery safeQuery = query == null ? new BillingUnknownInvocationQuery() : query;
        PageParam param = pageParamResolver.resolve(page, size);
        Specification<BillingInvocationEntity> specification = (root, criteriaQuery, criteriaBuilder) -> {
            var predicate = criteriaBuilder.equal(root.get("state"), BillingInvocationState.UNKNOWN.name());
            if (StringUtils.hasText(safeQuery.getTenantId())) {
                predicate = criteriaBuilder.and(predicate,
                        criteriaBuilder.equal(root.get("tenantId"), safeQuery.getTenantId().trim()));
            }
            if (StringUtils.hasText(safeQuery.getProviderName())) {
                predicate = criteriaBuilder.and(predicate,
                        criteriaBuilder.equal(root.get("providerName"), safeQuery.getProviderName().trim()));
            }
            return predicate;
        };
        PageRequest pageable = PageRequest.of(Math.max(param.page() - 1, 0), param.size(),
                Sort.by(Sort.Direction.DESC, "id"));
        Page<BillingInvocationEntity> result = invocationRepository.findAll(specification, pageable);
        List<BillingUnknownInvocationView> views = result.getContent().stream().map(this::toView).toList();
        return new PageResult<>(views, result.getTotalElements(), param.page(), param.size());
    }

    private BillingUnknownInvocationView toView(BillingInvocationEntity entity) {
        return new BillingUnknownInvocationView(
                entity.getInvocationId(),
                entity.getTenantId(),
                entity.getWorkflowRequestId(),
                entity.getLogicalCallNo(),
                entity.getStage(),
                entity.getCaller(),
                entity.getProviderName(),
                entity.getModelName(),
                entity.getModelVersion(),
                entity.getMaxBillableTokens(),
                entity.getUnitCost(),
                entity.getReservedAmount(),
                entity.getFailureCode(),
                entity.getReservedAt(),
                entity.getDispatchedAt());
    }
}
