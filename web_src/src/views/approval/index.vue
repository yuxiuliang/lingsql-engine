<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import {fetchTenants, TenantView} from '@/api/tenant';
import {
  AgentApprovalResumeView,
  AgentApprovalView,
  decideApproval,
  fetchApprovals,
  resumeApproval
} from '@/api/approval';
import {useAuthStore} from '@/store/auth';

const authStore = useAuthStore();

const loading = ref(false);
const approvals = ref<AgentApprovalView[]>([]);

const tenants = ref<TenantView[]>([]);
const filterTenantId = ref('');
const filterStatus = ref('PENDING');

const dialogVisible = ref(false);
const dialogRow = ref<AgentApprovalView | null>(null);
const dialogApproved = ref(true);
const dialogComment = ref('');
const saving = ref(false);

const resumingId = ref<number | null>(null);
const resultVisible = ref(false);
const resumeResult = ref<AgentApprovalResumeView | null>(null);

/**
 * 判断是否系统管理员（仅系统管理员可裁决与续跑）。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
const isSysAdmin = computed(() => {
  return (authStore.roles || []).some((role) => ['SYS_ADMIN', 'SUPER_ADMIN'].includes(role));
});

/**
 * 加载人工审批节点列表。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
const loadApprovals = async () => {
  loading.value = true;
  try {
    approvals.value = await fetchApprovals({
      tenantId: filterTenantId.value || undefined,
      status: filterStatus.value || undefined
    });
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载人工审批列表失败');
  } finally {
    loading.value = false;
  }
};

/**
 * 加载租户列表用于筛选。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
const loadTenants = async () => {
  if (!isSysAdmin.value) {
    return;
  }
  try {
    tenants.value = await fetchTenants();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载租户列表失败');
  }
};

/**
 * 打开裁决弹窗。
 *
 * @param row      审批节点
 * @param approved 预置结论
 * @author 余炘洋
 * @date 2026-10-17
 */
const openDecideDialog = (row: AgentApprovalView, approved: boolean) => {
  dialogRow.value = row;
  dialogApproved.value = approved;
  dialogComment.value = '';
  dialogVisible.value = true;
};

/**
 * 提交审批结论。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
const submitDecision = async () => {
  if (!dialogRow.value) {
    return;
  }
  saving.value = true;
  try {
    await decideApproval(dialogRow.value.id, dialogApproved.value, dialogComment.value);
    ElMessage.success(dialogApproved.value ? '已通过该审批节点' : '已驳回该审批节点');
    dialogVisible.value = false;
    await loadApprovals();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交审批结论失败');
  } finally {
    saving.value = false;
  }
};

/**
 * 审批通过后发起续跑：续跑会重新完整经过安全查询模块。
 *
 * @param row 审批节点
 * @author 余炘洋
 * @date 2026-10-17
 */
