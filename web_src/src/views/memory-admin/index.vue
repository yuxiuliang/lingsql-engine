<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import {fetchTenants, TenantView} from '@/api/tenant';
import {
  AgentMemoryAdminWriteRequest,
  AgentMemoryTier,
  AgentMemoryView,
  AgentMemoryWriteRequest,
  approveAdminMemoryCandidate,
  correctAdminMemory,
  createAdminMemory,
  exportAdminMemories,
  fetchAdminMemories,
  fetchAdminMemoryCandidates,
  purgeAdminMemory,
  rejectAdminMemoryCandidate,
  revokeAdminMemory
} from '@/api/memory';
import {useAuthStore} from '@/store/auth';

const authStore = useAuthStore();

const canRead = computed(() => authStore.hasPermission('ADMIN:MEMORY:READ'));
const canWrite = computed(() => authStore.hasPermission('ADMIN:MEMORY:WRITE'));

const tenants = ref<TenantView[]>([]);
const tenantLoading = ref(false);
const activeTenantId = ref('');
const filterUserId = ref<number | undefined>(undefined);
const filterTier = ref('');
const filterStatus = ref('');

const activeTab = ref('list');
const memories = ref<AgentMemoryView[]>([]);
const candidates = ref<AgentMemoryView[]>([]);
const memoryLoading = ref(false);
const candidateLoading = ref(false);
const exporting = ref(false);
const approvingId = ref<number | null>(null);
const rejectingId = ref<number | null>(null);

const writeVisible = ref(false);
const writeSubmitting = ref(false);
const writeForm = ref<{
  tier: AgentMemoryTier | '';
  userId?: number;
  factText: string;
  retentionDays?: number;
}>({tier: '', factText: ''});

const correctVisible = ref(false);
const correctSubmitting = ref(false);
const correctTenantId = ref('');
const correctTarget = ref<AgentMemoryView | null>(null);
const correctForm = ref<{factText: string; retentionDays?: number}>({factText: ''});

const tierOptions: Array<{value: AgentMemoryTier; label: string}> = [
  {value: 'PROCEDURAL', label: '租户程序记忆'},
  {value: 'PERMANENT', label: '用户永久记忆'}
];

const statusOptions: Array<{value: string; label: string}> = [
  {value: 'ACTIVE', label: '生效'},
  {value: 'CANDIDATE', label: '候选待确认'},
  {value: 'APPROVED', label: '候选已采纳'},
  {value: 'REJECTED', label: '候选已拒绝'},
  {value: 'SUPERSEDED', label: '已被新版本取代'},
  {value: 'REVOKED', label: '已撤销'},
  {value: 'EXPIRED', label: '已过期失效'}
];

/**
 * 判断候选是否为敏感内容。
 *
 * @param row 候选视图
 * @return 是否敏感
 * @author 余炘洋
 * @date 2026-10-19
 */
const isSensitive = (row: AgentMemoryView): boolean => {
  return row.sensitivity === 'SENSITIVE';
};

/**
 * 格式化后端时间。
 *
 * @param value ISO 时间字符串
 * @return 本地时间文本
 * @author 余炘洋
 * @date 2026-10-19
 */
const formatDate = (value: string | null | undefined): string => {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('zh-CN', {hour12: false});
};

/**
 * 格式化导出文件名日期。
 *
 * @return yyyyMMdd 文本
 * @author 余炘洋
 * @date 2026-10-19
 */
