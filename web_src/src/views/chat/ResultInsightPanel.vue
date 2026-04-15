<script setup lang="ts">
import {computed} from 'vue';
import {ElMessage} from 'element-plus';
import {
  analyzeResultRows,
  buildCsvContent,
  formatCompactNumber
} from '@/utils/chat-result-insight';

const props = defineProps<{
  rows: Array<Record<string, unknown>>;
}>();

const insight = computed(() => analyzeResultRows(props.rows));
const chart = computed(() => insight.value?.chart);

const barPoints = computed(() => {
  if (!chart.value || chart.value.type !== 'bar') {
    return [];
  }
  const maxValue = Math.max(...chart.value.points.map((point) => point.value), 0);
  return chart.value.points.map((point) => ({
    ...point,
    ratio: maxValue > 0 ? Math.max((point.value / maxValue) * 100, point.value > 0 ? 8 : 0) : 0
  }));
});

const lineChart = computed(() => {
  if (!chart.value || chart.value.type !== 'line') {
    return null;
  }
  const width = 640;
  const height = 220;
  const paddingLeft = 24;
  const paddingRight = 16;
  const paddingTop = 16;
  const paddingBottom = 34;
  const values = chart.value.points.map((point) => point.value);
  const maxValue = Math.max(...values);
  const minValue = Math.min(...values);
  const span = maxValue === minValue ? 1 : maxValue - minValue;
  const usableWidth = width - paddingLeft - paddingRight;
  const usableHeight = height - paddingTop - paddingBottom;
  const stepX = chart.value.points.length > 1 ? usableWidth / (chart.value.points.length - 1) : usableWidth;
  const points = chart.value.points.map((point, index) => {
    const x = paddingLeft + index * stepX;
    const y = paddingTop + (1 - (point.value - minValue) / span) * usableHeight;
    return {
      ...point,
      x,
      y
    };
  });
  const path = points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' ');
  const guideLines = [maxValue, maxValue === minValue ? maxValue : (maxValue + minValue) / 2, minValue].map((value) => ({
    value,
    y: paddingTop + (1 - (value - minValue) / span) * usableHeight
  }));
  return {
    width,
    height,
    points,
    path,
    guideLines
  };
});

const truncateLabel = (label: string, limit = 10) => {
  return label.length > limit ? `${label.slice(0, limit)}...` : label;
};

const formatChartValue = (value: number) => {
  return formatCompactNumber(value);
};

const exportCsv = () => {
  if (!props.rows.length) {
    ElMessage.warning('当前结果暂无可导出数据');
    return;
  }
  const csv = buildCsvContent(props.rows);
  if (!csv) {
    ElMessage.warning('当前结果暂无可导出数据');
    return;
  }
  const blob = new Blob([`\uFEFF${csv}`], {type: 'text/csv;charset=utf-8;'});
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = `chat-result-${Date.now()}.csv`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(objectUrl);
  ElMessage.success('CSV 已开始下载');
};
</script>

<template>
  <div v-if="insight" class="insight-section">
    <div class="insight-header">
      <div>
        <div class="insight-title">结果概览</div>
        <div class="insight-subtitle">已自动提取主指标、主维度和图表建议</div>
      </div>
      <div class="insight-actions">
        <span class="recommend-chip">{{ insight.recommendation }}</span>
        <button type="button" class="export-button" @click="exportCsv">导出 CSV</button>
      </div>
    </div>

    <div class="summary-grid">
      <div v-for="card in insight.summaryCards" :key="card.label" class="summary-card" :class="card.tone">
        <div class="summary-label">{{ card.label }}</div>
        <div class="summary-value">{{ card.value }}</div>
      </div>
    </div>

    <div v-if="chart" class="chart-card">
      <div class="chart-card-header">
        <div>
          <div class="chart-card-title">{{ chart.title }}</div>
          <div class="chart-card-subtitle">
            已按 {{ chart.dimensionLabel }} 与 {{ chart.metricLabel }} 自动生成{{ chart.type === 'line' ? '趋势图' : '对比图' }}
            <span v-if="chart.truncated">，当前仅展示前 {{ chart.points.length }} 个数据点</span>
          </div>
        </div>
        <span class="chart-type-chip">{{ chart.type === 'line' ? '趋势折线图' : '对比柱状图' }}</span>
      </div>

      <div v-if="chart.type === 'bar'" class="bar-chart">
        <div v-for="point in barPoints" :key="point.label" class="bar-item">
          <div class="bar-value">{{ formatChartValue(point.value) }}</div>
          <div class="bar-track">
            <div class="bar-fill" :style="{height: `${point.ratio}%`}"></div>
          </div>
          <div class="bar-label" :title="point.label">{{ truncateLabel(point.label) }}</div>
        </div>
      </div>

      <div v-else-if="lineChart" class="line-chart-wrapper">
        <svg class="line-chart" :viewBox="`0 0 ${lineChart.width} ${lineChart.height}`" preserveAspectRatio="none" aria-hidden="true">
          <line
            v-for="guide in lineChart.guideLines"
            :key="guide.y"
            x1="24"
            :y1="guide.y"
            x2="624"
            :y2="guide.y"
            class="line-guide"
          />
          <path :d="lineChart.path" class="line-path"/>
          <circle
            v-for="point in lineChart.points"
            :key="point.label"
            :cx="point.x"
            :cy="point.y"
            r="4"
            class="line-point"
          />
        </svg>
        <div class="line-labels">
          <div v-for="point in lineChart.points" :key="point.label" class="line-label-item">
            <div class="line-label-text" :title="point.label">{{ truncateLabel(point.label, 8) }}</div>
            <div class="line-label-value">{{ formatChartValue(point.value) }}</div>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="chart-empty">
      <div class="chart-empty-title">暂不推荐图表</div>
      <div class="chart-empty-text">当前结果更适合直接查看表格，已保留 CSV 导出能力。</div>
    </div>
  </div>
