<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import {
  activateBillingPolicy,
  BillingPolicyVersion,
  BillingPolicyVersionRequest,
  BillingUnknownInvocation,
  createBillingPolicy,
  fetchBillingOverride,
  fetchBillingOverrides,
  fetchBillingPolicies,
  fetchUnknownInvocations,
  fetchVendorBills,
  importVendorBills,
  resolveUnknownInvocation,
  TenantBillingOverride,
  TenantBillingOverrideRequest,
  updateBillingPolicy,
  upsertBillingOverride,
  VendorBillEvent,
  VendorBillImportReport
} from '@/api/billing';

const policyList = ref<BillingPolicyVersion[]>([]);
const policyLoading = ref(false);
const policyDialogVisible = ref(false);
const policySaving = ref(false);
const editingPolicyId = ref<number | null>(null);

const policyForm = reactive({
  versionCode: '',
  status: 1,
  effectiveTime: '',
  defaultUnitCost: null as number | null,
  defaultDailyLimit: null as number | null,
  defaultDailyTokenLimit: null as number | null,
  defaultTenantWeight: null as number | null,
  modelUnitCostText: '',
  serviceLevelLimitText: '',
  tenantTypeWeightText: '',
  description: ''
});

const overrideList = ref<TenantBillingOverride[]>([]);
const overrideLoading = ref(false);
const overrideDialogVisible = ref(false);
const overrideSaving = ref(false);
const overrideKeyword = ref('');
const editingOverride = ref(false);

const overrideForm = reactive({
  tenantId: '',
  priceMultiplier: null as number | null,
  status: 1,
  remark: ''
});

/**
 * 格式化 JSON 文本。
 *
 * @param value 原始 JSON
 * @return 格式化后的文本
 * @author 余炘洋
 * @date 2026-01-27
 */
const formatJsonText = (value?: string | null): string => {
  if (!value) {
    return '';
  }
  try {
    const parsed = JSON.parse(value);
    return JSON.stringify(parsed, null, 2);
  } catch {
    return String(value);
  }
};

/**
 * 解析 JSON 文本。
 *
 * @param text 输入文本
 * @param label 字段说明
 * @return JSON 对象
 * @author 余炘洋
 * @date 2026-01-27
 */
const parseJsonText = <T>(text: string, label: string): T | undefined => {
  if (!text || !text.trim()) {
    return undefined;
  }
  try {
    return JSON.parse(text) as T;
  } catch {
    throw new Error(`${label} 格式错误，请输入合法 JSON`);
  }
};

const resolveNumber = (value: number | null): number | undefined => {
  return value == null ? undefined : value;
};

const jsonFieldLabels = {
  modelUnitCostText: '模型单价配置',
  serviceLevelLimitText: '服务等级限额',
  tenantTypeWeightText: '租户类型权重'
} as const;

type JsonFieldKey = keyof typeof jsonFieldLabels;

/**
 * 校验/格式化 JSON 文本。
 *
 * @param field 字段
 * @param mode 操作模式
 * @author 余炘洋
 * @date 2026-01-27
 */
const handleJsonAction = (field: JsonFieldKey, mode: 'validate' | 'format') => {
  const label = jsonFieldLabels[field];
  const raw = policyForm[field];
  if (!raw || !raw.trim()) {
    ElMessage.warning(`${label} 为空`);
    return;
  }
  try {
    const parsed = JSON.parse(raw);
    if (mode === 'format') {
      policyForm[field] = JSON.stringify(parsed, null, 2);
      ElMessage.success(`${label} 已格式化`);
      return;
    }
    ElMessage.success(`${label} 校验通过`);
  } catch {
    ElMessage.error(`${label} 格式错误，请检查括号与引号`);
  }
};

/**
 * 重置计费策略表单。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
const resetPolicyForm = () => {
  policyForm.versionCode = '';
  policyForm.status = 1;
  policyForm.effectiveTime = '';
  policyForm.defaultUnitCost = null;
  policyForm.defaultDailyLimit = null;
  policyForm.defaultDailyTokenLimit = null;
  policyForm.defaultTenantWeight = null;
  policyForm.modelUnitCostText = '';
  policyForm.serviceLevelLimitText = '';
  policyForm.tenantTypeWeightText = '';
  policyForm.description = '';
};

/**
 * 加载计费策略列表。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
const loadPolicies = async () => {
  policyLoading.value = true;
  try {
    policyList.value = await fetchBillingPolicies();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载计费策略失败');
  } finally {
    policyLoading.value = false;
  }
};

/**
 * 新增计费策略。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
const handleCreatePolicy = () => {
  editingPolicyId.value = null;
  resetPolicyForm();
  policyDialogVisible.value = true;
};

/**
 * 编辑计费策略。
 *
 * @param row 策略数据
 * @author 余炘洋
 * @date 2026-01-27
 */
