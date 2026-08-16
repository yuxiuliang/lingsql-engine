<script setup lang="ts">
import type {TenantModelRouteRow} from '@/composables/model-route/useTenantModelRoutes';

interface Props {
  rows: readonly TenantModelRouteRow[];
  loading: boolean;
  tenantSelected: boolean;
}

interface Emits {
  toggle: [modelConfigId: number, authorized: boolean];
  updatePriority: [modelConfigId: number, priority: number];
  updateWeight: [modelConfigId: number, weight: number];
}

defineProps<Props>();
const emit = defineEmits<Emits>();

const resolveStatusType = (row: TenantModelRouteRow) => row.eligible ? 'success' : 'info';
</script>

<template>
  <el-table
    :data="rows"
    border
    size="small"
    v-loading="loading"
    empty-text="暂无模型配置"
  >
    <el-table-column label="授权" width="72" align="center">
      <template #default="scope">
        <el-checkbox
          :model-value="scope.row.authorized"
          :disabled="!tenantSelected || (!scope.row.eligible && !scope.row.authorized)"
          @update:model-value="emit('toggle', scope.row.modelConfigId, Boolean($event))"
        />
      </template>
    </el-table-column>
    <el-table-column prop="providerName" label="供应商" min-width="120"/>
    <el-table-column prop="modelName" label="模型" min-width="150"/>
    <el-table-column prop="modelVersion" label="版本" min-width="120"/>
    <el-table-column label="状态" width="100">
      <template #default="scope">
        <el-tag size="small" :type="resolveStatusType(scope.row)">{{ scope.row.statusText }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="优先级" width="130">
      <template #default="scope">
        <el-input-number
          :model-value="scope.row.priority"
          :min="1"
          :max="100"
          :disabled="!scope.row.authorized"
          controls-position="right"
          size="small"
          @update:model-value="emit('updatePriority', scope.row.modelConfigId, Number($event))"
        />
      </template>
    </el-table-column>
    <el-table-column label="权重" width="130">
      <template #default="scope">
        <el-input-number
          :model-value="scope.row.weight"
          :min="1"
          :max="1000"
          :disabled="!scope.row.authorized"
          controls-position="right"
          size="small"
          @update:model-value="emit('updateWeight', scope.row.modelConfigId, Number($event))"
        />
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
:deep(.el-input-number) {
  width: 104px;
}
</style>
