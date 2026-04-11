<script setup lang="ts">
import {computed, onMounted, reactive, ref, watch} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import {useAuthStore} from '@/store/auth';
import {fetchTenants, TenantView} from '@/api/tenant';
import {
  createDigitalEmployee,
  deleteDigitalEmployee,
  DigitalEmployeeCreateRequest,
  DigitalEmployeeOverviewView,
  DigitalEmployeeRunResult,
  DigitalEmployeeView,
  fetchDigitalEmployeeOverview,
  fetchDigitalEmployees,
  runDigitalEmployee,
  updateDigitalEmployee
} from '@/api/digital-employee';

const authStore = useAuthStore();
const tenantOptions = ref<TenantView[]>([]);
const employeeList = ref<DigitalEmployeeView[]>([]);
const overview = ref<DigitalEmployeeOverviewView | null>(null);

const tenantLoading = ref(false);
const employeeLoading = ref(false);
const overviewLoading = ref(false);
const saveLoading = ref(false);
const runLoading = ref(false);

const dialogVisible = ref(false);
const runDialogVisible = ref(false);
const resultDialogVisible = ref(false);
const editingEmployeeId = ref<number | null>(null);
const runningEmployee = ref<DigitalEmployeeView | null>(null);
const runResult = ref<DigitalEmployeeRunResult | null>(null);

const filterForm = reactive({
  tenantId: '',
  keyword: '',
  status: '' as '' | '1' | '0',
  windowHours: 24
});

const employeeForm = reactive<DigitalEmployeeCreateRequest>({
  tenantId: '',
  employeeCode: '',
  employeeName: '',
  roleName: '',
  scenario: '',
  promptTemplate: '',
  defaultQuestion: '',
  status: 1,
  remark: ''
});

const runForm = reactive({
  question: '',
  triggerSource: 'MANUAL'
});