const handleEditPolicy = (row: BillingPolicyVersion) => {
  editingPolicyId.value = row.id;
  policyForm.versionCode = row.versionCode || '';
  policyForm.status = row.status ?? 1;
  policyForm.effectiveTime = row.effectiveTime || '';
  policyForm.defaultUnitCost = row.defaultUnitCost ?? null;
  policyForm.defaultDailyLimit = row.defaultDailyLimit ?? null;
  policyForm.defaultDailyTokenLimit = row.defaultDailyTokenLimit ?? null;
  policyForm.defaultTenantWeight = row.defaultTenantWeight ?? null;
  policyForm.modelUnitCostText = formatJsonText(row.modelUnitCost);
  policyForm.serviceLevelLimitText = formatJsonText(row.serviceLevelLimit);
  policyForm.tenantTypeWeightText = formatJsonText(row.tenantTypeWeight);
  policyForm.description = row.description || '';
  policyDialogVisible.value = true;
};

/**
 * 保存计费策略。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
const handleSavePolicy = async () => {
  if (!policyForm.versionCode.trim()) {
    ElMessage.warning('请填写版本号');
    return;
  }
  let payload: BillingPolicyVersionRequest;
  try {
    payload = {
      versionCode: policyForm.versionCode.trim(),
      status: policyForm.status,
      effectiveTime: policyForm.effectiveTime || undefined,
      defaultUnitCost: resolveNumber(policyForm.defaultUnitCost),
      defaultDailyLimit: resolveNumber(policyForm.defaultDailyLimit),
      defaultDailyTokenLimit: resolveNumber(policyForm.defaultDailyTokenLimit),
      defaultTenantWeight: resolveNumber(policyForm.defaultTenantWeight),
      modelUnitCost: parseJsonText<Record<string, number>>(policyForm.modelUnitCostText, '模型单价配置'),
      serviceLevelLimit: parseJsonText<Record<string, {dailyCallLimit: number; dailyTokenLimit: number}>>(
        policyForm.serviceLevelLimitText,
        '服务等级限额'
      ),
      tenantTypeWeight: parseJsonText<Record<string, number>>(policyForm.tenantTypeWeightText, '租户类型权重'),
      description: policyForm.description || undefined
    };
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '计费策略格式错误');
    return;
  }
  policySaving.value = true;
  try {
    if (editingPolicyId.value) {
      await updateBillingPolicy(editingPolicyId.value, payload);
      ElMessage.success('计费策略已更新');
    } else {
      await createBillingPolicy(payload);
      ElMessage.success('计费策略已创建');
    }
    policyDialogVisible.value = false;
    await loadPolicies();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存计费策略失败');
  } finally {
    policySaving.value = false;
  }
};

/**
 * 激活计费策略版本。
 *
 * @param row 版本信息
 * @author 余炘洋
 * @date 2026-01-27
 */
const handleActivatePolicy = async (row: BillingPolicyVersion) => {
  if (!row.id) {
    return;
  }
  try {
    await ElMessageBox.confirm(`确认将版本 ${row.versionCode} 设为生效？`, '提示', {type: 'warning'});
    await activateBillingPolicy(row.id);
    ElMessage.success('计费策略已生效');
    await loadPolicies();
  } catch (error) {
    if (isMessageBoxCancel(error)) {
      return;
    }
    ElMessage.error(error instanceof Error ? error.message : '计费策略生效失败');
  }
};

/**
 * 加载租户覆盖列表。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
const loadOverrides = async () => {
  overrideLoading.value = true;
  try {
    overrideList.value = await fetchBillingOverrides();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载租户覆盖失败');
  } finally {
    overrideLoading.value = false;
  }
};

/**
 * 过滤后的租户覆盖列表。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
const filteredOverrides = computed(() => {
  const keyword = overrideKeyword.value.trim().toLowerCase();
  if (!keyword) {
    return overrideList.value;
  }
  return overrideList.value.filter((item) => String(item.tenantId || '').toLowerCase().includes(keyword));
});

/**
 * 打开租户覆盖编辑。
 *
 * @param row 覆盖配置
 * @author 余炘洋
 * @date 2026-01-27
 */
const handleEditOverride = (row?: TenantBillingOverride) => {
  editingOverride.value = !!row;
  overrideForm.tenantId = row?.tenantId || '';
  overrideForm.priceMultiplier = row?.priceMultiplier ?? null;
  overrideForm.status = row?.status ?? 1;
  overrideForm.remark = row?.remark || '';
  overrideDialogVisible.value = true;
};

