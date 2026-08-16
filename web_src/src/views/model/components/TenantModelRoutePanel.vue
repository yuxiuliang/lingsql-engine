<script setup lang="ts">
import {computed, onMounted} from 'vue';
import {ElMessage} from 'element-plus';
import {useTenantModelRoutes} from '@/composables/model-route/useTenantModelRoutes';
import TenantModelRouteTable from './TenantModelRouteTable.vue';
import TenantModelRouteToolbar from './TenantModelRouteToolbar.vue';

const {
  tenantOptions,
  rows,
  selectedTenantId,
  loading,
  saving,
  selectedCount,
  eligibleCount,
  initialize,
  selectTenant,
  refresh,
  setAuthorized,
  updatePriority,
  updateWeight,
  save
} = useTenantModelRoutes();

const tenantSelected = computed(() => selectedTenantId.value.length > 0);

const handleInitialize = async () => {
  try {
    await initialize();
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '加载租户模型授权失败'));
  }
};

const handleSelectTenant = async (tenantId: string) => {
  try {
    await selectTenant(tenantId);
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '加载租户授权失败'));
  }
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
    await save();
    ElMessage.success('租户模型授权已生效');
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '保存授权失败'));
  }
};

const resolveErrorMessage = (error: unknown, fallback: string) => {
  return error instanceof Error ? error.message : fallback;
};

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
