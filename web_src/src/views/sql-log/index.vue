<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue';
import {ElMessage} from 'element-plus';
import {useAuthStore} from '@/store/auth';
import {
  PageResult,
  SqlExecuteLogView,
  TokenAuditOverviewView,
  exportSqlLogsExcel,
  fetchSqlLogs,
  fetchTokenAuditOverview
} from '@/api/audit';
import {useRoute} from 'vue-router';

const TOKEN_TOP_N = 10;

const authStore = useAuthStore();
const route = useRoute();
const logs = ref<SqlExecuteLogView[]>([]);
const loading = ref(false);
const overviewLoading = ref(false);
const exporting = ref(false);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const tokenOverview = ref<TokenAuditOverviewView | null>(null);

const searchForm = reactive({
  userId: '',
  question: '',
  requestSource: '',
  dateRange: [] as [Date, Date] | []
});

/**
 * 判断是否系统管理员。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const isSysAdmin = computed(() => {
  return (authStore.roles || []).some((role) => ['SYS_ADMIN', 'SUPER_ADMIN'].includes(role));
});

/**
 * 当前选中租户ID。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const activeTenantId = computed(() => {
  return isSysAdmin.value ? authStore.activeTenantId : authStore.tenantId;
});

/**
 * 当前租户范围展示文本。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const tenantScopeLabel = computed(() => {
  if (isSysAdmin.value) {
    if (!activeTenantId.value) {
      return '全部';
    }
    return authStore.activeTenantName || activeTenantId.value;
  }
  return authStore.tenantName || authStore.tenantId || '当前租户';
});

const modelStats = computed(() => tokenOverview.value?.modelStats || []);
const tenantStats = computed(() => tokenOverview.value?.tenantStats || []);
const trendStats = computed(() => tokenOverview.value?.trend || []);

/**
 * 格式化日期。
 *
 * @param value 日期
 * @return 格式化字符串
 * @author 余炘洋
 * @date 2026-01-26
 */