/**
 * 载入指定租户覆盖。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
const handleLoadOverride = async () => {
  if (!overrideForm.tenantId.trim()) {
    ElMessage.warning('请输入租户ID');
    return;
  }
  try {
    const data = await fetchBillingOverride(overrideForm.tenantId.trim());
    handleEditOverride(data);
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载租户覆盖失败';
    if (message.includes('租户覆盖不存在')) {
      ElMessage.info('未找到覆盖配置，将按新配置保存');
      editingOverride.value = false;
      return;
    }
    ElMessage.error(message);
  }
};

/**
 * 保存租户覆盖。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
const handleSaveOverride = async () => {
  if (!overrideForm.tenantId.trim()) {
    ElMessage.warning('请输入租户ID');
    return;
  }
  const payload: TenantBillingOverrideRequest = {
    priceMultiplier: resolveNumber(overrideForm.priceMultiplier),
    status: overrideForm.status,
    remark: overrideForm.remark || undefined
  };
  overrideSaving.value = true;
  try {
    await upsertBillingOverride(overrideForm.tenantId.trim(), payload);
    ElMessage.success('租户计费覆盖已保存');
    overrideDialogVisible.value = false;
    await loadOverrides();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存租户覆盖失败');
  } finally {
    overrideSaving.value = false;
  }
};

/**
 * 判断是否为弹窗取消。
 *
 * @param error 异常对象
 * @return 是否取消
 * @author 余炘洋
 * @date 2026-01-27
 */
const isMessageBoxCancel = (error: unknown) => {
  return error === 'cancel' || error === 'close';
};

/**
 * 供应商账单匹配状态选项。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
const VENDOR_BILL_MATCH_STATE_OPTIONS = [
  {value: 'NO_REFERENCE', label: '无请求标识'},
  {value: 'NOT_FOUND', label: '未找到调用'},
  {value: 'PENDING', label: '调用待终态'},
  {value: 'UNKNOWN', label: '调用 UNKNOWN'},
  {value: 'MATCHED', label: '一致'},
  {value: 'MISMATCH', label: '不一致'},
  {value: 'CONFLICT', label: '冲突'}
];

const vendorBillList = ref<VendorBillEvent[]>([]);
const vendorBillLoading = ref(false);
const vendorBillImporting = ref(false);
const vendorBillCsv = ref('');
const vendorBillImportReport = ref<VendorBillImportReport | null>(null);
const vendorBillFilter = reactive({tenantId: '', providerName: '', matchState: '', externalBillId: ''});
const vendorBillPage = reactive({page: 1, size: 10, total: 0});

const unknownList = ref<BillingUnknownInvocation[]>([]);
const unknownLoading = ref(false);
const unknownFilter = reactive({tenantId: '', providerName: ''});
const unknownPage = reactive({page: 1, size: 10, total: 0});

const resolveDialogVisible = ref(false);
const resolveSaving = ref(false);
const resolveTarget = ref<BillingUnknownInvocation | null>(null);
const evidenceBills = ref<VendorBillEvent[]>([]);
const evidenceSearch = reactive({externalBillId: ''});
const resolveForm = reactive({
  resolutionType: 'CONFIRMED_USAGE',
  evidenceHash: '',
  providerRequestId: '',
  actualTokens: null as number | null
});

/**
 * 加载供应商账单列表。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
const loadVendorBills = async () => {
  vendorBillLoading.value = true;
  try {
    const result = await fetchVendorBills({
      tenantId: vendorBillFilter.tenantId.trim() || undefined,
      providerName: vendorBillFilter.providerName.trim() || undefined,
      matchState: vendorBillFilter.matchState || undefined,
      externalBillId: vendorBillFilter.externalBillId.trim() || undefined,
      page: vendorBillPage.page,
      size: vendorBillPage.size
    });
    vendorBillList.value = result.list;
    vendorBillPage.total = result.total;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载供应商账单失败');
  } finally {
    vendorBillLoading.value = false;
  }
};

/**
 * 按条件查询供应商账单。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
const handleSearchVendorBills = async () => {
  vendorBillPage.page = 1;
  await loadVendorBills();
};

/**
 * 导入供应商账单 CSV。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
const handleImportVendorBills = async () => {
  if (!vendorBillCsv.value.trim()) {
    ElMessage.warning('请粘贴供应商账单 CSV');
    return;
  }
  vendorBillImporting.value = true;
  try {
    const report = await importVendorBills(vendorBillCsv.value);
    vendorBillImportReport.value = report;
    ElMessage.success(
      `导入完成：导入 ${report.importedCount} 行，重复 ${report.duplicateCount} 行，拒绝 ${report.rejectedCount} 行`
    );
    vendorBillPage.page = 1;
    await loadVendorBills();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导入供应商账单失败');
  } finally {
    vendorBillImporting.value = false;
  }
};

/**
 * 供应商账单翻页。
 *
 * @param page 页码
 * @author 余炘洋
 * @date 2026-10-17
 */