const formatFileDate = (): string => {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}${month}${day}`;
};

/**
 * 校验记忆内容与保留期，构造纠正请求。
 *
 * @param factText      记忆内容
 * @param retentionDays 保留天数
 * @return 合法请求，校验失败返回 null
 * @author 余炘洋
 * @date 2026-10-19
 */
const buildCorrectPayload = (
  factText: string,
  retentionDays?: number
): AgentMemoryWriteRequest | null => {
  const text = factText.trim();
  if (!text) {
    ElMessage.warning('记忆内容不能为空');
    return null;
  }
  if (text.length > 500) {
    ElMessage.warning('记忆内容不能超过 500 个字符');
    return null;
  }
  const payload: AgentMemoryWriteRequest = {factText: text};
  if (retentionDays !== undefined && retentionDays !== null) {
    if (!Number.isInteger(retentionDays) || retentionDays < 1 || retentionDays > 3650) {
      ElMessage.warning('保留天数需为 1-3650 的整数');
      return null;
    }
    payload.retentionDays = retentionDays;
  }
  return payload;
};

/**
 * 校验租户选择。
 *
 * @return 是否已选择租户
 * @author 余炘洋
 * @date 2026-10-19
 */
const requireTenant = (): boolean => {
  if (!activeTenantId.value) {
    ElMessage.warning('请先选择租户');
    return false;
  }
  return true;
};

/**
 * 加载租户列表。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
const loadTenants = async () => {
  if (!canRead.value) {
    return;
  }
  tenantLoading.value = true;
  try {
    tenants.value = await fetchTenants();
  } catch (error) {
    tenants.value = [];
    ElMessage.error(error instanceof Error ? error.message : '加载租户列表失败');
  } finally {
    tenantLoading.value = false;
  }
};

/**
 * 加载租户记忆列表。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
const loadMemories = async () => {
  if (!canRead.value || !activeTenantId.value) {
    memories.value = [];
    return;
  }
  memoryLoading.value = true;
  try {
    memories.value = await fetchAdminMemories({
      tenantId: activeTenantId.value,
      userId: filterUserId.value,
      tier: filterTier.value,
      status: filterStatus.value
    });
  } catch (error) {
    memories.value = [];
    ElMessage.error(error instanceof Error ? error.message : '加载记忆列表失败');
  } finally {
    memoryLoading.value = false;
  }
};

/**
 * 加载租户待确认候选。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
const loadCandidates = async () => {
  if (!canRead.value || !activeTenantId.value) {
    candidates.value = [];
    return;
  }
  candidateLoading.value = true;
  try {
    candidates.value = await fetchAdminMemoryCandidates(activeTenantId.value, filterUserId.value);
  } catch (error) {
    candidates.value = [];
    ElMessage.error(error instanceof Error ? error.message : '加载待确认候选失败');
  } finally {
    candidateLoading.value = false;
  }
};

/**
 * 刷新当前页签数据。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
const refreshActive = () => {
  if (activeTab.value === 'candidates') {
    loadCandidates();
    return;
  }
  loadMemories();
};

/**
 * 切换租户时清空旧数据并重新加载。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
const handleTenantChange = () => {
  memories.value = [];
  candidates.value = [];
  loadMemories();
  loadCandidates();
};

/**
 * 切换页签时刷新当前数据。
 *
 * @param name 页签名称
 * @author 余炘洋
 * @date 2026-10-19
 */
const handleTabChange = (name: string | number) => {
  if (name === 'candidates') {
    loadCandidates();
    return;
  }
  loadMemories();
};

/**
 * 打开写入记忆对话框。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
const openWrite = () => {
  writeForm.value = {tier: '', factText: ''};
  writeVisible.value = true;
};

/**
 * 切换记忆层级时清理不适用的用户 ID。
 *
 * @param tier 记忆层级
 * @author 余炘洋
 * @date 2026-10-19
 */
const handleTierChange = (tier: string) => {
  if (tier === 'PROCEDURAL') {
    writeForm.value.userId = undefined;
  }
};

/**
 * 提交管理端写入。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
const submitWrite = async () => {
  if (!requireTenant()) {
    return;
  }
  const form = writeForm.value;
  if (!form.tier) {
    ElMessage.warning('请选择记忆层级');
    return;
  }
  if (form.tier === 'PROCEDURAL' && form.userId !== undefined) {
    ElMessage.warning('租户程序记忆不能绑定用户');
    return;
  }
  if (form.tier === 'PERMANENT' && !form.userId) {
    ElMessage.warning('用户永久记忆必须填写用户 ID');
    return;
  }
  const text = form.factText.trim();
  if (!text) {
    ElMessage.warning('记忆内容不能为空');
    return;
  }
  if (text.length > 500) {
    ElMessage.warning('记忆内容不能超过 500 个字符');
    return;
  }
  if (form.retentionDays !== undefined && form.retentionDays !== null) {
    if (!Number.isInteger(form.retentionDays)
      || form.retentionDays < 1
      || form.retentionDays > 3650) {
      ElMessage.warning('保留天数需为 1-3650 的整数');
      return;
    }
  }
  const payload: AgentMemoryAdminWriteRequest = {
    tenantId: activeTenantId.value,
    tier: form.tier,
    factText: text
  };
  if (form.tier === 'PERMANENT') {
    payload.userId = form.userId;
  }
  if (form.retentionDays !== undefined && form.retentionDays !== null) {
    payload.retentionDays = form.retentionDays;
  }
  writeSubmitting.value = true;
  try {
    await createAdminMemory(payload);
    writeVisible.value = false;
    ElMessage.success('记忆已写入');
    await loadMemories();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '写入记忆失败');
  } finally {
    writeSubmitting.value = false;
  }
};

/**
 * 打开纠正记忆对话框。
 *
 * @param row 记忆视图
 * @author 余炘洋
 * @date 2026-10-19
 */
const openCorrect = (row: AgentMemoryView) => {
  correctTarget.value = row;
  correctTenantId.value = activeTenantId.value;
  correctForm.value = {factText: row.factText};
  correctVisible.value = true;
};

/**
 * 提交管理端纠正。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
const submitCorrect = async () => {
  if (!correctTarget.value || !correctTenantId.value) {
    return;
  }
  const payload = buildCorrectPayload(correctForm.value.factText, correctForm.value.retentionDays);
  if (!payload) {
    return;
  }
  correctSubmitting.value = true;
  try {
    await correctAdminMemory(correctTarget.value.id, correctTenantId.value, payload);
    correctVisible.value = false;
    ElMessage.success('记忆已纠正并生成新版本');
    await loadMemories();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '纠正记忆失败');
  } finally {
    correctSubmitting.value = false;
  }
};

/**
 * 撤销记忆。
 *
 * @param row 记忆视图
 * @author 余炘洋
 * @date 2026-10-19
 */
const handleRevoke = async (row: AgentMemoryView) => {
  if (!requireTenant()) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      '撤销后该记忆不再参与提示词注入，历史链路仍保留审计记录，是否继续？',
      '撤销记忆',
      {
        confirmButtonText: '撤销',
        cancelButtonText: '取消',
        type: 'warning'
      }
    );
  } catch (error) {
    return;
  }
  try {
    await revokeAdminMemory(row.id, activeTenantId.value);
    ElMessage.success('记忆已撤销');
    await loadMemories();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '撤销记忆失败');
  }
};

/**
 * 彻底清除记忆，操作不可恢复。
 *
 * @param row 记忆视图
 * @author 余炘洋
 * @date 2026-10-19
 */
const handlePurge = async (row: AgentMemoryView) => {
  if (!requireTenant()) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      `将彻底删除记忆 #${row.id} 的记录本身，仅保留审计摘要，操作不可恢复，是否继续？`,
      '彻底清除记忆',
      {
        confirmButtonText: '彻底清除',
        cancelButtonText: '取消',
        type: 'error'
      }
    );
  } catch (error) {
    return;
  }
  try {
    await purgeAdminMemory(row.id, activeTenantId.value);
    ElMessage.success('记忆已彻底清除');
    await Promise.all([loadMemories(), loadCandidates()]);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '彻底清除记忆失败');
  }
};

