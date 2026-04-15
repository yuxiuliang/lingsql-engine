export interface ResultSummaryCard {
  label: string;
  value: string;
  tone: 'primary' | 'success' | 'warning' | 'info';
}

export interface ResultChartPoint {
  label: string;
  value: number;
}

export interface ResultChartModel {
  type: 'bar' | 'line';
  title: string;
  dimensionLabel: string;
  metricLabel: string;
  points: ResultChartPoint[];
  truncated: boolean;
}

export interface ResultInsight {
  recommendation: string;
  summaryCards: ResultSummaryCard[];
  chart?: ResultChartModel;
}

const compactNumberFormatter = new Intl.NumberFormat('zh-CN', {
  maximumFractionDigits: 1
});

export const analyzeResultRows = (rows?: Array<Record<string, unknown>>): ResultInsight | null => {
  if (!rows || rows.length === 0) {
    return null;
  }
  const columns = Object.keys(rows[0] || {});
  if (!columns.length) {
    return null;
  }
  const numericColumns = columns.filter((column) => isNumericColumn(rows, column));
  const temporalColumns = columns.filter((column) => isTemporalColumn(rows, column));
  const dimensionColumns = columns.filter((column) => !numericColumns.includes(column));
  const chart = buildChartModel(rows, numericColumns, temporalColumns, dimensionColumns);
  const recommendation = resolveRecommendation(chart);
  return {
    recommendation,
    summaryCards: buildSummaryCards(rows, columns, numericColumns, temporalColumns, dimensionColumns, recommendation),
    chart
  };
};

export const buildCsvContent = (rows: Array<Record<string, unknown>>): string => {
  if (!rows.length) {
    return '';
  }
  const columns = Object.keys(rows[0] || {});
  const header = columns.map((column) => escapeCsvValue(column)).join(',');
  const body = rows.map((row) => {
    return columns.map((column) => escapeCsvValue(row[column])).join(',');
  });
  return [header, ...body].join('\r\n');
};

export const formatCompactNumber = (value: number): string => {
  if (!Number.isFinite(value)) {
    return '--';
  }
  const absoluteValue = Math.abs(value);
  if (absoluteValue >= 100000000) {
    return `${compactNumberFormatter.format(value / 100000000)}亿`;
  }
  if (absoluteValue >= 10000) {
    return `${compactNumberFormatter.format(value / 10000)}万`;
  }
  return compactNumberFormatter.format(value);
};

const buildSummaryCards = (
  rows: Array<Record<string, unknown>>,
  columns: string[],
  numericColumns: string[],
  temporalColumns: string[],
  dimensionColumns: string[],
  recommendation: string
): ResultSummaryCard[] => {
  const cards: ResultSummaryCard[] = [
    {
      label: '结果规模',
      value: `${rows.length} 行`,
      tone: 'primary'
    },
    {
      label: '字段结构',
      value: `${columns.length} 列 / ${numericColumns.length} 个数值字段`,
      tone: 'info'
    }
  ];
  const primaryDimension = temporalColumns[0] || dimensionColumns[0];
  if (primaryDimension) {
    cards.push({
      label: temporalColumns.includes(primaryDimension) ? '主时间轴' : '主维度',
      value: primaryDimension,
      tone: 'success'
    });
  }
  const primaryMetric = numericColumns[0];
  if (primaryMetric) {
    const metricValues = rows
      .map((row) => parseNumericValue(row[primaryMetric]))
      .filter((value): value is number => value !== null);
    if (metricValues.length > 0) {
      cards.push({
        label: `${primaryMetric}合计`,
        value: formatCompactNumber(metricValues.reduce((total, current) => total + current, 0)),
        tone: 'success'
      });
    }
  }
  cards.push({
    label: '图表建议',
    value: recommendation,
    tone: 'warning'
  });
  return cards;
};