const handleVendorBillPageChange = async (page: number) => {
  vendorBillPage.page = page;
  await loadVendorBills();
};

/**
 * 加载 UNKNOWN 物理调用列表。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
const loadUnknownInvocations = async () => {
  unknownLoading.value = true;
  try {
    const result = await fetchUnknownInvocations({
      tenantId: unknownFilter.tenantId.trim() || undefined,
      providerName: unknownFilter.providerName.trim() || undefined,
      page: unknownPage.page,
      size: unknownPage.size
    });
    unknownList.value = result.list;
    unknownPage.total = result.total;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载 UNKNOWN 调用失败');
  } finally {
    unknownLoading.value = false;
  }
};

/**
 * 按条件查询 UNKNOWN 物理调用。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
const handleSearchUnknown = async () => {
  unknownPage.page = 1;
  await loadUnknownInvocations();
};

/**
 * UNKNOWN 调用翻页。
 *
 * @param page 页码
 * @author 余炘洋
 * @date 2026-10-17
 */
const handleUnknownPageChange = async (page: number) => {
  unknownPage.page = page;
  await loadUnknownInvocations();
};

/**
 * 打开裁决弹窗。
 *
 * @param row UNKNOWN 物理调用
 * @author 余炘洋
 * @date 2026-10-17
 */
const handleOpenResolve = (row: BillingUnknownInvocation) => {
  resolveTarget.value = row;
  resolveForm.resolutionType = 'CONFIRMED_USAGE';
  resolveForm.evidenceHash = '';
  resolveForm.providerRequestId = '';
  resolveForm.actualTokens = null;
  evidenceSearch.externalBillId = '';
  evidenceBills.value = [];
  resolveDialogVisible.value = true;
};

/**
 * 检索可用于裁决的供应商账单证据。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
const handleSearchEvidence = async () => {
  const target = resolveTarget.value;
  if (!target || !target.tenantId) {
    ElMessage.warning('当前调用缺少租户信息，无法检索账单');
    return;
  }
  try {
    const result = await fetchVendorBills({
      tenantId: target.tenantId,
      providerName: target.providerName || undefined,
      externalBillId: evidenceSearch.externalBillId.trim() || undefined,
      page: 1,
      size: 10
    });
    evidenceBills.value = result.list;
    if (result.list.length === 0) {
      ElMessage.info('未找到匹配账单，请手动填写证据摘要与供应商请求ID');
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '检索供应商账单失败');
  }
};

/**
 * 引用账单证据填充裁决表单。
 *
 * @param bill 供应商账单
 * @author 余炘洋
 * @date 2026-10-17
 */
const handleApplyBillEvidence = (bill: VendorBillEvent) => {
  resolveForm.evidenceHash = bill.evidenceHash || '';
  resolveForm.providerRequestId = bill.providerRequestId || '';
  if (bill.vendorTokens !== undefined && bill.vendorTokens !== null) {
    resolveForm.actualTokens = bill.vendorTokens;
  }
  ElMessage.success('已引用账单证据，请核对后提交裁决');
};

/**
 * 复制证据摘要。
 *
 * @param value 摘要文本
 * @author 余炘洋
 * @date 2026-10-17
 */
const handleCopyEvidence = async (value?: string) => {
  if (!value) {
    return;
  }
  try {
    await navigator.clipboard.writeText(value);
    ElMessage.success('证据摘要已复制');
  } catch {
    ElMessage.warning('当前环境不支持自动复制，请手动选择摘要文本');
  }
};

