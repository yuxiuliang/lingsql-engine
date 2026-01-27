<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import {ElMessage} from 'element-plus';
import {fetchProfitDashboard, ProfitDashboardView} from '@/api/dashboard';
import {useRouter} from 'vue-router';

const days = ref(30);
const loading = ref(false);
const dashboard = ref<ProfitDashboardView | null>(null);
const router = useRouter();

/**
 * 加载仪表盘数据。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
const loadDashboard = async () => {
  loading.value = true;
  try {
    dashboard.value = await fetchProfitDashboard(days.value || 30);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载仪表盘失败');
  } finally {
    loading.value = false;
  }
};

const topQuestions = computed(() => dashboard.value?.topQuestions || []);
const accuracyTrend = computed(() => dashboard.value?.accuracyTrend || []);
const tokenCostStats = computed(() => dashboard.value?.tokenCostStats || []);

const totalQuestionCount = computed(() => {
  return topQuestions.value.reduce((sum, item) => sum + (item.totalCount || 0), 0);
});

const totalTokenCost = computed(() => {
  return tokenCostStats.value.reduce((sum, item) => sum + (item.totalCost || 0), 0);
});

/**
 * 跳转到 SQL 日志详情。
 *
 * @param question 问题内容
 * @author 余炘洋
 * @date 2026-01-27
 */
const handleTopQuestionClick = (question?: string) => {
  if (!question || !question.trim()) {
    ElMessage.warning('问题内容为空，无法跳转');
    return;
  }
  router.push({path: '/sql-log', query: {question}});
};

onMounted(loadDashboard);
</script>

<template>
  <div class="page">
    <div class="page-title">仪表盘</div>
    <div class="page-desc">概览租户活跃度、模型成本与问答质量趋势。</div>

    <el-card class="card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-left">
          <span>统计周期（天）</span>
          <el-input-number v-model="days" :min="1" :max="365" :step="1"/>
        </div>
        <el-button type="primary" :loading="loading" @click="loadDashboard">刷新</el-button>
      </div>

      <div class="summary-grid">
        <div class="summary-card">
          <div class="summary-label">活跃租户</div>
          <div class="summary-value">{{ dashboard?.activeTenantCount ?? 0 }}</div>
        </div>
        <div class="summary-card">
          <div class="summary-label">Top问答次数</div>
          <div class="summary-value">{{ totalQuestionCount }}</div>
        </div>
        <div class="summary-card">
          <div class="summary-label">模型成本汇总</div>
          <div class="summary-value">{{ totalTokenCost }}</div>
        </div>
        <div class="summary-card">
          <div class="summary-label">趋势样本数</div>
          <div class="summary-value">{{ accuracyTrend.length }}</div>
        </div>
      </div>
    </el-card>

    <el-card class="card" shadow="never">
      <div class="section-title">准确率趋势</div>
      <el-table :data="accuracyTrend" border v-loading="loading">
        <el-table-column prop="date" label="日期" min-width="140"/>
        <el-table-column prop="accuracy" label="准确率(%)" min-width="120"/>
        <el-table-column prop="positiveCount" label="好评数" min-width="120"/>
        <el-table-column prop="totalCount" label="评价总数" min-width="120"/>
      </el-table>
    </el-card>

    <el-card class="card" shadow="never">
      <div class="section-title">Top 问题消耗</div>
      <el-table :data="topQuestions" border v-loading="loading">
        <el-table-column prop="question" label="问题" min-width="240"/>
        <el-table-column prop="totalTokens" label="Token总量" min-width="120"/>
        <el-table-column prop="totalCost" label="成本" min-width="120"/>
        <el-table-column prop="totalCount" label="调用次数" min-width="120"/>
        <el-table-column label="穿透" width="120">
          <template #default="scope">
            <el-button size="small" @click="handleTopQuestionClick(scope.row.question)">查看日志</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="card" shadow="never">
      <div class="section-title">模型成本分布</div>
      <el-table :data="tokenCostStats" border v-loading="loading">
        <el-table-column prop="modelName" label="模型" min-width="160"/>
        <el-table-column prop="totalTokens" label="Token总量" min-width="120"/>
        <el-table-column prop="totalCost" label="成本" min-width="120"/>
        <el-table-column prop="totalCount" label="调用次数" min-width="120"/>
      </el-table>
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
  margin-bottom: 16px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
}

.summary-card {
  background: #f8fafc;
  border-radius: 12px;
  padding: 16px;
}

.summary-label {
  color: #64748b;
  font-size: 13px;
}

.summary-value {
  font-size: 22px;
  font-weight: 600;
  margin-top: 6px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
}
</style>
