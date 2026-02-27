package com.lingsql.audit.service;

import com.lingsql.audit.model.TokenAuditOverviewView;
import com.lingsql.audit.model.TokenAuditQuery;
import com.lingsql.audit.repository.SqlExecuteLogRepository;
import com.lingsql.common.pagination.PageParamResolver;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlExecuteLogServiceTokenOverviewTest {

    @Test
    void shouldBuildTokenOverviewWithTenantStats() {
        SqlExecuteLogRepository repository = Mockito.mock(SqlExecuteLogRepository.class);
        PageParamResolver pageParamResolver = Mockito.mock(PageParamResolver.class);
        SqlExecuteLogService service = new SqlExecuteLogService(repository, pageParamResolver);

        TokenAuditQuery query = new TokenAuditQuery();
        query.setTenantId("TENANT_A");
        query.setRequestSource("WEB");
        query.setStartTime(LocalDateTime.of(2026, 2, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 2, 28, 23, 59, 59));
        query.setTopN(10);

        when(repository.findTokenAuditSummary(any(), any(), any(), any(), any(), any()))
                .thenReturn(summaryProjection(100L, 90L, 10L, 20000L, 5000L, 200d, 50d));
        when(repository.findTokenAuditModelStats(any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(dimensionProjection("gpt-4", 60L, 54L, 6L, 12000L, 4200L)));
        when(repository.findTokenAuditTenantStats(any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(dimensionProjection("TENANT_A", 100L, 90L, 10L, 20000L, 5000L)));
        when(repository.findTokenAuditTrend(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(trendProjection(LocalDate.of(2026, 2, 20), 30L, 27L, 3L, 6000L, 1500L)));

        TokenAuditOverviewView view = service.tokenOverview(query, true);

        assertNotNull(view);
        assertEquals(100L, view.getTotalCount());
        assertEquals(90L, view.getSuccessCount());
        assertEquals(10L, view.getFailureCount());
        assertEquals(90d, view.getSuccessRate());
        assertEquals(20000L, view.getTotalTokens());
        assertEquals(5000L, view.getTotalCost());
        assertEquals(200d, view.getAvgTokensPerRequest());
        assertEquals(50d, view.getAvgCostPerRequest());
        assertEquals(1, view.getModelStats().size());
        assertEquals("gpt-4", view.getModelStats().get(0).getDimensionKey());
        assertEquals(1, view.getTenantStats().size());
        assertEquals("TENANT_A", view.getTenantStats().get(0).getDimensionKey());
        assertEquals(1, view.getTrend().size());
        assertEquals(LocalDate.of(2026, 2, 20), view.getTrend().get(0).getStatDate());
        assertEquals(90d, view.getTrend().get(0).getSuccessRate());
    }

    @Test
    void shouldClampTopNAndSkipTenantStatsWhenDisabled() {
        SqlExecuteLogRepository repository = Mockito.mock(SqlExecuteLogRepository.class);
        PageParamResolver pageParamResolver = Mockito.mock(PageParamResolver.class);
        SqlExecuteLogService service = new SqlExecuteLogService(repository, pageParamResolver);

        TokenAuditQuery query = new TokenAuditQuery();
        query.setTopN(999);

        when(repository.findTokenAuditSummary(any(), any(), any(), any(), any(), any()))
                .thenReturn(summaryProjection(0L, 0L, 0L, 0L, 0L, 0d, 0d));
        when(repository.findTokenAuditModelStats(any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());
        when(repository.findTokenAuditTrend(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        TokenAuditOverviewView view = service.tokenOverview(query, false);

        assertNotNull(view);
        assertTrue(view.getStartTime() != null && view.getEndTime() != null);
        assertTrue(view.getTenantStats().isEmpty());
        verify(repository).findTokenAuditModelStats(any(), any(), any(), any(), any(), any(), eq(50));
        verify(repository, never()).findTokenAuditTenantStats(any(), any(), any(), any(), any(), any(), anyInt());
    }

    private SqlExecuteLogRepository.TokenAuditSummaryProjection summaryProjection(
            long totalCount,
            long successCount,
            long failureCount,
            long totalTokens,
            long totalCost,
            double avgTokensPerRequest,
            double avgCostPerRequest
    ) {
        return new SqlExecuteLogRepository.TokenAuditSummaryProjection() {
            @Override
            public Long getTotalCount() {
                return totalCount;
            }

            @Override
            public Long getSuccessCount() {
                return successCount;
            }

            @Override
            public Long getFailureCount() {
                return failureCount;
            }

            @Override
            public Long getTotalTokens() {
                return totalTokens;
            }

            @Override
            public Long getTotalCost() {
                return totalCost;
            }

            @Override
            public Double getAvgTokensPerRequest() {
                return avgTokensPerRequest;
            }

            @Override
            public Double getAvgCostPerRequest() {
                return avgCostPerRequest;
            }
        };
    }

    private SqlExecuteLogRepository.TokenAuditDimensionProjection dimensionProjection(
            String dimensionKey,
            long totalCount,
            long successCount,
            long failureCount,
            long totalTokens,
            long totalCost
    ) {
        return new SqlExecuteLogRepository.TokenAuditDimensionProjection() {
            @Override
            public String getDimensionKey() {
                return dimensionKey;
            }

            @Override
            public Long getTotalCount() {
                return totalCount;
            }

            @Override
            public Long getSuccessCount() {
                return successCount;
            }

            @Override
            public Long getFailureCount() {
                return failureCount;
            }

            @Override
            public Long getTotalTokens() {
                return totalTokens;
            }

            @Override
            public Long getTotalCost() {
                return totalCost;
            }
        };
    }

    private SqlExecuteLogRepository.TokenAuditTrendProjection trendProjection(
            LocalDate date,
            long totalCount,
            long successCount,
            long failureCount,
            long totalTokens,
            long totalCost
    ) {
        return new SqlExecuteLogRepository.TokenAuditTrendProjection() {
            @Override
            public java.sql.Date getStatDate() {
                return java.sql.Date.valueOf(date);
            }

            @Override
            public Long getTotalCount() {
                return totalCount;
            }

            @Override
            public Long getSuccessCount() {
                return successCount;
            }

            @Override
            public Long getFailureCount() {
                return failureCount;
            }

            @Override
            public Long getTotalTokens() {
                return totalTokens;
            }

            @Override
            public Long getTotalCost() {
                return totalCost;
            }
        };
    }
}