/**
 * 采纳候选并生成生效记忆。
 *
 * @param row 候选视图
 * @author 余炘洋
 * @date 2026-10-19
 */
const handleApproveCandidate = async (row: AgentMemoryView) => {
  if (!requireTenant()) {
    return;
  }
  if (isSensitive(row)) {
    ElMessage.warning('敏感候选不允许采纳');
    return;
  }
  approvingId.value = row.id;
  try {
    await approveAdminMemoryCandidate(row.id, activeTenantId.value);
    ElMessage.success('候选已采纳并生成生效记忆');
    await Promise.all([loadCandidates(), loadMemories()]);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '采纳候选失败');
  } finally {
    approvingId.value = null;
  }
};

/**
 * 拒绝候选，拒绝原因可选。
 *
 * @param row 候选视图
 * @author 余炘洋
 * @date 2026-10-19
 */
const handleRejectCandidate = async (row: AgentMemoryView) => {
  if (!requireTenant()) {
    return;
  }
  let reason = '';
  try {
    const {value} = await ElMessageBox.prompt('拒绝原因可选，将用于审计留档', '拒绝候选', {
      confirmButtonText: '拒绝',
      cancelButtonText: '取消',
      inputPlaceholder: '可填写拒绝原因',
      inputType: 'textarea'
    }) as {value: string};
    reason = (value || '').trim();
  } catch (error) {
    return;
  }
  rejectingId.value = row.id;
  try {
    await rejectAdminMemoryCandidate(row.id, activeTenantId.value, reason ? {reason} : {});
    ElMessage.success('候选已拒绝');
    await loadCandidates();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '拒绝候选失败');
  } finally {
    rejectingId.value = null;
  }
};

