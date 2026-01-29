package com.lingsql.billing;

import com.lingsql.billing.entity.BillingReconcileLog;
import com.lingsql.billing.model.BillingReconcileLogQuery;
import com.lingsql.billing.model.BillingReconcileLogView;
import com.lingsql.billing.repository.BillingReconcileLogRepository;
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

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 计费对账日志服务。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingReconcileLogService {

    private static final int STATUS_OPEN = 0;
    private static final int STATUS_RESOLVED = 1;

    private final BillingReconcileLogRepository repository;
    private final PageParamResolver pageParamResolver;

    /**
     * 记录对账异常。
     *
     * @param tenantId    租户ID
     * @param requestId   请求ID
     * @param reserveTotal 预扣总额
     * @param closeTotal  CLOSE 总额
     * @param diffAmount  差异金额
     * @param latestTime  最新账本时间
     * @author 余炘洋
     * @date 2026-01-29
     */
    public void recordIfNeeded(String tenantId,
                               String requestId,
                               long reserveTotal,
                               long closeTotal,
                               long diffAmount,
                               LocalDateTime latestTime) {
        if (!StringUtils.hasText(requestId) || diffAmount <= 0) {
            return;
        }
        Optional<BillingReconcileLog> last = repository.findTopByRequestIdOrderByCreateTimeDesc(requestId);
        if (last.isPresent()) {
            BillingReconcileLog existing = last.get();
            if (isSame(existing, reserveTotal, closeTotal, diffAmount, latestTime)) {
                return;
            }
        }
        BillingReconcileLog logEntity = new BillingReconcileLog();
        logEntity.setTenantId(StringUtils.hasText(tenantId) ? tenantId : null);
        logEntity.setRequestId(requestId);
        logEntity.setReserveTotal(reserveTotal);
        logEntity.setCloseTotal(closeTotal);
        logEntity.setDiffAmount(diffAmount);
        logEntity.setLatestTime(latestTime);
        logEntity.setStatus(STATUS_OPEN);
        logEntity.setCreateTime(LocalDateTime.now());
        logEntity.setUpdateTime(LocalDateTime.now());
        repository.save(logEntity);
    }

    /**
     * 查询对账日志。
     *
     * @param query 查询条件
     * @param page  页码
     * @param size  每页大小
     * @return 分页结果
     * @author 余炘洋
     * @date 2026-01-29
     */
    public PageResult<BillingReconcileLogView> query(BillingReconcileLogQuery query, int page, int size) {
        BillingReconcileLogQuery safeQuery = query == null ? new BillingReconcileLogQuery() : query;
        PageParam param = pageParamResolver.resolve(page, size);
        Specification<BillingReconcileLog> specification = (root, criteriaQuery, criteriaBuilder) -> {
            var predicate = criteriaBuilder.conjunction();
            if (StringUtils.hasText(safeQuery.getTenantId())) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("tenantId"), safeQuery.getTenantId()));
            }
            if (StringUtils.hasText(safeQuery.getRequestId())) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("requestId"), safeQuery.getRequestId().trim()));
            }
            if (safeQuery.getStatus() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("status"), safeQuery.getStatus()));
            }
            if (safeQuery.getStartTime() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.greaterThanOrEqualTo(root.get("createTime"), safeQuery.getStartTime()));
            }
            if (safeQuery.getEndTime() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.lessThanOrEqualTo(root.get("createTime"), safeQuery.getEndTime()));
            }
            return predicate;
        };
        PageRequest pageable = PageRequest.of(Math.max(param.page() - 1, 0), param.size(), Sort.by(Sort.Direction.DESC, "createTime"));
        Page<BillingReconcileLog> result = repository.findAll(specification, pageable);
        return new PageResult<>(
                result.getContent().stream().map(this::toView).toList(),
                result.getTotalElements(),
                param.page(),
                param.size()
        );
    }

    /**
     * 标记对账日志为已处理。
     *
     * @param id           日志ID
     * @param resolveUserId 处理人ID
     * @param resolveNote  处理备注
     * @return 处理后的日志
     * @author 余炘洋
     * @date 2026-01-29
     */
    @org.springframework.transaction.annotation.Transactional
    public BillingReconcileLogView resolve(Long id, Long resolveUserId, String resolveNote) {
        if (id == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "对账日志ID不能为空");
        }
        String note = StringUtils.hasText(resolveNote) ? resolveNote.trim() : null;
        LocalDateTime now = LocalDateTime.now();
        int updated = repository.resolveById(id, STATUS_RESOLVED, STATUS_OPEN, note, resolveUserId, now, now);
        BillingReconcileLog logEntity = repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "对账日志不存在"));
        if (updated <= 0 && logEntity.getStatus() != null && logEntity.getStatus() != STATUS_RESOLVED) {
            throw new BizException(ErrorCode.CONFLICT, "对账日志状态已变更");
        }
        return toView(logEntity);
    }

    private boolean isSame(BillingReconcileLog existing,
                           long reserveTotal,
                           long closeTotal,
                           long diffAmount,
                           LocalDateTime latestTime) {
        if (existing == null) {
            return false;
        }
        if (!equalsLong(existing.getReserveTotal(), reserveTotal)) {
            return false;
        }
        if (!equalsLong(existing.getCloseTotal(), closeTotal)) {
            return false;
        }
        if (!equalsLong(existing.getDiffAmount(), diffAmount)) {
            return false;
        }
        if (existing.getLatestTime() == null || latestTime == null) {
            return false;
        }
        return !latestTime.isAfter(existing.getLatestTime());
    }

    private boolean equalsLong(Long value, long expected) {
        if (value == null) {
            return expected == 0L;
        }
        return value == expected;
    }

    private BillingReconcileLogView toView(BillingReconcileLog entity) {
        BillingReconcileLogView view = new BillingReconcileLogView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setRequestId(entity.getRequestId());
        view.setReserveTotal(entity.getReserveTotal());
        view.setCloseTotal(entity.getCloseTotal());
        view.setDiffAmount(entity.getDiffAmount());
        view.setStatus(entity.getStatus());
        view.setResolveNote(entity.getResolveNote());
        view.setResolveUserId(entity.getResolveUserId());
        view.setResolveTime(entity.getResolveTime());
        view.setLatestTime(entity.getLatestTime());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }
}
