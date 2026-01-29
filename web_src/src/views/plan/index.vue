<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import {fetchTenants, TenantView} from '@/api/tenant';
import {
  BillingPlanCreateRequest,
  BillingPlanUpdateRequest,
  BillingPlanView,
  createPlan,
  deletePlan,
  fetchPlans,
  updatePlan
} from '@/api/plan';
import {useAuthStore} from '@/store/auth';

const authStore = useAuthStore();
const loading = ref(false);
const tenants = ref<TenantView[]>([]);
const tenantKeyword = ref('');

const planLoading = ref(false);
const planList = ref<BillingPlanView[]>([]);
const planKeyword = ref('');
const planDialogVisible = ref(false);
const planSaving = ref(false);
const editingPlanId = ref<number | null>(null);

const planForm = reactive({
  planCode: '',
  planName: '',
  status: 1,
  serviceLevel: '',
  tenantType: '',
  defaultQuotaBalance: null as number | null,
  remark: ''
});

/**
 * 判断是否系统管理员。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
const isSysAdmin = computed(() => {
  return (authStore.roles || []).some((role) => ['SYS_ADMIN', 'SUPER_ADMIN'].includes(role));
});

/**
 * 加载租户套餐数据。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
const loadTenants = async () => {
  loading.value = true;
  try {
    tenants.value = await fetchTenants();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载套餐数据失败');
  } finally {
    loading.value = false;
  }
};

/**
 * 加载套餐配置。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
const loadPlans = async () => {
  planLoading.value = true;
  try {
    planList.value = await fetchPlans();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载套餐失败');
  } finally {
    planLoading.value = false;
  }
};

const filteredTenants = computed(() => {
  const key = tenantKeyword.value.trim().toLowerCase();
  if (!key) {
    return tenants.value;
  }
  return tenants.value.filter((item) => {
    return [item.tenantName, item.tenantId, item.planName, item.serviceLevel]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(key));
  });
});

const filteredPlans = computed(() => {
  const key = planKeyword.value.trim().toLowerCase();
  if (!key) {
    return planList.value;
  }
  return planList.value.filter((plan) => {
    return [plan.planName, plan.planCode]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(key));
  });
});

const planSummary = computed(() => {
  const summaryMap = new Map<string, {planName: string; tenantCount: number; activeCount: number; totalBalance: number}>();
  tenants.value.forEach((tenant) => {
    const planName = tenant.planName?.trim() || '未设置';
    const entry = summaryMap.get(planName) || {
      planName,
      tenantCount: 0,
      activeCount: 0,
      totalBalance: 0
    };
    entry.tenantCount += 1;
    if (tenant.status === 1) {
      entry.activeCount += 1;
    }
    entry.totalBalance += tenant.quotaBalance || 0;
    summaryMap.set(planName, entry);
  });
  return Array.from(summaryMap.values()).sort((a, b) => b.tenantCount - a.tenantCount);
});

const totalTenantCount = computed(() => tenants.value.length);
const activeTenantCount = computed(() => tenants.value.filter((tenant) => tenant.status === 1).length);
const planTypeCount = computed(() => planSummary.value.length);
const totalBalance = computed(() => tenants.value.reduce((sum, item) => sum + (item.quotaBalance || 0), 0));

/**
 * 重置套餐表单。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
const resetPlanForm = () => {
  planForm.planCode = '';
  planForm.planName = '';
  planForm.status = 1;
  planForm.serviceLevel = '';
  planForm.tenantType = '';
  planForm.defaultQuotaBalance = null;
  planForm.remark = '';
};

/**
 * 新增套餐。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
const handlePlanCreate = () => {
  editingPlanId.value = null;
  resetPlanForm();
  planDialogVisible.value = true;
};

/**
 * 编辑套餐。
 *
 * @param row 套餐信息
 * @author 余炘洋
 * @date 2026-01-29
 */
const handlePlanEdit = (row: BillingPlanView) => {
  editingPlanId.value = row.id;
  planForm.planCode = row.planCode || '';
  planForm.planName = row.planName || '';
  planForm.status = row.status ?? 1;
  planForm.serviceLevel = row.serviceLevel || '';
  planForm.tenantType = row.tenantType || '';
  planForm.defaultQuotaBalance = row.defaultQuotaBalance ?? null;
  planForm.remark = row.remark || '';
  planDialogVisible.value = true;
};