/**
 * 导出租户记忆与候选为 JSON 文件。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
const handleExport = async () => {
  if (!requireTenant()) {
    return;
  }
  exporting.value = true;
  try {
    const data = await exportAdminMemories(activeTenantId.value, filterUserId.value);
    const blob = new Blob([JSON.stringify(data, null, 2)], {
      type: 'application/json;charset=utf-8'
    });
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = `tenant-memories-${formatFileDate()}.json`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(objectUrl);
    ElMessage.success('租户记忆导出已开始下载');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出租户记忆失败');
  } finally {
    exporting.value = false;
  }
};

onMounted(async () => {
  if (!canRead.value) {
    return;
  }
  await loadTenants();
  if (!activeTenantId.value) {
    activeTenantId.value = authStore.activeTenantId || authStore.tenantId || '';
  }
  if (activeTenantId.value) {
    loadMemories();
    loadCandidates();
  }
});
</script>

<template>
  <div class="page">
    <div class="page-title">记忆治理</div>
    <div class="page-desc">
      按租户查看长期记忆与待确认候选，支持写入、纠正、撤销、候选审核、导出与彻底清除；所有治理操作进入审计。
    </div>

    <el-result
      v-if="!canRead"
      icon="warning"
      title="无权限访问"
      sub-title="当前账号没有记忆治理权限"
    />

    <template v-else>
      <el-card class="card" shadow="never">
        <div class="toolbar">
          <div class="filter-row">
            <el-select
              v-model="activeTenantId"
              filterable
              clearable
              placeholder="选择租户"
              style="width: 280px"
              :loading="tenantLoading"
              @change="handleTenantChange"
            >
              <el-option
                v-for="tenant in tenants"
                :key="tenant.tenantId"
                :label="`${tenant.tenantName}（${tenant.tenantId}）`"
                :value="tenant.tenantId"
              />
            </el-select>
            <el-input-number
              v-model="filterUserId"
              :min="1"
              :step="1"
              :controls="false"
              placeholder="用户 ID"
              style="width: 140px"
            />
            <el-select
              v-model="filterTier"
              clearable
              placeholder="记忆层级"
              style="width: 160px"
            >
              <el-option
                v-for="option in tierOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-select
              v-model="filterStatus"
              clearable
              placeholder="记忆状态"
              style="width: 160px"
            >
              <el-option
                v-for="option in statusOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-button :disabled="!activeTenantId" @click="refreshActive">刷新</el-button>
            <el-button :disabled="!activeTenantId" :loading="exporting" @click="handleExport">
              导出
            </el-button>
          </div>
          <div class="toolbar-actions">
            <el-button v-if="canWrite" type="primary" :disabled="!activeTenantId" @click="openWrite">
              写入记忆
            </el-button>
          </div>
        </div>
        <div class="toolbar-tip">用户 ID、层级与状态仅作为过滤条件；导出会包含当前租户内记忆与候选。</div>
      </el-card>

      <el-card class="card" shadow="never">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">
          <el-tab-pane label="记忆列表" name="list">
            <el-table
              :data="memories"
              border
              v-loading="memoryLoading"
              empty-text="当前租户暂无符合条件的记忆"
            >
              <el-table-column prop="factText" label="记忆内容" min-width="260" show-overflow-tooltip/>
              <el-table-column label="用户 ID" width="100">
                <template #default="scope">
                  {{ scope.row.userId ?? '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="tierLabel" label="层级" width="120"/>
              <el-table-column prop="sourceLabel" label="来源" width="130"/>
              <el-table-column prop="statusLabel" label="状态" width="120"/>
              <el-table-column label="敏感级别" width="110">
                <template #default="scope">
                  <el-tag v-if="isSensitive(scope.row)" type="danger">
                    {{ scope.row.sensitivityLabel || '敏感' }}
                  </el-tag>
                  <el-tag v-else type="success">{{ scope.row.sensitivityLabel || '普通' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="置信度" width="90">
                <template #default="scope">
                  {{ scope.row.confidence }}%
                </template>
              </el-table-column>
              <el-table-column prop="version" label="版本" width="80"/>
              <el-table-column label="复核人" width="120">
                <template #default="scope">
                  {{ scope.row.reviewedBy || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="到期时间" width="180">
                <template #default="scope">
                  {{ formatDate(scope.row.expiresAt) }}
                </template>
              </el-table-column>
              <el-table-column v-if="canWrite" label="操作" width="210" fixed="right">
                <template #default="scope">
                  <el-button link type="primary" @click="openCorrect(scope.row)">纠正</el-button>
                  <el-button link type="danger" @click="handleRevoke(scope.row)">撤销</el-button>
                  <el-button link type="danger" @click="handlePurge(scope.row)">彻底清除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="待确认候选" name="candidates">
            <div class="toolbar-tip">候选默认不生效；敏感候选只保留证据，不允许采纳。</div>
            <el-table
              :data="candidates"
              border
              v-loading="candidateLoading"
              empty-text="当前租户暂无待确认候选"
            >
              <el-table-column prop="factText" label="候选内容" min-width="240" show-overflow-tooltip/>
              <el-table-column label="用户 ID" width="100">
                <template #default="scope">
                  {{ scope.row.userId ?? '-' }}
                </template>
              </el-table-column>
              <el-table-column label="敏感级别" width="110">
                <template #default="scope">
                  <el-tooltip
                    v-if="isSensitive(scope.row)"
                    content="敏感候选不允许采纳"
                    placement="top"
                  >
                    <el-tag type="danger">{{ scope.row.sensitivityLabel || '敏感' }}</el-tag>
                  </el-tooltip>
                  <el-tag v-else type="success">{{ scope.row.sensitivityLabel || '普通' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="置信度" width="90">
                <template #default="scope">
                  {{ scope.row.confidence }}%
                </template>
              </el-table-column>
              <el-table-column prop="sourceRef" label="来源标识" min-width="150" show-overflow-tooltip/>
              <el-table-column label="创建时间" width="180">
                <template #default="scope">
                  {{ formatDate(scope.row.createdAt) }}
                </template>
              </el-table-column>
              <el-table-column label="到期时间" width="180">
                <template #default="scope">
                  {{ formatDate(scope.row.expiresAt) }}
                </template>
              </el-table-column>
              <el-table-column v-if="canWrite" label="操作" width="150" fixed="right">
                <template #default="scope">
                  <el-tooltip
                    :disabled="!isSensitive(scope.row)"
                    content="敏感候选不允许采纳"
                    placement="top"
                  >
                    <span>
                      <el-button
                        link
                        type="primary"
                        :disabled="isSensitive(scope.row)"
                        :loading="approvingId === scope.row.id"
                        @click="handleApproveCandidate(scope.row)"
                      >
                        采纳
                      </el-button>
                    </span>
                  </el-tooltip>
                  <el-button
                    link
                    type="danger"
                    :loading="rejectingId === scope.row.id"
                    @click="handleRejectCandidate(scope.row)"
                  >
                    拒绝
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </template>

    <el-dialog
      v-model="writeVisible"
      title="写入记忆"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form label-width="96px">
        <el-form-item label="记忆层级" required>
          <el-select v-model="writeForm.tier" placeholder="请选择记忆层级" style="width: 100%" @change="handleTierChange">
            <el-option
              v-for="option in tierOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="用户 ID" :required="writeForm.tier === 'PERMANENT'">
          <el-input-number
            v-model="writeForm.userId"
            :min="1"
            :step="1"
            :controls="false"
            :disabled="writeForm.tier === 'PROCEDURAL'"
            placeholder="永久记忆必填"
            style="width: 200px"
          />
          <div class="form-tip">租户程序记忆必须为空；用户永久记忆必须填写。</div>
        </el-form-item>
        <el-form-item label="记忆内容" required>
          <el-input
            v-model="writeForm.factText"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请输入需要写入的记忆事实"
          />
        </el-form-item>
        <el-form-item label="保留天数">
          <el-input-number
            v-model="writeForm.retentionDays"
            :min="1"
            :max="3650"
            :step="1"
            style="width: 160px"
          />
          <div class="form-tip">可选，范围 1-3650 天；留空使用系统默认保留期。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="writeVisible = false">取消</el-button>
        <el-button type="primary" :loading="writeSubmitting" @click="submitWrite">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="correctVisible"
      title="纠正记忆"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form label-width="88px">
        <el-form-item label="记忆内容" required>
          <el-input
            v-model="correctForm.factText"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请输入纠正后的记忆内容"
          />
        </el-form-item>
        <el-form-item label="保留天数">
          <el-input-number
            v-model="correctForm.retentionDays"
            :min="1"
            :max="3650"
            :step="1"
            style="width: 160px"
          />
          <div class="form-tip">可选，留空使用系统默认保留期。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="correctVisible = false">取消</el-button>
        <el-button type="primary" :loading="correctSubmitting" @click="submitCorrect">保存</el-button>
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

.filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-tip {
  color: #94a3b8;
  font-size: 12px;
  margin-bottom: 12px;
}

.form-tip {
  color: #94a3b8;
  font-size: 12px;
  margin-left: 12px;
}
</style>
