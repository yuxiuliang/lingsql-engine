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
import com.lingsql.common.exception.ErrorCode;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 供应商账单查询服务。
 *
 * <p>只读查询导入账单与匹配结果，并附带匹配到的物理调用结算快照与账单证据摘要，
 * 供供应商账单列表与 UNKNOWN 裁决工作台展示。</p>
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VendorBillQueryService {

    private final VendorBillEventRepository repository;

    private final BillingInvocationRepository invocationRepository;

    private final PageParamResolver pageParamResolver;

    /**
     * 分页查询供应商账单。
     *
     * @param query 查询条件
     * @param page  页码
     * @param size  每页大小
     * @return 分页账单视图
     * @author 余炘洋
     * @date 2026-10-17
     */
    public PageResult<VendorBillEventView> query(VendorBillQuery query, int page, int size) {
        VendorBillQuery safeQuery = query == null ? new VendorBillQuery() : query;
        String matchState = normalizeMatchState(safeQuery.getMatchState());
        PageParam param = pageParamResolver.resolve(page, size);
        Specification<VendorBillEvent> specification = (root, criteriaQuery, criteriaBuilder) -> {
            var predicate = criteriaBuilder.conjunction();
            if (StringUtils.hasText(safeQuery.getTenantId())) {
                predicate = criteriaBuilder.and(predicate,
                        criteriaBuilder.equal(root.get("tenantId"), safeQuery.getTenantId().trim()));
            }
            if (StringUtils.hasText(safeQuery.getProviderName())) {
                predicate = criteriaBuilder.and(predicate,
                        criteriaBuilder.equal(root.get("providerName"), safeQuery.getProviderName().trim()));
            }
            if (matchState != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("matchState"), matchState));
            }
            if (StringUtils.hasText(safeQuery.getExternalBillId())) {
                predicate = criteriaBuilder.and(predicate,
                        criteriaBuilder.equal(root.get("externalBillId"), safeQuery.getExternalBillId().trim()));
            }
            return predicate;
        };
        PageRequest pageable = PageRequest.of(Math.max(param.page() - 1, 0), param.size(),
                Sort.by(Sort.Direction.DESC, "id"));
        Page<VendorBillEvent> result = repository.findAll(specification, pageable);
        Map<String, BillingInvocationEntity> matched = loadMatchedInvocations(result.getContent());
        List<VendorBillEventView> views = result.getContent().stream()
                .map(event -> toView(event, matchedInvocation(event, matched)))
                .toList();
        return new PageResult<>(views, result.getTotalElements(), param.page(), param.size());
    }

    private BillingInvocationEntity matchedInvocation(VendorBillEvent event,
                                                      Map<String, BillingInvocationEntity> matched) {
        String invocationId = event.getMatchedInvocationId();
        if (!StringUtils.hasText(invocationId)) {
            return null;
        }
        return matched.get(invocationId);
    }

    private Map<String, BillingInvocationEntity> loadMatchedInvocations(List<VendorBillEvent> events) {
        Set<String> invocationIds = events.stream()
                .map(VendorBillEvent::getMatchedInvocationId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (invocationIds.isEmpty()) {
            return Map.of();
        }
        return invocationRepository.findByInvocationIdIn(invocationIds).stream()
                .collect(Collectors.toMap(BillingInvocationEntity::getInvocationId, Function.identity(), (left, right) -> left));
    }

    private VendorBillEventView toView(VendorBillEvent event, BillingInvocationEntity invocation) {
        VendorBillMatchState state = parseMatchState(event.getMatchState());
        return new VendorBillEventView(
                event.getId(),
                event.getTenantId(),
                event.getProviderName(),
                event.getExternalBillId(),
                event.getProviderRequestId(),
                event.getAmountMinor(),
                event.getVendorTokens(),
                event.getCurrency(),
                event.getStatus(),
                event.getMatchState(),
                state == null ? null : state.getDescription(),
                event.getMatchedInvocationId(),
                invocation == null ? null : invocation.getState(),
                invocation == null ? null : invocation.getActualAmount(),
                invocation == null ? null : invocation.getActualTokens(),
                VendorBillEvidenceDigest.digest(event),
                event.getMatchTime(),
                event.getCreatedAt());
    }

    private String normalizeMatchState(String matchState) {
        if (!StringUtils.hasText(matchState)) {
            return null;
        }
        VendorBillMatchState state = parseMatchState(matchState.trim().toUpperCase(Locale.ROOT));
        if (state == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "匹配状态取值非法");
        }
        return state.name();
    }

    private VendorBillMatchState parseMatchState(String matchState) {
        if (!StringUtils.hasText(matchState)) {
            return null;
        }
        try {
            return VendorBillMatchState.valueOf(matchState.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            log.warn("供应商账单匹配状态无法识别: {}", matchState);
            return null;
        }
    }
}