const buildCreatePayload = (): BillingPlanCreateRequest => {
  return {
    planCode: planForm.planCode.trim() || undefined,
    planName: planForm.planName.trim(),
    status: planForm.status,
    serviceLevel: planForm.serviceLevel.trim() || undefined,
    tenantType: planForm.tenantType.trim() || undefined,
    defaultQuotaBalance: planForm.defaultQuotaBalance ?? undefined,
    remark: planForm.remark.trim() || undefined
  };
};

const buildUpdatePayload = (): BillingPlanUpdateRequest => {
  return {
    planCode: planForm.planCode.trim() || undefined,
    planName: planForm.planName.trim() || undefined,
    status: planForm.status,
    serviceLevel: planForm.serviceLevel.trim() || undefined,
    tenantType: planForm.tenantType.trim() || undefined,
    defaultQuotaBalance: planForm.defaultQuotaBalance ?? undefined,
    remark: planForm.remark.trim() || undefined
  };
};

/**
 * 保存套餐。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
const handlePlanSave = async () => {
  if (!planForm.planName.trim()) {
    ElMessage.warning('请填写套餐名称');
    return;
  }
  planSaving.value = true;
  try {
    if (editingPlanId.value) {
      await updatePlan(editingPlanId.value, buildUpdatePayload());
      ElMessage.success('套餐已更新');
    } else {
      await createPlan(buildCreatePayload());
      ElMessage.success('套餐已创建');
    }
    planDialogVisible.value = false;
    await loadPlans();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    planSaving.value = false;
  }
};

/**
 * 删除套餐。
 *
 * @param row 套餐信息
 * @author 余炘洋
 * @date 2026-01-29
 */
const handlePlanDelete = async (row: BillingPlanView) => {
  try {
    await ElMessageBox.confirm('确认删除该套餐？', '提示', {type: 'warning'});
    await deletePlan(row.id);
    ElMessage.success('套餐已删除');
    await loadPlans();
  } catch (error) {
    if (!isMessageBoxCancel(error)) {
      ElMessage.error(error instanceof Error ? error.message : '删除失败');
    }
  }
};

/**
 * 解析到期状态。
 *
 * @param expireTime 到期时间
 * @return 状态
 * @author 余炘洋
 * @date 2026-01-27
 */
const resolveExpireState = (expireTime?: string) => {
  if (!expireTime) {
    return 'unknown';
  }
  const parsed = new Date(expireTime);
  if (Number.isNaN(parsed.getTime())) {
    return 'unknown';
  }
  const now = new Date();
  const diffMs = parsed.getTime() - now.getTime();
  if (diffMs < 0) {
    return 'expired';
  }
  const diffDays = diffMs / (1000 * 60 * 60 * 24);
  return diffDays <= 7 ? 'soon' : 'normal';
};

const resolveStatusText = (status?: number) => {
  return status === 1 ? '启用' : '停用';
};

const resolveStatusType = (status?: number) => {
  return status === 1 ? 'success' : 'info';
};

const isMessageBoxCancel = (error: unknown) => {
  return error === 'cancel' || error === 'close';
};

onMounted(() => {
  loadPlans();
  loadTenants();
});
</script>