/**
 * 提交 UNKNOWN 裁决。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
const handleResolve = async () => {
  const target = resolveTarget.value;
  if (!target) {
    return;
  }
  const evidenceHash = resolveForm.evidenceHash.trim();
  if (!/^[0-9a-fA-F]{64}$/.test(evidenceHash)) {
    ElMessage.warning('证据摘要必须为 64 位十六进制 SHA-256');
    return;
  }
  const providerRequestId = resolveForm.providerRequestId.trim();
  if (!providerRequestId) {
    ElMessage.warning('请填写供应商请求ID');
    return;
  }
  const confirmedUsage = resolveForm.resolutionType === 'CONFIRMED_USAGE';
  if (confirmedUsage && (resolveForm.actualTokens === null || resolveForm.actualTokens === undefined)) {
    ElMessage.warning('确认用量时必须填写实际 Token');
    return;
  }
  try {
    await ElMessageBox.confirm('裁决不可逆：确认后将按外部证据固化该物理调用的资金状态。', '确认裁决', {
      type: 'warning'
    });
  } catch (error) {
    if (isMessageBoxCancel(error)) {
      return;
    }
    ElMessage.error('裁决确认失败，请重试');
    return;
  }
  resolveSaving.value = true;
  try {
    const receipt = await resolveUnknownInvocation(target.invocationId, {
      evidenceHash,
      providerRequestId,
      resolutionType: resolveForm.resolutionType as 'CONFIRMED_USAGE' | 'CONFIRMED_NOT_BILLED',
      actualTokens: confirmedUsage && resolveForm.actualTokens !== null ? resolveForm.actualTokens : undefined
    });
    ElMessage.success(`裁决完成：${receipt.state}，结算 ${receipt.actualAmount} 分，退款 ${receipt.refundAmount} 分`);
    resolveDialogVisible.value = false;
    await Promise.all([loadUnknownInvocations(), loadVendorBills()]);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '裁决失败');
  } finally {
    resolveSaving.value = false;
  }
};

/**
 * 匹配状态本地化标签。
 *
 * @param state 匹配状态
 * @return 状态标签
 * @author 余炘洋
 * @date 2026-10-17
 */
const formatMatchState = (state?: string) => {
  if (!state) {
    return '-';
  }
  const option = VENDOR_BILL_MATCH_STATE_OPTIONS.find((item) => item.value === state);
  return option ? option.label : state;
};

/**
 * 匹配状态标签样式。
 *
 * @param state 匹配状态
 * @return Element Plus 标签类型
 * @author 余炘洋
 * @date 2026-10-17
 */
const matchStateTagType = (state?: string) => {
  if (state === 'MATCHED') {
    return 'success';
  }
  if (state === 'MISMATCH' || state === 'CONFLICT') {
    return 'danger';
  }
  if (state === 'UNKNOWN') {
    return 'warning';
  }
  return 'info';
};

/**
 * 金额格式化。
 *
 * @param value 金额（分）
 * @return 展示文本
 * @author 余炘洋
 * @date 2026-10-17
 */
const formatMinor = (value?: number | null) => {
  if (value === undefined || value === null) {
    return '-';
  }
  return `${(value / 100).toFixed(2)} 元（${value} 分）`;
};

onMounted(loadPolicies);
onMounted(loadOverrides);
onMounted(loadVendorBills);
onMounted(loadUnknownInvocations);
</script>

