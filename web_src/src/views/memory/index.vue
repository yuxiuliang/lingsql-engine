<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import {
  AgentMemoryView,
  AgentMemoryWriteRequest,
  approveMyMemoryCandidate,
  correctMyMemory,
  createMyMemory,
  exportMyMemories,
  extractMyMemoryCandidates,
  fetchMyMemories,
  fetchMyMemoryCandidates,
  rejectMyMemoryCandidate,
  revokeMyMemory
} from '@/api/memory';
import {useAuthStore} from '@/store/auth';

const authStore = useAuthStore();

const canRead = computed(() => authStore.hasPermission('API:CHAT:MEMORY:READ'));
const canWrite = computed(() => authStore.hasPermission('API:CHAT:MEMORY:WRITE'));

const activeTab = ref('active');
const memories = ref<AgentMemoryView[]>([]);
const candidates = ref<AgentMemoryView[]>([]);
const memoryLoading = ref(false);
const candidateLoading = ref(false);
const extracting = ref(false);
const exporting = ref(false);
const approvingId = ref<number | null>(null);
const rejectingId = ref<number | null>(null);

const createVisible = ref(false);
const createSubmitting = ref(false);
const createForm = ref<{factText: string; retentionDays?: number}>({factText: ''});

const correctVisible = ref(false);
const correctSubmitting = ref(false);
const correctTarget = ref<AgentMemoryView | null>(null);
const correctForm = ref<{factText: string; retentionDays?: number}>({factText: ''});

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
 * 校验并构造写入请求，保留期为空时不提交。
 *
 * @param factText      记忆内容
 * @param retentionDays 保留天数
 * @return 合法请求，校验失败返回 null
 * @author 余炘洋
 * @date 2026-10-19
 */