const formatDateTime = (value: Date): string => {
  const pad = (num: number) => String(num).padStart(2, '0');
  const year = value.getFullYear();
  const month = pad(value.getMonth() + 1);
  const day = pad(value.getDate());
  const hour = pad(value.getHours());
  const minute = pad(value.getMinutes());
  const second = pad(value.getSeconds());
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`;
};

/**
 * 获取查询时间范围。
 *
 * @return 起止时间
 * @author 余炘洋
 * @date 2026-01-26
 */
const resolveTimeRange = () => {
  if (searchForm.dateRange && searchForm.dateRange.length === 2) {
    const [start, end] = searchForm.dateRange as [Date, Date];
    return {
      startTime: formatDateTime(start),
      endTime: formatDateTime(end)
    };
  }
  return {startTime: undefined, endTime: undefined};
};

/**
 * 构建公共查询参数。
 *
 * @return 查询参数
 * @author 余炘洋
 * @date 2026-02-27
 */
const buildCommonParams = () => {
  const {startTime, endTime} = resolveTimeRange();
  return {
    tenantId: isSysAdmin.value ? activeTenantId.value || undefined : undefined,
    userId: searchForm.userId ? Number(searchForm.userId) : undefined,
    question: searchForm.question || undefined,
    requestSource: searchForm.requestSource || undefined,
    startTime,
    endTime
  };
};

/**
 * 加载 SQL 日志。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const loadLogs = async () => {
  loading.value = true;
  try {
    const params = buildCommonParams();
    const result: PageResult<SqlExecuteLogView> = await fetchSqlLogs({
      ...params,
      page: page.value,
      size: size.value
    });
    logs.value = result.list || [];
    total.value = result.total || 0;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载 SQL 日志失败');
  } finally {
    loading.value = false;
  }
};

/**
 * 加载 Token 审计看板。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
const loadTokenOverview = async () => {
  overviewLoading.value = true;
  try {
    const params = buildCommonParams();
    tokenOverview.value = await fetchTokenAuditOverview({
      ...params,
      topN: TOKEN_TOP_N
    });
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载 Token 审计总览失败');
  } finally {
    overviewLoading.value = false;
  }
};

/**
 * 加载日志和看板数据。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
const loadAllData = async () => {
  await Promise.all([loadLogs(), loadTokenOverview()]);
};

/**
 * 导出 SQL 日志 Excel。
 *
 * @author 余炘洋
 * @date 2026-04-07
 */
const handleExport = async () => {
  if (!isSysAdmin.value) {
    ElMessage.warning('仅超级管理员可导出');
    return;
  }
  exporting.value = true;
  try {
    const {startTime, endTime} = resolveTimeRange();
    const file = await exportSqlLogsExcel({
      tenantId: activeTenantId.value || undefined,
      userId: searchForm.userId ? Number(searchForm.userId) : undefined,
      question: searchForm.question || undefined,
      requestSource: searchForm.requestSource || undefined,
      startTime,
      endTime
    });
    const url = URL.createObjectURL(file.blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = file.filename || `sql-log-export-${Date.now()}.xlsx`;
    link.click();
    URL.revokeObjectURL(url);
    ElMessage.success('导出完成');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  } finally {
    exporting.value = false;
  }
};

/**
 * 处理分页大小变化。
 *
 * @param val 新大小
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleSizeChange = (val: number) => {
  size.value = val;
  void loadLogs();
};

/**
 * 处理页码变化。
 *
 * @param val 新页码
 * @author 余炘洋
 * @date 2026-01-26
 */
const handlePageChange = (val: number) => {
  page.value = val;
  void loadLogs();
};

/**
 * 执行查询。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleSearch = () => {
  page.value = 1;
  void loadAllData();
};

/**
 * 同步 URL 查询条件。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
const applyRouteQuestion = () => {
  const question = route.query.question;
  if (typeof question === 'string' && question.trim()) {
    searchForm.question = question.trim();
  }
};

watch(activeTenantId, () => {
  if (isSysAdmin.value) {
    handleSearch();
  }
});

watch(
  () => route.query.question,
  () => {
    applyRouteQuestion();
    handleSearch();
  }
);

/**
 * 格式化数字展示。
 *
 * @param value 数值
 * @return 格式化结果
 * @author 余炘洋
 * @date 2026-02-27
 */
const formatNumber = (value?: number | null): string => {
  if (value == null || Number.isNaN(value)) {
    return '0';
  }
  return Number(value).toLocaleString('zh-CN', {maximumFractionDigits: 2});
};

/**
 * 格式化百分比。
 *
 * @param value 数值
 * @return 百分比
 * @author 余炘洋
 * @date 2026-02-27
 */
const formatPercent = (value?: number | null): string => {
  if (value == null || Number.isNaN(value)) {
    return '0.00%';
  }
  return `${Number(value).toFixed(2)}%`;
};

applyRouteQuestion();
handleSearch();
</script>

<template>
  <div class="page">
    <div class="page-title">SQL 日志</div>
    <div class="page-desc">检索 SQL 执行日志并支持 Excel 导出。</div>

    <el-card class="card" shadow="never">
      <div class="toolbar">
        <div class="filters">
          <div class="tenant-scope">
            当前范围：
            <el-tag size="small" :type="activeTenantId ? 'success' : 'info'">{{ tenantScopeLabel }}</el-tag>
          </div>
          <el-input v-model="searchForm.userId" placeholder="用户ID" style="width: 120px"/>
          <el-input v-model="searchForm.question" placeholder="问题关键词" style="width: 200px"/>
          <el-select v-model="searchForm.requestSource" placeholder="来源" style="width: 140px" clearable>
            <el-option label="WEB" value="WEB"/>
            <el-option label="API" value="API"/>
            <el-option label="UNKNOWN" value="UNKNOWN"/>
          </el-select>
          <el-date-picker
            v-model="searchForm.dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="loadAllData">刷新</el-button>
        </div>
        <el-button v-if="isSysAdmin" :loading="exporting" @click="handleExport">导出 Excel</el-button>
      </div>

      <div class="overview-wrap" v-loading="overviewLoading">
        <div class="overview-grid">
          <div class="overview-item">
            <div class="overview-label">请求总数</div>
            <div class="overview-value">{{ formatNumber(tokenOverview?.totalCount) }}</div>
          </div>
          <div class="overview-item">
            <div class="overview-label">成功率</div>
            <div class="overview-value">{{ formatPercent(tokenOverview?.successRate) }}</div>
          </div>
          <div class="overview-item">
            <div class="overview-label">Token 总量</div>
            <div class="overview-value">{{ formatNumber(tokenOverview?.totalTokens) }}</div>
          </div>
          <div class="overview-item">
            <div class="overview-label">成本总额</div>
            <div class="overview-value">{{ formatNumber(tokenOverview?.totalCost) }}</div>
          </div>
          <div class="overview-item">
            <div class="overview-label">平均 Token/请求</div>
            <div class="overview-value">{{ formatNumber(tokenOverview?.avgTokensPerRequest) }}</div>
          </div>
          <div class="overview-item">
            <div class="overview-label">平均成本/请求</div>
            <div class="overview-value">{{ formatNumber(tokenOverview?.avgCostPerRequest) }}</div>
          </div>
        </div>

        <div class="overview-panel">
          <div class="panel-title">模型 Token 消耗 Top{{ TOKEN_TOP_N }}</div>
          <el-table :data="modelStats" border size="small">
            <el-table-column prop="dimensionKey" label="模型" min-width="140"/>
            <el-table-column prop="totalTokens" label="Token总量" min-width="120">
              <template #default="scope">{{ formatNumber(scope.row.totalTokens) }}</template>
            </el-table-column>
            <el-table-column prop="totalCost" label="成本总额" min-width="120">
              <template #default="scope">{{ formatNumber(scope.row.totalCost) }}</template>
            </el-table-column>
            <el-table-column prop="totalCount" label="请求数" min-width="90">
              <template #default="scope">{{ formatNumber(scope.row.totalCount) }}</template>
            </el-table-column>
            <el-table-column prop="successRate" label="成功率" min-width="90">
              <template #default="scope">{{ formatPercent(scope.row.successRate) }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div class="overview-panel">
          <div class="panel-title">Token 日趋势</div>
          <el-table :data="trendStats" border size="small">
            <el-table-column prop="statDate" label="日期" min-width="120"/>
            <el-table-column prop="totalTokens" label="Token总量" min-width="120">
              <template #default="scope">{{ formatNumber(scope.row.totalTokens) }}</template>
            </el-table-column>
            <el-table-column prop="totalCost" label="成本总额" min-width="120">
              <template #default="scope">{{ formatNumber(scope.row.totalCost) }}</template>
            </el-table-column>
            <el-table-column prop="totalCount" label="请求数" min-width="90">
              <template #default="scope">{{ formatNumber(scope.row.totalCount) }}</template>
            </el-table-column>
            <el-table-column prop="successRate" label="成功率" min-width="90">
              <template #default="scope">{{ formatPercent(scope.row.successRate) }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="isSysAdmin" class="overview-panel">
          <div class="panel-title">租户 Token 消耗 Top{{ TOKEN_TOP_N }}</div>
          <el-table :data="tenantStats" border size="small">
            <el-table-column prop="dimensionKey" label="租户" min-width="140"/>
            <el-table-column prop="totalTokens" label="Token总量" min-width="120">
              <template #default="scope">{{ formatNumber(scope.row.totalTokens) }}</template>
            </el-table-column>
            <el-table-column prop="totalCost" label="成本总额" min-width="120">
              <template #default="scope">{{ formatNumber(scope.row.totalCost) }}</template>
            </el-table-column>
            <el-table-column prop="totalCount" label="请求数" min-width="90">
              <template #default="scope">{{ formatNumber(scope.row.totalCount) }}</template>
            </el-table-column>
            <el-table-column prop="successRate" label="成功率" min-width="90">
              <template #default="scope">{{ formatPercent(scope.row.successRate) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <el-table :data="logs" border v-loading="loading">
        <el-table-column prop="createTime" label="时间" min-width="160"/>
        <el-table-column prop="tenantId" label="租户" min-width="120"/>
        <el-table-column prop="userId" label="用户ID" width="100"/>
        <el-table-column prop="requestSource" label="来源" width="100"/>
        <el-table-column prop="success" label="结果" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.success === 1 ? 'success' : 'danger'">
              {{ scope.row.success === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="modelName" label="模型" min-width="140"/>
        <el-table-column prop="tokenCount" label="Token" width="110"/>
        <el-table-column prop="costAmount" label="成本" width="110"/>
        <el-table-column prop="executeCostMs" label="耗时(ms)" width="110"/>
        <el-table-column prop="resultRows" label="行数" width="90"/>
        <el-table-column prop="question" label="问题" min-width="200"/>
        <el-table-column prop="errorMessage" label="错误信息" min-width="200"/>
      </el-table>

      <div class="pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="page"
          :page-size="size"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  padding: 24px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 6px;
}

.page-desc {
  color: #64748b;
  margin-bottom: 16px;
}

.card {
  border-radius: 12px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.filters {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}

.tenant-scope {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #64748b;
}

.overview-wrap {
  margin-bottom: 16px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.overview-item {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px;
  background: #f8fafc;
}

.overview-label {
  font-size: 12px;
  color: #64748b;
}

.overview-value {
  font-size: 20px;
  font-weight: 600;
  margin-top: 6px;
}

.overview-panel {
  margin-bottom: 12px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