/**
 * 当前选中的租户ID。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const activeTenantId = computed(() => authStore.activeTenantId || '');

/**
 * 概览统计卡片。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const summaryCards = computed(() => {
  return [
    {label: '数字员工总数', value: overview.value?.totalEmployeeCount ?? 0},
    {label: '启用员工数', value: overview.value?.activeEmployeeCount ?? 0},
    {label: '窗口内执行次数', value: overview.value?.runCount ?? 0},
    {label: '执行成功率', value: `${formatRate(overview.value?.successRate)}%`}
  ];
});

/**
 * 载入租户列表。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const loadTenants = async () => {
  tenantLoading.value = true;
  try {
    tenantOptions.value = await fetchTenants();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载租户列表失败');
  } finally {
    tenantLoading.value = false;
  }
};

/**
 * 载入数字员工列表。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const loadEmployees = async () => {
  employeeLoading.value = true;
  try {
    employeeList.value = await fetchDigitalEmployees({
      tenantId: filterForm.tenantId || undefined,
      keyword: filterForm.keyword.trim() || undefined,
      status: filterForm.status === '' ? undefined : Number(filterForm.status)
    });
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载数字员工失败');
  } finally {
    employeeLoading.value = false;
  }
};

/**
 * 载入数字员工概览。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const loadOverview = async () => {
  overviewLoading.value = true;
  try {
    overview.value = await fetchDigitalEmployeeOverview({
      tenantId: filterForm.tenantId || undefined,
      windowHours: filterForm.windowHours || 24
    });
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载运营概览失败');
  } finally {
    overviewLoading.value = false;
  }
};

/**
 * 刷新页面数据。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const refreshPage = async () => {
  await Promise.all([loadEmployees(), loadOverview()]);
};

/**
 * 重置员工表单。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const resetEmployeeForm = () => {
  employeeForm.tenantId = filterForm.tenantId || activeTenantId.value || '';
  employeeForm.employeeCode = '';
  employeeForm.employeeName = '';
  employeeForm.roleName = '';
  employeeForm.scenario = '';
  employeeForm.promptTemplate = '';
  employeeForm.defaultQuestion = '';
  employeeForm.status = 1;
  employeeForm.remark = '';
};

/**
 * 打开新增表单。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleCreate = () => {
  editingEmployeeId.value = null;
  resetEmployeeForm();
  dialogVisible.value = true;
};

/**
 * 打开编辑表单。
 *
 * @param row 数字员工
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleEdit = (row: DigitalEmployeeView) => {
  editingEmployeeId.value = row.id;
  employeeForm.tenantId = row.tenantId || '';
  employeeForm.employeeCode = row.employeeCode || '';
  employeeForm.employeeName = row.employeeName || '';
  employeeForm.roleName = row.roleName || '';
  employeeForm.scenario = row.scenario || '';
  employeeForm.promptTemplate = row.promptTemplate || '';
  employeeForm.defaultQuestion = row.defaultQuestion || '';
  employeeForm.status = row.status ?? 1;
  employeeForm.remark = row.remark || '';
  dialogVisible.value = true;
};

/**
 * 保存数字员工。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleSave = async () => {
  if (!employeeForm.tenantId.trim()) {
    ElMessage.warning('请选择租户');
    return;
  }
  if (!employeeForm.employeeName.trim()) {
    ElMessage.warning('请输入员工名称');
    return;
  }
  if (!employeeForm.defaultQuestion.trim()) {
    ElMessage.warning('请输入默认执行问题');
    return;
  }
  saveLoading.value = true;
  try {
    const payload = {
      tenantId: employeeForm.tenantId.trim(),
      employeeCode: employeeForm.employeeCode?.trim() || undefined,
      employeeName: employeeForm.employeeName.trim(),
      roleName: employeeForm.roleName?.trim() || undefined,
      scenario: employeeForm.scenario?.trim() || undefined,
      promptTemplate: employeeForm.promptTemplate?.trim() || undefined,
      defaultQuestion: employeeForm.defaultQuestion.trim(),
      status: employeeForm.status,
      remark: employeeForm.remark?.trim() || undefined
    };
    if (editingEmployeeId.value) {
      await updateDigitalEmployee(editingEmployeeId.value, payload);
      ElMessage.success('数字员工已更新');
    } else {
      await createDigitalEmployee(payload);
      ElMessage.success('数字员工已创建');
    }
    dialogVisible.value = false;
    await refreshPage();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存数字员工失败');
  } finally {
    saveLoading.value = false;
  }
};

/**
 * 删除数字员工。
 *
 * @param row 数字员工
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleDelete = async (row: DigitalEmployeeView) => {
  try {
    await ElMessageBox.confirm(`确认删除数字员工“${row.employeeName}”？`, '提示', {type: 'warning'});
    await deleteDigitalEmployee(row.id);
    ElMessage.success('数字员工已删除');
    await refreshPage();
  } catch (error) {
    if (!isMessageBoxCancel(error)) {
      ElMessage.error(error instanceof Error ? error.message : '删除数字员工失败');
    }
  }
};

/**
 * 打开执行对话框。
 *
 * @param row 数字员工
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleOpenRun = (row: DigitalEmployeeView) => {
  runningEmployee.value = row;
  runForm.question = row.defaultQuestion || '';
  runForm.triggerSource = 'MANUAL';
  runDialogVisible.value = true;
};

/**
 * 执行数字员工。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleRun = async () => {
  if (!runningEmployee.value) {
    return;
  }
  if (!runForm.question.trim()) {
    ElMessage.warning('请输入执行问题');
    return;
  }
  runLoading.value = true;
  try {
    runResult.value = await runDigitalEmployee(runningEmployee.value.id, {
      question: runForm.question.trim(),
      triggerSource: runForm.triggerSource
    });
    runDialogVisible.value = false;
    resultDialogVisible.value = true;
    ElMessage.success(runResult.value.success ? '执行完成' : '执行已结束，结果为失败');
    await refreshPage();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '执行数字员工失败');
  } finally {
    runLoading.value = false;
  }
};

/**
 * 解析状态文案。
 *
 * @param status 状态
 * @return 状态文案
 * @author 余炘洋
 * @date 2026-04-11
 */
const resolveStatusText = (status?: number | null) => {
  return status === 1 ? '启用' : '停用';
};

/**
 * 解析状态标签类型。
 *
 * @param status 状态
 * @return 标签类型
 * @author 余炘洋
 * @date 2026-04-11
 */
