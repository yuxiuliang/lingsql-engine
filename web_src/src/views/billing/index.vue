<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import {
  activateBillingPolicy,
  BillingPolicyVersion,
  BillingPolicyVersionRequest,
  createBillingPolicy,
  fetchBillingOverride,
  fetchBillingOverrides,
  fetchBillingPolicies,
  TenantBillingOverride,
  TenantBillingOverrideRequest,
  updateBillingPolicy,
  upsertBillingOverride
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

onMounted(loadPolicies);
onMounted(loadOverrides);
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
</style>
