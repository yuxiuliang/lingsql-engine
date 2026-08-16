<script setup lang="ts">
import type {TenantModelRouteTenantOption} from '@/composables/model-route/useTenantModelRoutes';

interface Props {
  tenants: readonly TenantModelRouteTenantOption[];
  selectedTenantId: string;
  selectedCount: number;
  loading: boolean;
  saving: boolean;
}

interface Emits {
  selectTenant: [tenantId: string];
  refresh: [];
  save: [];
}

defineProps<Props>();
const emit = defineEmits<Emits>();
</script>

<template>
  <div class="route-toolbar">
    <div class="route-toolbar__selector">
      <span class="route-toolbar__label">业务租户</span>
      <el-select
        :model-value="selectedTenantId"
        filterable
        placeholder="选择租户"
        :loading="loading"
        @update:model-value="emit('selectTenant', $event)"
      >
        <el-option
          v-for="tenant in tenants"
          :key="tenant.tenantId"
          :label="`${tenant.tenantName}（${tenant.tenantId}）`"
          :value="tenant.tenantId"
          :disabled="tenant.status !== 1"
        />
      </el-select>
      <el-tag size="small" type="info">已授权 {{ selectedCount }} 个</el-tag>
    </div>
    <div class="route-toolbar__actions">
      <el-button :loading="loading" @click="emit('refresh')">刷新</el-button>
      <el-button
        type="primary"
        :loading="saving"
        :disabled="!selectedTenantId || selectedCount === 0"
        @click="emit('save')"
      >保存授权</el-button>
    </div>
  </div>
</template>

<style scoped>
.route-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.route-toolbar__selector,
.route-toolbar__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.route-toolbar__selector :deep(.el-select) {
  width: 320px;
}

.route-toolbar__label {
  color: var(--el-text-color-regular);
  font-size: 14px;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .route-toolbar,
  .route-toolbar__selector {
    align-items: stretch;
    flex-direction: column;
  }

  .route-toolbar__selector :deep(.el-select) {
    width: 100%;
  }
}
</style>