const handleResume = async (row: AgentApprovalView) => {
  if (!isSysAdmin.value) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认对执行 ${row.executionId} 发起续跑？续跑会重新完整经过 SQL 安全执行链。`,
      '提示',
      {type: 'warning'}
    );
  } catch {
    return;
  }
  resumingId.value = row.id;
  try {
    resumeResult.value = await resumeApproval(row.id);
    resultVisible.value = true;
    ElMessage.success('续跑已完成');
    await loadApprovals();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '续跑失败');
  } finally {
    resumingId.value = null;
  }
};

const resolveStatusText = (status: string) => {
  if (status === 'APPROVED') {
    return '已通过';
  }
  if (status === 'REJECTED') {
    return '已驳回';
  }
  return '待审批';
};

const resolveStatusType = (status: string) => {
  if (status === 'APPROVED') {
    return 'success';
  }
  if (status === 'REJECTED') {
    return 'danger';
  }
  return 'warning';
};

const resolveResumeStatusText = (status?: string) => {
  if (status === 'SUCCEEDED') {
    return '执行成功';
  }
  if (status === 'SUCCEEDED_WITH_DEGRADATION') {
    return '执行成功（分析降级）';
  }
  if (status === 'FAILED') {
    return '执行失败';
  }
  return status || '-';
};

onMounted(() => {
  loadApprovals();
  loadTenants();
});
</script>

<template>
  <div class="page">
    <div class="page-title">人工审批</div>
    <div class="page-desc">
      查看待审批的 Agent 执行，裁决通过后可发起续跑；续跑只能从审批节点重新进入，并重新完整经过 SQL 安全执行链。
    </div>

    <el-card class="card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-filters">
          <el-select
            v-model="filterTenantId"
            filterable
            clearable
            placeholder="全部租户"
            style="width: 260px"
            :disabled="!isSysAdmin"
            @change="loadApprovals"
          >
            <el-option
              v-for="tenant in tenants"
              :key="tenant.tenantId"
              :label="`${tenant.tenantName}（${tenant.tenantId}）`"
              :value="tenant.tenantId"
            />
          </el-select>
          <el-select v-model="filterStatus" placeholder="全部状态" clearable style="width: 160px" @change="loadApprovals">
            <el-option label="待审批" value="PENDING"/>
            <el-option label="已通过" value="APPROVED"/>
            <el-option label="已驳回" value="REJECTED"/>
          </el-select>
        </div>
        <div class="toolbar-actions">
          <el-button @click="loadApprovals">刷新</el-button>
        </div>
      </div>
      <div class="toolbar-tip" v-if="!isSysAdmin">仅系统管理员可裁决与续跑审批节点。</div>

      <el-table :data="approvals" border v-loading="loading">
        <el-table-column prop="id" label="编号" width="80"/>
        <el-table-column prop="tenantId" label="租户" min-width="120"/>
        <el-table-column prop="executionId" label="执行标识" min-width="220"/>
        <el-table-column prop="stage" label="恢复点阶段" min-width="170"/>
        <el-table-column label="状态" width="110">
          <template #default="scope">
            <el-tag :type="resolveStatusType(scope.row.status)">{{ resolveStatusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="approverId" label="审批人" width="110"/>
        <el-table-column prop="comment" label="审批意见" min-width="160"/>
        <el-table-column prop="createdAt" label="发起时间" min-width="170"/>
        <el-table-column prop="decidedAt" label="裁决时间" min-width="170"/>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="scope">
            <template v-if="scope.row.status === 'PENDING'">
              <el-button link type="primary" :disabled="!isSysAdmin" @click="openDecideDialog(scope.row, true)">
                通过
              </el-button>
              <el-button link type="danger" :disabled="!isSysAdmin" @click="openDecideDialog(scope.row, false)">
                驳回
              </el-button>
            </template>
            <el-button
              v-else-if="scope.row.status === 'APPROVED'"
              link
              type="primary"
              :loading="resumingId === scope.row.id"
              :disabled="!isSysAdmin"
              @click="handleResume(scope.row)"
            >
              续跑
            </el-button>
            <span v-else class="empty-tip">已驳回</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="empty-tip" v-if="!loading && approvals.length === 0">当前条件下没有审批节点。</div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogApproved ? '通过审批' : '驳回审批'" width="520px">
      <div class="dialog-line">执行标识：{{ dialogRow?.executionId }}</div>
      <div class="dialog-line">恢复点阶段：{{ dialogRow?.stage }}</div>
      <el-input
        v-model="dialogComment"
        type="textarea"
        :rows="3"
        maxlength="512"
        show-word-limit
        placeholder="填写审批意见（可选）"
      />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitDecision">确认提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultVisible" title="续跑结果" width="640px">
      <div class="dialog-line">运行状态：{{ resolveResumeStatusText(resumeResult?.status) }}</div>
      <div class="dialog-line">耗时：{{ resumeResult?.durationMs ?? 0 }} ms</div>
      <div class="dialog-block">
        <div class="dialog-block-title">业务答复</div>
        <div class="dialog-block-body">{{ resumeResult?.answer || '（无）' }}</div>
      </div>
      <div class="dialog-block">
        <div class="dialog-block-title">最终 SQL</div>
        <div class="dialog-block-body">{{ resumeResult?.sql || '（未生成）' }}</div>
      </div>
      <template #footer>
        <el-button type="primary" @click="resultVisible = false">关闭</el-button>
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
  margin-bottom: 12px;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-filters {
  display: flex;
  align-items: center;
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
  margin-bottom: 12px;
}

.empty-tip {
  color: #94a3b8;
  font-size: 13px;
  padding: 12px 0;
}

.dialog-line {
  color: #475569;
  font-size: 13px;
  margin-bottom: 8px;
}

.dialog-block {
  margin-top: 12px;
}

.dialog-block-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
}

.dialog-block-body {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