<template>
  <div class="page">
    <div class="page-title">计费规则</div>
    <div class="page-desc">维护计费策略版本与租户覆盖配置。</div>

    <el-tabs type="border-card" class="card">
      <el-tab-pane label="策略版本">
        <div class="toolbar">
          <el-button type="primary" @click="handleCreatePolicy">新增策略</el-button>
          <el-button @click="loadPolicies">刷新</el-button>
        </div>
        <el-table :data="policyList" border v-loading="policyLoading">
          <el-table-column prop="versionCode" label="版本号" min-width="140"/>
          <el-table-column prop="status" label="状态" width="90">
            <template #default="scope">
              <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
                {{ scope.row.status === 1 ? '生效' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="effectiveTime" label="生效时间" min-width="160"/>
          <el-table-column prop="defaultUnitCost" label="默认单价" min-width="120"/>
          <el-table-column prop="defaultDailyLimit" label="默认日调用" min-width="120"/>
          <el-table-column prop="defaultDailyTokenLimit" label="默认日Token" min-width="140"/>
          <el-table-column prop="defaultTenantWeight" label="默认权重" min-width="120"/>
          <el-table-column prop="description" label="说明" min-width="200"/>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="scope">
              <el-button size="small" @click="handleEditPolicy(scope.row)">编辑</el-button>
              <el-button size="small" type="success" @click="handleActivatePolicy(scope.row)">生效</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="租户覆盖">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-input v-model="overrideKeyword" placeholder="租户ID搜索" style="width: 200px"/>
          </div>
          <div class="toolbar-right">
            <el-button type="primary" @click="handleEditOverride()">新增/更新</el-button>
            <el-button @click="loadOverrides">刷新</el-button>
          </div>
        </div>
        <el-table :data="filteredOverrides" border v-loading="overrideLoading">
          <el-table-column prop="tenantId" label="租户ID" min-width="160"/>
          <el-table-column prop="priceMultiplier" label="加价倍率" min-width="120"/>
          <el-table-column prop="status" label="状态" width="90">
            <template #default="scope">
              <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
                {{ scope.row.status === 1 ? '生效' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="200"/>
          <el-table-column prop="updateTime" label="更新时间" min-width="160"/>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="scope">
              <el-button size="small" @click="handleEditOverride(scope.row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="供应商账单">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-input v-model="vendorBillFilter.tenantId" placeholder="租户ID" style="width: 150px"/>
            <el-input v-model="vendorBillFilter.providerName" placeholder="供应商" style="width: 150px"/>
            <el-select v-model="vendorBillFilter.matchState" placeholder="匹配状态" clearable style="width: 150px">
              <el-option
                v-for="option in VENDOR_BILL_MATCH_STATE_OPTIONS"
                :key="option.value"
                :value="option.value"
                :label="option.label"
              />
            </el-select>
            <el-input v-model="vendorBillFilter.externalBillId" placeholder="外部账单标识" style="width: 180px"/>
            <el-button type="primary" @click="handleSearchVendorBills">查询</el-button>
          </div>
          <div class="toolbar-right">
            <el-button @click="loadVendorBills">刷新</el-button>
          </div>
        </div>
        <el-input
          v-model="vendorBillCsv"
          type="textarea"
          :rows="4"
          placeholder="tenantId,providerName,externalBillId,amountMinor,currency,status,providerRequestId,vendorTokens"
        />
        <div class="toolbar import-actions">
          <div class="toolbar-left">
            <div class="form-tip">
              逐行校验导入：非法行拒绝、重复行跳过且不覆盖已导入账单；未携带供应商请求标识的账单匹配状态为「无请求标识」。
            </div>
          </div>
          <div class="toolbar-right">
            <el-button @click="vendorBillCsv = ''">清空</el-button>
            <el-button type="primary" :loading="vendorBillImporting" @click="handleImportVendorBills">导入账单</el-button>
          </div>
        </div>
        <div v-if="vendorBillImportReport" class="import-report">
          <div class="form-tip">
            上次导入：数据行 {{ vendorBillImportReport.totalRows }}，导入 {{ vendorBillImportReport.importedCount }}，
            重复 {{ vendorBillImportReport.duplicateCount }}，拒绝 {{ vendorBillImportReport.rejectedCount }}；
            CSV 摘要 {{ vendorBillImportReport.csvSha256.slice(0, 16) }}…
          </div>
          <el-table :data="vendorBillImportReport.rows" border size="small" class="import-rows">
            <el-table-column prop="lineNumber" label="行号" width="70"/>
            <el-table-column prop="externalBillId" label="账单标识" min-width="140"/>
            <el-table-column prop="outcome" label="结果" width="110"/>
            <el-table-column label="匹配" width="120">
              <template #default="scope">{{ formatMatchState(scope.row.matchState) }}</template>
            </el-table-column>
            <el-table-column prop="message" label="说明" min-width="240"/>
          </el-table>
        </div>
        <el-table :data="vendorBillList" border v-loading="vendorBillLoading">
          <el-table-column prop="tenantId" label="租户ID" min-width="120"/>
          <el-table-column prop="providerName" label="供应商" min-width="120"/>
          <el-table-column prop="externalBillId" label="账单标识" min-width="140"/>
          <el-table-column prop="providerRequestId" label="供应商请求ID" min-width="160"/>
          <el-table-column label="账单金额" min-width="150">
            <template #default="scope">{{ formatMinor(scope.row.amountMinor) }}</template>
          </el-table-column>
          <el-table-column prop="vendorTokens" label="账单Token" width="100"/>
          <el-table-column prop="currency" label="币种" width="80"/>
          <el-table-column label="匹配状态" width="120">
            <template #default="scope">
              <el-tag :type="matchStateTagType(scope.row.matchState)">
                {{ formatMatchState(scope.row.matchState) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="平台结算" min-width="150">
            <template #default="scope">{{ formatMinor(scope.row.matchedActualAmount) }}</template>
          </el-table-column>
          <el-table-column prop="matchedState" label="调用状态" width="110"/>
          <el-table-column label="证据摘要" min-width="180">
            <template #default="scope">
              <el-button
                v-if="scope.row.evidenceHash"
                link
                type="primary"
                @click="handleCopyEvidence(scope.row.evidenceHash)"
              >
                {{ scope.row.evidenceHash.slice(0, 16) }}…
              </el-button>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="matchTime" label="匹配时间" min-width="160"/>
        </el-table>
        <el-pagination
          class="pagination"
          layout="total, prev, pager, next"
          :total="vendorBillPage.total"
          :current-page="vendorBillPage.page"
          :page-size="vendorBillPage.size"
          @current-change="handleVendorBillPageChange"
        />
      </el-tab-pane>
      <el-tab-pane label="UNKNOWN 裁决">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-input v-model="unknownFilter.tenantId" placeholder="租户ID" style="width: 160px"/>
            <el-input v-model="unknownFilter.providerName" placeholder="供应商" style="width: 160px"/>
            <el-button type="primary" @click="handleSearchUnknown">查询</el-button>
          </div>
          <div class="toolbar-right">
            <el-button @click="loadUnknownInvocations">刷新</el-button>
          </div>
        </div>
        <div class="form-tip unknown-hint">
          调用已派发但缺少可信用量，必须依据外部证据（供应商账单或供应商控制台记录）裁决，裁决结果不可回退。
        </div>
        <el-table :data="unknownList" border v-loading="unknownLoading">
          <el-table-column prop="invocationId" label="物理调用ID" min-width="180"/>
          <el-table-column prop="tenantId" label="租户ID" min-width="120"/>
          <el-table-column prop="stage" label="阶段" width="130"/>
          <el-table-column label="模型" min-width="160">
            <template #default="scope">
              {{ scope.row.modelName }}{{ scope.row.modelVersion ? ` / ${scope.row.modelVersion}` : '' }}
            </template>
          </el-table-column>
          <el-table-column prop="providerName" label="供应商" min-width="120"/>
          <el-table-column prop="maxBillableTokens" label="最大可计费Token" width="150"/>
          <el-table-column label="预留金额" min-width="150">
            <template #default="scope">{{ formatMinor(scope.row.reservedAmount) }}</template>
          </el-table-column>
          <el-table-column prop="failureCode" label="失败原因" min-width="140"/>
          <el-table-column prop="dispatchedAt" label="派发时间" min-width="160"/>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="scope">
              <el-button size="small" type="warning" @click="handleOpenResolve(scope.row)">裁决</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="pagination"
          layout="total, prev, pager, next"
          :total="unknownPage.total"
          :current-page="unknownPage.page"
          :page-size="unknownPage.size"
          @current-change="handleUnknownPageChange"
        />
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="policyDialogVisible"
      :title="editingPolicyId ? '编辑计费策略' : '新增计费策略'"
      width="720px"
      :close-on-click-modal="false"
    >
      <el-form label-width="140px">
        <el-form-item label="版本号" required>
          <el-input v-model="policyForm.versionCode" placeholder="例如：v2026.01"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="policyForm.status" style="width: 100%">
            <el-option :value="1" label="生效"/>
            <el-option :value="0" label="停用"/>
          </el-select>
        </el-form-item>
        <el-form-item label="生效时间">
          <el-date-picker
            v-model="policyForm.effectiveTime"
            type="datetime"
            placeholder="请选择生效时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="默认单价">
          <el-input-number v-model="policyForm.defaultUnitCost" :min="0" :step="1" style="width: 100%"/>
        </el-form-item>
        <el-form-item label="默认日调用上限">
          <el-input-number v-model="policyForm.defaultDailyLimit" :min="0" :step="1" style="width: 100%"/>
        </el-form-item>
        <el-form-item label="默认日Token上限">
          <el-input-number v-model="policyForm.defaultDailyTokenLimit" :min="0" :step="1" style="width: 100%"/>
        </el-form-item>
        <el-form-item label="默认租户权重">
          <el-input-number v-model="policyForm.defaultTenantWeight" :min="0" :step="0.1" style="width: 100%"/>
        </el-form-item>
        <el-form-item label="模型单价配置">
          <el-input
            v-model="policyForm.modelUnitCostText"
            type="textarea"
            :rows="4"
            placeholder='{"gpt-4": 120, "gpt-3.5": 20}'
          />
          <div class="json-actions">
            <el-button size="small" @click="handleJsonAction('modelUnitCostText', 'validate')">校验</el-button>
            <el-button size="small" @click="handleJsonAction('modelUnitCostText', 'format')">格式化</el-button>
          </div>
        </el-form-item>
        <el-form-item label="服务等级限额">
          <el-input
            v-model="policyForm.serviceLevelLimitText"
            type="textarea"
            :rows="4"
            placeholder='{"PRO": {"dailyCallLimit": 2000, "dailyTokenLimit": 200000}}'
          />
          <div class="json-actions">
            <el-button size="small" @click="handleJsonAction('serviceLevelLimitText', 'validate')">校验</el-button>
            <el-button size="small" @click="handleJsonAction('serviceLevelLimitText', 'format')">格式化</el-button>
          </div>
        </el-form-item>
        <el-form-item label="租户类型权重">
          <el-input
            v-model="policyForm.tenantTypeWeightText"
            type="textarea"
            :rows="3"
            placeholder='{"VIP": 1.2, "NORMAL": 1.0}'
          />
          <div class="json-actions">
            <el-button size="small" @click="handleJsonAction('tenantTypeWeightText', 'validate')">校验</el-button>
            <el-button size="small" @click="handleJsonAction('tenantTypeWeightText', 'format')">格式化</el-button>
          </div>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="policyForm.description" type="textarea" :rows="3" placeholder="版本说明"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="policyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="policySaving" @click="handleSavePolicy">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="overrideDialogVisible"
      :title="editingOverride ? '编辑租户覆盖' : '新增租户覆盖'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form label-width="120px">
        <el-form-item label="租户ID" required>
          <el-input v-model="overrideForm.tenantId" placeholder="输入租户ID"/>
          <div class="form-tip">填写租户ID（租户列表里的 tenantId），不是数据库主键ID。</div>
        </el-form-item>
        <el-form-item label="加价倍率">
          <el-input-number v-model="overrideForm.priceMultiplier" :min="0" :step="0.1" style="width: 100%"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="overrideForm.status" style="width: 100%">
            <el-option :value="1" label="生效"/>
            <el-option :value="0" label="停用"/>
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="overrideForm.remark" type="textarea" :rows="3" placeholder="说明备注"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleLoadOverride">加载</el-button>
        <el-button @click="overrideDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="overrideSaving" @click="handleSaveOverride">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="resolveDialogVisible"
      title="UNKNOWN 裁决"
      width="680px"
      :close-on-click-modal="false"
    >
      <el-form label-width="140px">
        <el-form-item label="物理调用ID">
          <el-input :model-value="resolveTarget?.invocationId || ''" disabled/>
        </el-form-item>
        <el-form-item label="调用信息">
          <div class="resolve-summary">
            <span>租户：{{ resolveTarget?.tenantId || '-' }}</span>
            <span>供应商：{{ resolveTarget?.providerName || '-' }}</span>
            <span>模型：{{ resolveTarget?.modelName || '-' }}</span>
            <span>预留：{{ formatMinor(resolveTarget?.reservedAmount ?? null) }}</span>
          </div>
        </el-form-item>
        <el-form-item label="检索账单证据">
          <div class="evidence-toolbar">
            <el-input v-model="evidenceSearch.externalBillId" placeholder="外部账单标识（可留空）"/>
            <el-button @click="handleSearchEvidence">检索</el-button>
          </div>
          <el-table v-if="evidenceBills.length" :data="evidenceBills" border size="small" class="import-rows">
            <el-table-column prop="externalBillId" label="账单标识" min-width="140"/>
            <el-table-column prop="providerRequestId" label="供应商请求ID" min-width="150"/>
            <el-table-column label="金额" min-width="130">
              <template #default="scope">{{ formatMinor(scope.row.amountMinor) }}</template>
            </el-table-column>
            <el-table-column prop="vendorTokens" label="Token" width="90"/>
            <el-table-column label="匹配" width="110">
              <template #default="scope">{{ formatMatchState(scope.row.matchState) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="scope">
                <el-button size="small" @click="handleApplyBillEvidence(scope.row)">引用</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="form-tip">引用后自动填充证据摘要与供应商请求ID，可再手工核对修改。</div>
        </el-form-item>
        <el-form-item label="裁决类型">
          <el-select v-model="resolveForm.resolutionType" style="width: 100%">
            <el-option value="CONFIRMED_USAGE" label="确认已产生用量（结算）"/>
            <el-option value="CONFIRMED_NOT_BILLED" label="确认未计费（退款取消）"/>
          </el-select>
        </el-form-item>
        <el-form-item label="证据摘要" required>
          <el-input v-model="resolveForm.evidenceHash" placeholder="外部证据 SHA-256（64 位十六进制）"/>
        </el-form-item>
        <el-form-item label="供应商请求ID" required>
          <el-input v-model="resolveForm.providerRequestId" placeholder="供应商账单或控制台中的请求标识"/>
        </el-form-item>
        <el-form-item v-if="resolveForm.resolutionType === 'CONFIRMED_USAGE'" label="实际 Token" required>
          <el-input-number v-model="resolveForm.actualTokens" :min="0" :step="1" style="width: 100%"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resolveDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="resolveSaving" @click="handleResolve">提交裁决</el-button>
      </template>
    </el-dialog>
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
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.json-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.form-tip {
  font-size: 12px;
  color: #64748b;
  margin-top: 6px;
}

.import-actions {
  margin-top: 12px;
}

.import-report {
  margin-bottom: 16px;
}

.import-rows {
  margin-top: 8px;
}

.unknown-hint {
  margin-bottom: 12px;
}

.pagination {
  margin-top: 12px;
  justify-content: flex-end;
}

.evidence-toolbar {
  display: flex;
  gap: 8px;
  width: 100%;
}

.resolve-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: #475569;
}
</style>
