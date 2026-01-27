package com.lingsql.dashboard.service;

import com.lingsql.audit.repository.SqlExecuteLogRepository;
import com.lingsql.dashboard.model.AccuracyTrendPoint;
import com.lingsql.dashboard.model.ProfitDashboardView;
import com.lingsql.dashboard.model.TokenCostStat;
import com.lingsql.dashboard.model.TopQuestionStat;
import com.lingsql.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 老板仪表盘服务。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@Service
@RequiredArgsConstructor
public class ProfitDashboardService {

    private static final Integer NOT_DELETED = 0;
    private static final Integer ACTIVE_STATUS = 1;

    private final TenantRepository tenantRepository;
    private final SqlExecuteLogRepository sqlExecuteLogRepository;

    /**
     * 构建仪表盘数据。
     *
     * @param days 统计天数
     * @return 仪表盘视图
     * @author 余炘洋
     * @date 2026-01-27
     */
    public ProfitDashboardView buildDashboard(int days) {
        int safeDays = Math.max(days, 1);
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(safeDays);
        ProfitDashboardView view = new ProfitDashboardView();
        view.setActiveTenantCount(tenantRepository.countByIsDeletedAndStatus(NOT_DELETED, ACTIVE_STATUS));
        view.setTopQuestions(loadTopQuestions(start, end));
        view.setAccuracyTrend(loadAccuracyTrend(start, end));
        view.setTokenCostStats(loadTokenCostStats(start, end));
        return view;
    }

    /**
     * 构建 Top 问题统计。
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 统计列表
     * @author 余炘洋
     * @date 2026-01-27
     */
    private List<TopQuestionStat> loadTopQuestions(LocalDateTime start, LocalDateTime end) {
        return sqlExecuteLogRepository.findTopQuestions(start, end, 10).stream()
                .map(item -> {
                    TopQuestionStat stat = new TopQuestionStat();
                    stat.setQuestion(item.getQuestion());
                    stat.setTotalTokens(resolveLong(item.getTotalTokens()));
                    stat.setTotalCost(resolveLong(item.getTotalCost()));
                    stat.setTotalCount(resolveLong(item.getTotalCount()));
                    return stat;
                })
                .toList();
    }

    /**
     * 构建准确率趋势。
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 统计列表
     * @author 余炘洋
     * @date 2026-01-27
     */
    private List<AccuracyTrendPoint> loadAccuracyTrend(LocalDateTime start, LocalDateTime end) {
        return sqlExecuteLogRepository.findAccuracyTrend(start, end).stream()
                .map(item -> {
                    AccuracyTrendPoint point = new AccuracyTrendPoint();
                    LocalDate date = item.getStatDate() == null ? null : item.getStatDate().toLocalDate();
                    long total = resolveLong(item.getTotalCount());
                    long positive = resolveLong(item.getPositiveCount());
                    point.setDate(date);
                    point.setTotalCount(total);
                    point.setPositiveCount(positive);
                    point.setAccuracy(total == 0 ? 0d : (positive * 100d / total));
                    return point;
                })
                .toList();
    }

    /**
     * 构建模型成本对比。
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 统计列表
     * @author 余炘洋
     * @date 2026-01-27
     */
    private List<TokenCostStat> loadTokenCostStats(LocalDateTime start, LocalDateTime end) {
        return sqlExecuteLogRepository.findModelCostStats(start, end).stream()
                .map(item -> {
                    TokenCostStat stat = new TokenCostStat();
                    stat.setModelName(item.getModelName());
                    stat.setTotalTokens(resolveLong(item.getTotalTokens()));
                    stat.setTotalCost(resolveLong(item.getTotalCost()));
                    stat.setTotalCount(resolveLong(item.getTotalCount()));
                    return stat;
                })
                .toList();
    }

    /**
     * 安全解析 Long。
     *
     * @param value 输入
     * @return 输出
     * @author 余炘洋
     * @date 2026-01-27
     */
    private long resolveLong(Long value) {
        return value == null ? 0L : value;
    }
}