const buildPayload = (
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
 * 加载我的生效记忆。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
const loadMemories = async () => {
  if (!canRead.value) {
    return;
  }
  memoryLoading.value = true;
  try {
    memories.value = await fetchMyMemories();
  } catch (error) {
    memories.value = [];
    ElMessage.error(error instanceof Error ? error.message : '加载生效记忆失败');
  } finally {
    memoryLoading.value = false;
  }
};

/**
 * 加载我的待确认候选。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
const loadCandidates = async () => {
  if (!canRead.value) {
    return;
  }
  candidateLoading.value = true;
  try {
    candidates.value = await fetchMyMemoryCandidates();
  } catch (error) {
    candidates.value = [];
    ElMessage.error(error instanceof Error ? error.message : '加载待确认候选失败');
  } finally {
    candidateLoading.value = false;
  }
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
 * 打开新增记忆对话框。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
const openCreate = () => {
  createForm.value = {factText: ''};
  createVisible.value = true;
};

/**
 * 提交新增记忆。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
const submitCreate = async () => {
  const payload = buildPayload(createForm.value.factText, createForm.value.retentionDays);
  if (!payload) {
    return;
  }
  createSubmitting.value = true;
  try {
    await createMyMemory(payload);
    createVisible.value = false;
    ElMessage.success('记忆已写入并生效');
    await loadMemories();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '新增记忆失败');
  } finally {
    createSubmitting.value = false;
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
  correctForm.value = {factText: row.factText};
  correctVisible.value = true;
};

/**
 * 提交纠正，生成新版本并失效旧事实。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
const submitCorrect = async () => {
  if (!correctTarget.value) {
    return;
  }
  const payload = buildPayload(correctForm.value.factText, correctForm.value.retentionDays);
  if (!payload) {
    return;
  }
  correctSubmitting.value = true;
  try {
    await correctMyMemory(correctTarget.value.id, payload);
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
    await revokeMyMemory(row.id);
    ElMessage.success('记忆已撤销');
    await loadMemories();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '撤销记忆失败');
  }
};

/**
 * 从当前会话窗口提炼候选。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
const handleExtract = async () => {
  extracting.value = true;
  try {
    const extracted = await extractMyMemoryCandidates({});
    if (extracted.length) {
      ElMessage.success(`已提炼 ${extracted.length} 条候选，请确认后再生效`);
    } else {
      ElMessage.info('当前会话没有可提炼的新候选');
    }
    activeTab.value = 'candidates';
    await loadCandidates();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提炼记忆候选失败');
  } finally {
    extracting.value = false;
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
  if (isSensitive(row)) {
    ElMessage.warning('敏感候选不允许采纳');
    return;
  }
  approvingId.value = row.id;
  try {
    await approveMyMemoryCandidate(row.id);
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
    await rejectMyMemoryCandidate(row.id, reason ? {reason} : {});
    ElMessage.success('候选已拒绝');
    await loadCandidates();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '拒绝候选失败');
  } finally {
    rejectingId.value = null;
  }
};

/**
 * 导出我的记忆与候选为 JSON 文件。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
const handleExport = async () => {
  exporting.value = true;
  try {
    const data = await exportMyMemories();
    const blob = new Blob([JSON.stringify(data, null, 2)], {
      type: 'application/json;charset=utf-8'
    });
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = `my-memories-${formatFileDate()}.json`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(objectUrl);
    ElMessage.success('记忆导出已开始下载');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出记忆失败');
  } finally {
    exporting.value = false;
  }
};

onMounted(() => {
  loadMemories();
  loadCandidates();
});
</script>

<template>
  <div class="page">
    <div class="page-title">我的记忆</div>
    <div class="page-desc">
      记忆默认不生效：模型提炼的事实只进入候选池，必须由你确认后才写入长期记忆；记忆按保留期自动失效，到期后不再参与提示词注入。
    </div>

    <el-result
      v-if="!canRead"
      icon="warning"
      title="无权限访问"
      sub-title="当前账号没有记忆查看权限"
    />

    <template v-else>
      <el-card class="card" shadow="never">
        <div class="toolbar">
          <div class="section-title">记忆操作</div>
          <div class="toolbar-actions">
            <el-button v-if="canWrite" :loading="extracting" @click="handleExtract">
              从当前会话提炼候选
            </el-button>
            <el-button :loading="exporting" @click="handleExport">导出我的记忆</el-button>
          </div>
        </div>
        <div class="toolbar-tip">
          候选默认不生效；纠正会生成新版本并使旧事实失效；敏感候选只保留证据，不允许采纳。
        </div>
      </el-card>

      <el-card class="card" shadow="never">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">
          <el-tab-pane label="生效记忆" name="active">
            <div class="toolbar">
              <div class="toolbar-tip">这里展示当前仍生效的永久记忆。</div>
              <div class="toolbar-actions">
                <el-button v-if="canWrite" type="primary" @click="openCreate">新增记忆</el-button>
              </div>
            </div>
            <el-table
              :data="memories"
              border
              v-loading="memoryLoading"
              empty-text="暂无生效记忆，可先新增或从会话中提炼候选"
            >
              <el-table-column prop="factText" label="记忆内容" min-width="280" show-overflow-tooltip/>
              <el-table-column prop="tierLabel" label="层级" width="120"/>
              <el-table-column prop="sourceLabel" label="来源" width="140"/>
              <el-table-column prop="statusLabel" label="状态" width="110"/>
              <el-table-column label="置信度" width="100">
                <template #default="scope">
                  {{ scope.row.confidence }}%
                </template>
              </el-table-column>
              <el-table-column label="更新时间" width="180">
                <template #default="scope">
                  {{ formatDate(scope.row.updatedAt) }}
                </template>
              </el-table-column>
              <el-table-column label="到期时间" width="180">
                <template #default="scope">
                  {{ formatDate(scope.row.expiresAt) }}
                </template>
              </el-table-column>
              <el-table-column v-if="canWrite" label="操作" width="150" fixed="right">
                <template #default="scope">
                  <el-button link type="primary" @click="openCorrect(scope.row)">纠正</el-button>
                  <el-button link type="danger" @click="handleRevoke(scope.row)">撤销</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="待确认候选" name="candidates">
            <div class="toolbar">
              <div class="toolbar-tip">候选必须确认后才写入长期记忆；敏感候选不允许采纳。</div>
            </div>
            <el-table
              :data="candidates"
              border
              v-loading="candidateLoading"
              empty-text="暂无待确认候选"
            >
              <el-table-column prop="factText" label="候选内容" min-width="260" show-overflow-tooltip/>
              <el-table-column label="敏感级别" width="120">
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
              <el-table-column label="置信度" width="100">
                <template #default="scope">
                  {{ scope.row.confidence }}%
                </template>
              </el-table-column>
              <el-table-column prop="sourceRef" label="来源标识" min-width="160" show-overflow-tooltip/>
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
      v-model="createVisible"
      title="新增记忆"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form label-width="88px">
        <el-form-item label="记忆内容" required>
          <el-input
            v-model="createForm.factText"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="例如：我习惯按自然月统计含税金额"
          />
        </el-form-item>
        <el-form-item label="保留天数">
          <el-input-number
            v-model="createForm.retentionDays"
            :min="1"
            :max="3650"
            :step="1"
            style="width: 160px"
          />
          <div class="form-tip">可选，范围 1-3650 天；留空使用系统默认保留期。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreate">保存</el-button>
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

.section-title {
  font-size: 15px;
  font-weight: 600;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
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
}

.form-tip {
  color: #94a3b8;
  font-size: 12px;
  margin-left: 12px;
}
</style>