const buildChartModel = (
  rows: Array<Record<string, unknown>>,
  numericColumns: string[],
  temporalColumns: string[],
  dimensionColumns: string[]
): ResultChartModel | undefined => {
  if (!numericColumns.length) {
    return undefined;
  }
  if (rows.length === 1) {
    const singleRowPoints = numericColumns
      .slice(0, 6)
      .map((column) => ({
        label: column,
        value: parseNumericValue(rows[0][column])
      }))
      .filter((point): point is ResultChartPoint => point.value !== null);
    if (singleRowPoints.length >= 2 && singleRowPoints.every((point) => point.value >= 0)) {
      return {
        type: 'bar',
        title: '单行指标对比',
        dimensionLabel: '指标',
        metricLabel: '指标值',
        points: singleRowPoints,
        truncated: numericColumns.length > singleRowPoints.length
      };
    }
  }
  const primaryMetric = numericColumns[0];
  const primaryDimension = temporalColumns[0] || dimensionColumns[0];
  if (!primaryMetric || !primaryDimension) {
    return undefined;
  }
  const maxPoints = temporalColumns.includes(primaryDimension) ? 12 : 8;
  const rawPoints = rows
    .map((row) => ({
      label: normalizeDisplayValue(row[primaryDimension]),
      value: parseNumericValue(row[primaryMetric])
    }))
    .filter((point): point is ResultChartPoint => point.value !== null && !!point.label && point.label !== '--');
  if (rawPoints.length < 2) {
    return undefined;
  }
  const chartType: ResultChartModel['type'] = temporalColumns.includes(primaryDimension) ? 'line' : 'bar';
  if (chartType === 'bar' && rawPoints.some((point) => point.value < 0)) {
    return undefined;
  }
  return {
    type: chartType,
    title: `${primaryDimension}与${primaryMetric}`,
    dimensionLabel: primaryDimension,
    metricLabel: primaryMetric,
    points: rawPoints.slice(0, maxPoints),
    truncated: rawPoints.length > maxPoints
  };
};

const resolveRecommendation = (chart?: ResultChartModel): string => {
  if (!chart) {
    return '表格查看';
  }
  return chart.type === 'line' ? '趋势折线图' : '对比柱状图';
};

const isNumericColumn = (rows: Array<Record<string, unknown>>, column: string): boolean => {
  const values = rows
    .map((row) => row[column])
    .filter((value) => value !== null && value !== undefined && String(value).trim() !== '');
  if (!values.length) {
    return false;
  }
  const numericCount = values.filter((value) => parseNumericValue(value) !== null).length;
  return numericCount / values.length >= 0.8;
};

const isTemporalColumn = (rows: Array<Record<string, unknown>>, column: string): boolean => {
  const values = rows
    .map((row) => row[column])
    .filter((value) => value !== null && value !== undefined && String(value).trim() !== '');
  if (!values.length) {
    return false;
  }
  return values.every((value) => looksLikeDateValue(value));
};

const looksLikeDateValue = (value: unknown): boolean => {
  if (value instanceof Date) {
    return true;
  }
  if (typeof value !== 'string') {
    return false;
  }
  const normalized = value.trim();
  if (!normalized) {
    return false;
  }
  if (!/^\d{4}[-/]\d{1,2}[-/]\d{1,2}/.test(normalized) && !/^\d{4}-\d{1,2}$/.test(normalized)) {
    return false;
  }
  const timestamp = Date.parse(normalized);
  return Number.isFinite(timestamp);
};

const parseNumericValue = (value: unknown): number | null => {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null;
  }
  if (typeof value === 'bigint') {
    return Number(value);
  }
  if (typeof value !== 'string') {
    return null;
  }
  const normalized = value.trim().replace(/,/g, '');
  if (!normalized) {
    return null;
  }
  const percentValue = normalized.endsWith('%') ? normalized.slice(0, -1) : normalized;
  const numericValue = Number(percentValue);
  return Number.isFinite(numericValue) ? numericValue : null;
};

const normalizeDisplayValue = (value: unknown): string => {
  if (value === null || value === undefined || value === '') {
    return '--';
  }
  if (value instanceof Date) {
    return value.toISOString().slice(0, 19).replace('T', ' ');
  }
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value);
    } catch (error) {
      return String(value);
    }
  }
  return String(value);
};

const escapeCsvValue = (value: unknown): string => {
  let text = normalizeDisplayValue(value);
  if (typeof value === 'string' && /^[\t\r\n ]*[=+\-@]/.test(value)) {
    text = `'${text}`;
  }
  text = text.replace(/"/g, '""');
  return `"${text}"`;
};