const resolveStatusType = (status?: number | null) => {
  return status === 1 ? 'success' : 'info';
};

/**
 * 解析最近执行结果文案。
 *
 * @param status 最近执行状态
 * @return 文案
 * @author 余炘洋
 * @date 2026-04-11
 */
const resolveLastRunText = (status?: number | null) => {
  if (status == null) {
    return '未执行';
  }
  return status === 1 ? '成功' : '失败';
};

/**
 * 格式化成功率。
 *
 * @param value 成功率
 * @return 百分比
 * @author 余炘洋
 * @date 2026-04-11
 */
const formatRate = (value?: number | null) => {
  if (value == null || Number.isNaN(Number(value))) {
    return '0.00';
  }
  return Number(value).toFixed(2);
};

/**
 * 计算员工成功率。
 *
 * @param row 数字员工
 * @return 百分比
 * @author 余炘洋
 * @date 2026-04-11
 */
const resolveEmployeeSuccessRate = (row: DigitalEmployeeView) => {
  const runCount = row.runCount || 0;
  const successCount = row.successCount || 0;
  if (runCount <= 0) {
    return '0.00%';
  }
  return `${((successCount * 100) / runCount).toFixed(2)}%`;
};

/**
 * 格式化时间文本。
 *
 * @param value 时间字符串
 * @return 格式化文本
 * @author 余炘洋
 * @date 2026-04-11
 */
