import {request} from '@/utils/request';

/**
 * Top 问题统计。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
export interface TopQuestionStat {
  question: string;
  totalTokens: number;
  totalCost: number;
  totalCount: number;
}

/**
 * 准确率趋势点。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
export interface AccuracyTrendPoint {
  date: string;
  accuracy: number;
  totalCount: number;
  positiveCount: number;
}

/**
 * Token 成本对比统计。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
export interface TokenCostStat {
  modelName: string;
  totalTokens: number;
  totalCost: number;
  totalCount: number;
}

/**
 * 仪表盘视图。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
export interface ProfitDashboardView {
  activeTenantCount: number;
  topQuestions: TopQuestionStat[];
  accuracyTrend: AccuracyTrendPoint[];
  tokenCostStats: TokenCostStat[];
}

/**
 * 查询老板仪表盘数据。
 *
 * @param days 统计天数
 * @return 仪表盘数据
 * @author 余炘洋
 * @date 2026-01-27
 */
export const fetchProfitDashboard = async (days = 30): Promise<ProfitDashboardView> => {
  return request<ProfitDashboardView>(`/api/v1/admin/dashboard/overview?days=${days}`);
};