<template>
  <div class="page">
    <div class="page-title">套餐管理</div>
    <div class="page-desc">查看租户套餐分布、服务等级与余额概览。</div>

    <el-card class="card" shadow="never">
      <div class="section-title">套餐配置</div>
      <div class="toolbar">
        <el-input v-model="planKeyword" placeholder="搜索套餐名称/编码" style="width: 240px"/>
        <div class="toolbar-actions">
          <el-button type="primary" :disabled="!isSysAdmin" @click="handlePlanCreate">新增套餐</el-button>
          <el-button @click="loadPlans">刷新</el-button>
        </div>
        <div class="toolbar-tip" v-if="!isSysAdmin">仅系统管理员可管理套餐。</div>
      </div>
      <el-table :data="filteredPlans" border v-loading="planLoading">
        <el-table-column prop="planCode" label="套餐编码" min-width="160"/>
        <el-table-column prop="planName" label="套餐名称" min-width="160"/>
        <el-table-column prop="serviceLevel" label="服务等级" min-width="120"/>
        <el-table-column prop="tenantType" label="租户类型" min-width="120"/>
        <el-table-column prop="defaultQuotaBalance" label="默认余额" min-width="120"/>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="scope">
            <el-tag :type="resolveStatusType(scope.row.status)">
              {{ resolveStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160"/>
        <el-table-column label="操作" width="160">
          <template #default="scope">
            <el-button size="small" :disabled="!isSysAdmin" @click="handlePlanEdit(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" :disabled="!isSysAdmin" @click="handlePlanDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="card" shadow="never">
      <div class="toolbar">
        <el-input v-model="tenantKeyword" placeholder="搜索租户/套餐/等级" style="width: 240px"/>
        <el-button @click="loadTenants">刷新</el-button>
      </div>
      <div class="summary-grid">
        <div class="summary-card">
          <div class="summary-label">租户总数</div>
          <div class="summary-value">{{ totalTenantCount }}</div>
        </div>
        <div class="summary-card">
          <div class="summary-label">启用租户</div>
          <div class="summary-value">{{ activeTenantCount }}</div>
        </div>
        <div class="summary-card">
          <div class="summary-label">套餐类型数</div>
          <div class="summary-value">{{ planTypeCount }}</div>
        </div>
        <div class="summary-card">
          <div class="summary-label">余额合计</div>
          <div class="summary-value">{{ totalBalance }}</div>
        </div>
      </div>
    </el-card>

    <el-card class="card" shadow="never">
      <div class="section-title">套餐分布</div>
      <el-table :data="planSummary" border v-loading="loading">
        <el-table-column prop="planName" label="套餐" min-width="160"/>
        <el-table-column prop="tenantCount" label="租户数" min-width="120"/>
        <el-table-column prop="activeCount" label="启用租户" min-width="120"/>
        <el-table-column prop="totalBalance" label="余额合计" min-width="120"/>
      </el-table>
    </el-card>

    <el-card class="card" shadow="never">
      <div class="section-title">租户清单</div>
      <el-table :data="filteredTenants" border v-loading="loading">
        <el-table-column prop="tenantName" label="租户名称" min-width="160"/>
        <el-table-column prop="tenantId" label="租户ID" min-width="160"/>
        <el-table-column prop="planName" label="套餐" min-width="120"/>
        <el-table-column prop="serviceLevel" label="服务等级" min-width="120"/>
        <el-table-column prop="quotaBalance" label="余额" min-width="100"/>
        <el-table-column prop="expireTime" label="到期时间" min-width="160">
          <template #default="scope">
            <span v-if="!scope.row.expireTime">-</span>
            <el-tag v-else-if="resolveExpireState(scope.row.expireTime) !== 'normal'" type="danger">
              {{ scope.row.expireTime }}
            </el-tag>
            <span v-else>{{ scope.row.expireTime }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
              {{ scope.row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="planDialogVisible" :title="editingPlanId ? '编辑套餐' : '新增套餐'" width="640px" :close-on-click-modal="false">
      <el-form label-width="120px">
        <el-form-item label="套餐名称" required>
          <el-input v-model="planForm.planName" placeholder="请输入套餐名称"/>
        </el-form-item>
        <el-form-item label="套餐编码">
          <el-input v-model="planForm.planCode" placeholder="留空自动生成"/>
        </el-form-item>
        <el-form-item label="服务等级">
          <el-input v-model="planForm.serviceLevel" placeholder="如 FREE / PRO"/>
        </el-form-item>
        <el-form-item label="租户类型">
          <el-input v-model="planForm.tenantType" placeholder="如 SMB / ENTERPRISE"/>
        </el-form-item>
        <el-form-item label="默认余额">
          <el-input-number v-model="planForm.defaultQuotaBalance" :min="0" :step="100" style="width: 100%"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="planForm.status" style="width: 100%">
            <el-option label="启用" :value="1"/>
            <el-option label="停用" :value="0"/>
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="planForm.remark" type="textarea" :rows="2" placeholder="备注说明"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="planSaving" @click="handlePlanSave">保存</el-button>
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

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-tip {
  color: #94a3b8;
  font-size: 12px;
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
