<script setup lang="ts">
import {computed, onMounted, watch} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import {useTenantModelRoutes} from '@/composables/model-route/useTenantModelRoutes';
import {useAuthStore} from '@/store/auth';
import TenantModelRouteTable from './TenantModelRouteTable.vue';
import TenantModelRouteToolbar from './TenantModelRouteToolbar.vue';
import TenantFirstQuestionReadinessSummary from './TenantFirstQuestionReadinessSummary.vue';

const authStore = useAuthStore();
const preferredTenantId = computed(() => authStore.activeTenantId);

const {
  tenantOptions,
  rows,
  selectedTenantId,
  loading,
  saving,
  selectedCount,
  eligibleCount,
  readiness,
  syncError,
  initialize,
  refresh,
  setAuthorized,
  updatePriority,
  updateWeight,
  save
} = useTenantModelRoutes({preferredTenantId});

const tenantSelected = computed(() => selectedTenantId.value.length > 0);
const selectedTenant = computed(() => tenantOptions.value.find(
  (tenant) => tenant.tenantId === selectedTenantId.value
));

const handleInitialize = async () => {
  try {
    await initialize();
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '加载租户模型授权失败'));
  }
};

const handleSelectTenant = (tenantId: string) => {
  const tenant = tenantOptions.value.find((item) => item.tenantId === tenantId);
  authStore.setActiveTenant(tenantId, tenant?.tenantName ?? '');
};

const handleRefresh = async () => {
  try {
    await refresh();
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '刷新失败'));
  }
};

const handleSave = async () => {
  try {
    const tenant = selectedTenant.value;
    if (!tenant) {
      ElMessage.warning('请先选择业务租户');
      return;
    }
    await ElMessageBox.confirm(
      `确认完整替换租户“${tenant.tenantName}”（${tenant.tenantId}）的模型授权为 ${selectedCount.value} 个模型？`,
      '确认租户模型授权',
      {
        type: 'warning',
        confirmButtonText: '确认保存',
        cancelButtonText: '取消'
      }
    );
    await save();
    ElMessage.success('租户模型授权已生效');
  } catch (error) {
    if (!isMessageBoxCancel(error)) {
      ElMessage.error(resolveErrorMessage(error, '保存授权失败'));
    }
  }
};

const resolveErrorMessage = (error: unknown, fallback: string) => {
  return error instanceof Error ? error.message : fallback;
};

const isMessageBoxCancel = (error: unknown) => error === 'cancel' || error === 'close';

watch(syncError, (error) => {
  if (error) {
    ElMessage.error(resolveErrorMessage(error, '加载租户授权失败'));
  }
});

onMounted(handleInitialize);
</script>

<template>
  <section class="tenant-model-route-panel">
    <TenantModelRouteToolbar
      :tenants="tenantOptions"
      :selected-tenant-id="selectedTenantId"
      :selected-count="selectedCount"
      :loading="loading"
      :saving="saving"
      @select-tenant="handleSelectTenant"
      @refresh="handleRefresh"
      @save="handleSave"
    />

    <el-alert
      v-if="eligibleCount === 0"
      title="没有可授权模型，请先在“健康状态”完成验证并启用模型"
      type="warning"
      :closable="false"
      show-icon
      class="tenant-model-route-panel__alert"
    />

    <TenantFirstQuestionReadinessSummary :readiness="readiness" :loading="loading"/>

    <TenantModelRouteTable
      :rows="rows"
      :loading="loading"
      :tenant-selected="tenantSelected"
      @toggle="setAuthorized"
      @update-priority="updatePriority"
      @update-weight="updateWeight"
    />
  </section>
</template>

<style scoped>
.tenant-model-route-panel__alert {
  margin-bottom: 12px;
}
</style>