const formatDateTime = (value?: string) => {
  if (!value) {
    return '-';
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleString('zh-CN', {hour12: false});
};

/**
 * 解析最近失败提示。
 *
 * @param row 数字员工
 * @return 错误提示
 * @author 余炘洋
 * @date 2026-04-11
 */
const resolveLastError = (row: DigitalEmployeeView) => {
  return row.lastErrorMessage?.trim() || '最近一次执行无错误信息';
};

/**
 * 是否为消息框取消动作。
 *
 * @param error 异常
 * @return 是否取消
 * @author 余炘洋
 * @date 2026-04-11
 */
const isMessageBoxCancel = (error: unknown) => {
  return error === 'cancel' || error === 'close';
};

watch(
  () => authStore.activeTenantId,
  (value) => {
    filterForm.tenantId = value || '';
    refreshPage();
  }
);

onMounted(async () => {
  filterForm.tenantId = activeTenantId.value;
  await loadTenants();
  await refreshPage();
});
</script>

<template>
  <div class="page">
    <div class="header-block">
      <div class="page-title">数字员工</div>
      <div class="page-desc">查看数字员工运营概览，并完成新增、维护与手动执行。</div>
    </div>

    <el-card class="card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-select v-model="filterForm.tenantId" placeholder="选择租户" clearable filterable :loading="tenantLoading" style="width: 220px">
            <el-option label="全部租户" value=""/>
            <el-option v-for="item in tenantOptions" :key="item.tenantId" :label="`${item.tenantName || item.tenantId} (${item.tenantId})`" :value="item.tenantId"/>
          </el-select>
          <el-input v-model="filterForm.keyword" placeholder="搜索员工名称 / 编码 / 场景" style="width: 260px"/>
          <el-select v-model="filterForm.status" placeholder="状态" clearable style="width: 120px">
            <el-option label="全部状态" value=""/>
            <el-option label="启用" value="1"/>
            <el-option label="停用" value="0"/>
          </el-select>
          <el-select v-model="filterForm.windowHours" placeholder="统计窗口" style="width: 140px">
            <el-option label="近 1 小时" :value="1"/>
            <el-option label="近 6 小时" :value="6"/>
            <el-option label="近 12 小时" :value="12"/>
            <el-option label="近 24 小时" :value="24"/>
            <el-option label="近 72 小时" :value="72"/>
            <el-option label="近 168 小时" :value="168"/>
          </el-select>
        </div>
        <div class="toolbar-actions">
          <el-button @click="refreshPage">刷新</el-button>
          <el-button type="primary" @click="handleCreate">新增数字员工</el-button>
        </div>
      </div>

      <div class="summary-grid" v-loading="overviewLoading">
        <div v-for="item in summaryCards" :key="item.label" class="summary-card">
          <div class="summary-label">{{ item.label }}</div>
          <div class="summary-value">{{ item.value }}</div>
          <div class="summary-tip">{{ filterForm.tenantId ? '当前租户视角' : '全部租户视角' }}</div>
        </div>
      </div>

      <div class="top-employee-panel">
        <div class="section-title">高频数字员工</div>
        <div v-if="overview?.topEmployees && overview.topEmployees.length > 0" class="top-employee-list">
          <div v-for="item in overview.topEmployees" :key="`${item.employeeId}-${item.employeeName}`" class="top-employee-item">
            <div class="top-employee-name">{{ item.employeeName || '未命名员工' }}</div>
            <div class="top-employee-meta">
              执行 {{ item.runCount || 0 }} 次
              <span class="meta-divider">|</span>
              成功率 {{ formatRate(item.successRate) }}%
            </div>
          </div>
        </div>
        <el-empty v-else description="当前窗口暂无数字员工执行记录"/>
      </div>
    </el-card>

    <el-card class="card" shadow="never">
      <div class="section-title">数字员工列表</div>
      <div class="table-wrap">
        <el-table :data="employeeList" border v-loading="employeeLoading">
          <el-table-column prop="employeeName" label="员工名称" min-width="180">
            <template #default="scope">
              <div class="primary-text">{{ scope.row.employeeName }}</div>
              <div class="secondary-text">{{ scope.row.employeeCode || '系统自动编码' }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="tenantId" label="租户ID" min-width="140"/>
          <el-table-column prop="scenario" label="业务场景" min-width="160"/>
          <el-table-column prop="defaultQuestion" label="默认执行问题" min-width="260" show-overflow-tooltip/>
          <el-table-column label="状态" width="96">
            <template #default="scope">
              <el-tag :type="resolveStatusType(scope.row.status)">{{ resolveStatusText(scope.row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="累计执行" width="100">
            <template #default="scope">{{ scope.row.runCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="成功率" width="110">
            <template #default="scope">{{ resolveEmployeeSuccessRate(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="最近执行" min-width="170">
            <template #default="scope">
              <div class="primary-text">{{ formatDateTime(scope.row.lastRunTime) }}</div>
              <div class="secondary-text">{{ resolveLastRunText(scope.row.lastRunStatus) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="最近异常" min-width="220" show-overflow-tooltip>
            <template #default="scope">{{ resolveLastError(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="scope">
              <div class="row-actions">
                <el-button size="small" type="primary" @click="handleOpenRun(scope.row)">执行</el-button>
                <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
                <el-button size="small" type="danger" plain @click="handleDelete(scope.row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingEmployeeId ? '编辑数字员工' : '新增数字员工'" width="680px" destroy-on-close>
      <el-form label-width="110px" class="form-grid">
        <el-form-item label="租户ID" required>
          <el-select v-model="employeeForm.tenantId" placeholder="请选择租户" filterable :loading="tenantLoading" style="width: 100%">
            <el-option v-for="item in tenantOptions" :key="item.tenantId" :label="`${item.tenantName || item.tenantId} (${item.tenantId})`" :value="item.tenantId"/>
          </el-select>
        </el-form-item>
        <el-form-item label="员工编码">
          <el-input v-model="employeeForm.employeeCode" placeholder="可留空，系统自动生成"/>
        </el-form-item>
        <el-form-item label="员工名称" required>
          <el-input v-model="employeeForm.employeeName" placeholder="例如：库存巡检助手"/>
        </el-form-item>
        <el-form-item label="岗位角色">
          <el-input v-model="employeeForm.roleName" placeholder="例如：运营分析师"/>
        </el-form-item>
        <el-form-item label="业务场景">
          <el-input v-model="employeeForm.scenario" placeholder="例如：库存、对账、日报"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="employeeForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="默认问题" required class="full-width">
          <el-input v-model="employeeForm.defaultQuestion" type="textarea" :rows="3" placeholder="请输入数字员工默认执行的问题"/>
        </el-form-item>
        <el-form-item label="提示模板" class="full-width">
          <el-input v-model="employeeForm.promptTemplate" type="textarea" :rows="3" placeholder="可选，用于补充岗位提示词"/>
        </el-form-item>
        <el-form-item label="备注" class="full-width">
          <el-input v-model="employeeForm.remark" type="textarea" :rows="2" placeholder="可选备注"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saveLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="runDialogVisible" title="执行数字员工" width="640px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="执行员工">
          <el-input :model-value="runningEmployee?.employeeName || ''" disabled/>
        </el-form-item>
        <el-form-item label="触发来源">
          <el-input v-model="runForm.triggerSource" disabled/>
        </el-form-item>
        <el-form-item label="执行问题" required>
          <el-input v-model="runForm.question" type="textarea" :rows="4" placeholder="可基于默认问题做微调"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="runDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="runLoading" @click="handleRun">开始执行</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultDialogVisible" title="执行结果" width="760px" destroy-on-close>
      <template v-if="runResult">
        <div class="result-summary">
          <el-tag :type="runResult.success ? 'success' : 'danger'">{{ runResult.success ? '执行成功' : '执行失败' }}</el-tag>
          <span>耗时：{{ runResult.durationMs || 0 }} ms</span>
          <span>执行时间：{{ formatDateTime(runResult.executeTime) }}</span>
        </div>
        <div class="result-block">
          <div class="result-label">问题</div>
          <div class="result-content">{{ runResult.question || '-' }}</div>
        </div>
        <div class="result-block">
          <div class="result-label">回答</div>
          <div class="result-content">{{ runResult.answer || '-' }}</div>
        </div>
        <div class="result-block">
          <div class="result-label">SQL</div>
          <pre class="result-code">{{ runResult.sql || '-' }}</pre>
        </div>
        <div class="result-block">
          <div class="result-label">结果行数</div>
          <div class="result-content">{{ runResult.data?.length || 0 }}</div>
        </div>
        <div v-if="runResult.errorMessage" class="result-block">
          <div class="result-label">错误信息</div>
          <div class="result-error">{{ runResult.errorMessage }}</div>
        </div>
      </template>
      <template #footer>
        <el-button type="primary" @click="resultDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  padding: 6px;
}

.header-block {
  margin-bottom: 12px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 6px;
}

.page-desc {
  color: #64748b;
  margin-top: 4px;
}

.card {
  border-radius: 14px;
  margin-bottom: 16px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-actions {
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
  background: linear-gradient(180deg, #f8fafc 0%, #eef9f2 100%);
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #dbe7e0;
}

.summary-label {
  color: #475569;
  font-size: 13px;
}

.summary-value {
  font-size: 22px;
  font-weight: 600;
  margin-top: 6px;
  color: #0f172a;
}

.summary-tip {
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #0f172a;
}

.top-employee-panel {
  margin-top: 20px;
}

.top-employee-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.top-employee-item {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px;
  background: #ffffff;
}

.top-employee-name {
  font-weight: 600;
  color: #0f172a;
}

.top-employee-meta {
  margin-top: 6px;
  color: #64748b;
  font-size: 13px;
}

.meta-divider {
  margin: 0 6px;
}

.table-wrap {
  overflow-x: auto;
}

.table-wrap :deep(.el-table) {
  min-width: 980px;
}

.primary-text {
  color: #0f172a;
  font-weight: 500;
}

.secondary-text {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}

.form-grid :deep(.el-form-item) {
  margin-bottom: 18px;
}

.full-width {
  grid-column: 1 / -1;
}

.result-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 18px;
  color: #475569;
}

.result-block {
  margin-bottom: 16px;
}

.result-label {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 8px;
}

.result-content {
  color: #0f172a;
  line-height: 1.7;
  white-space: pre-wrap;
}

.result-code {
  margin: 0;
  padding: 12px;
  border-radius: 10px;
  background: #0f172a;
  color: #e2e8f0;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

.result-error {
  color: #b91c1c;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 10px;
  padding: 12px;
}

@media (max-width: 900px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .page {
    padding: 0;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .toolbar-actions {
    width: 100%;
  }

  .toolbar-actions :deep(.el-button) {
    flex: 1;
  }
}
</style>