</template>

<style scoped>
.insight-section {
  margin-bottom: 14px;
  padding: 14px;
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.98) 0%, rgba(255, 255, 255, 0.98) 100%);
  border: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.75);
}

.insight-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.insight-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.insight-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
}

.insight-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.recommend-chip,
.chart-type-chip {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: #e0f2fe;
  color: #0369a1;
  font-size: 12px;
  font-weight: 600;
}

.chart-type-chip {
  background: #dbeafe;
  color: #1d4ed8;
}

.export-button {
  appearance: none;
  border: none;
  height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  background: #0f172a;
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.export-button:hover {
  background: #1e293b;
}

.summary-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(132px, 1fr));
  gap: 10px;
}

.summary-card {
  padding: 12px;
  border-radius: 12px;
  border: 1px solid rgba(226, 232, 240, 0.85);
  background: #ffffff;
}

.summary-card.primary {
  background: linear-gradient(180deg, rgba(219, 234, 254, 0.72) 0%, #ffffff 100%);
}

.summary-card.success {
  background: linear-gradient(180deg, rgba(220, 252, 231, 0.72) 0%, #ffffff 100%);
}

.summary-card.warning {
  background: linear-gradient(180deg, rgba(254, 240, 138, 0.45) 0%, #ffffff 100%);
}

.summary-card.info {
  background: linear-gradient(180deg, rgba(226, 232, 240, 0.72) 0%, #ffffff 100%);
}

.summary-label {
  font-size: 12px;
  color: #64748b;
}

.summary-value {
  margin-top: 6px;
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  word-break: break-word;
}

.chart-card,
.chart-empty {
  margin-top: 14px;
  padding: 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(191, 219, 254, 0.85);
}

.chart-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.chart-card-title,
.chart-empty-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.chart-card-subtitle,
.chart-empty-text {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
}

.bar-chart {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(68px, 1fr));
  gap: 10px;
  align-items: end;
}

.bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.bar-value {
  font-size: 12px;
  font-weight: 600;
  color: #0f172a;
}

.bar-track {
  width: 100%;
  height: 160px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 8px;
  border-radius: 12px;
  background: linear-gradient(180deg, rgba(219, 234, 254, 0.42) 0%, rgba(248, 250, 252, 0.92) 100%);
}

.bar-fill {
  width: 100%;
  max-width: 42px;
  min-height: 12px;
  border-radius: 10px 10px 4px 4px;
  background: linear-gradient(180deg, #38bdf8 0%, #2563eb 100%);
  box-shadow: 0 10px 18px rgba(37, 99, 235, 0.22);
}

.bar-label {
  width: 100%;
  font-size: 12px;
  line-height: 1.5;
  color: #334155;
  text-align: center;
  word-break: break-word;
}

.line-chart-wrapper {
  margin-top: 14px;
}

.line-chart {
  width: 100%;
  height: 220px;
  display: block;
}

.line-guide {
  stroke: rgba(148, 163, 184, 0.35);
  stroke-dasharray: 4 4;
  stroke-width: 1;
}

.line-path {
  fill: none;
  stroke: #0ea5e9;
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.line-point {
  fill: #ffffff;
  stroke: #2563eb;
  stroke-width: 2;
}

.line-labels {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(80px, 1fr));
  gap: 8px;
}

.line-label-item {
  padding: 10px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid rgba(226, 232, 240, 0.9);
}

.line-label-text {
  font-size: 12px;
  color: #334155;
}

.line-label-value {
  margin-top: 6px;
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
}

@media (max-width: 640px) {
  .insight-header,
  .chart-card-header {
    flex-direction: column;
  }

  .insight-actions {
    width: 100%;
  }

  .export-button {
    width: 100%;
  }
}
</style>
