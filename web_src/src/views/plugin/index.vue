<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import {ElMessage} from 'element-plus';
import {fetchTenants, TenantView} from '@/api/tenant';
import {AgentPluginView, fetchPlugins, fetchTenantPlugins, updateTenantPluginGrant} from '@/api/plugin';
import {useAuthStore} from '@/store/auth';

const authStore = useAuthStore();

const catalogLoading = ref(false);
const catalog = ref<AgentPluginView[]>([]);

const tenants = ref<TenantView[]>([]);
const activeTenantId = ref('');
const tenantLoading = ref(false);
const tenantPlugins = ref<AgentPluginView[]>([]);
const savingPluginId = ref('');

/**
 * 判断是否系统管理员。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
const isSysAdmin = computed(() => {
  return (authStore.roles || []).some((role) => ['SYS_ADMIN', 'SUPER_ADMIN'].includes(role));
});

/**
 * 加载插件目录。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
const loadCatalog = async () => {
  catalogLoading.value = true;
  try {
    catalog.value = await fetchPlugins();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载插件目录失败');
  } finally {
    catalogLoading.value = false;
  }
};

/**
 * 加载租户列表。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
const loadTenants = async () => {
  try {
    tenants.value = await fetchTenants();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载租户列表失败');
  }
};

/**
 * 加载租户插件授权视图。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
const loadTenantPlugins = async () => {
  if (!activeTenantId.value) {
    tenantPlugins.value = [];
    return;
  }
  tenantLoading.value = true;
  try {
    tenantPlugins.value = await fetchTenantPlugins(activeTenantId.value);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载租户插件授权失败');
  } finally {
    tenantLoading.value = false;
  }
};

/**
 * 切换授权：以当前行状态取反后提交，失败时重新拉取以回滚开关显示。
 *
 * @param row 插件视图
 * @author 余炘洋
 * @date 2026-10-17
 */
const handleGrantToggle = async (row: AgentPluginView) => {
  if (!activeTenantId.value || !row.grantable || !isSysAdmin.value) {
    return;
  }
  const nextGranted = row.granted !== true;
  savingPluginId.value = row.pluginId;
  try {
    await updateTenantPluginGrant(activeTenantId.value, row.pluginId, nextGranted);
    ElMessage.success(nextGranted ? `已授权 ${row.pluginId}` : `已取消授权 ${row.pluginId}`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '插件授权更新失败');
  } finally {
    savingPluginId.value = '';
    await loadTenantPlugins();
  }
};

const resolveAccessText = (access: string) => {
  return access === 'SYSTEM_ONLY' ? '系统插件' : '受控业务插件';
};

const resolveAccessType = (access: string) => {
  return access === 'SYSTEM_ONLY' ? 'info' : 'warning';
};

const resolveGrantText = (row: AgentPluginView) => {
  if (!row.grantable) {
    return '系统插件固定调用';
  }
  return row.granted === true ? '已授权' : '未授权（拒绝调用）';
};

const resolveGrantType = (row: AgentPluginView) => {
  if (!row.grantable) {
    return 'info';
  }
  return row.granted === true ? 'success' : 'danger';
};

onMounted(() => {
  loadCatalog();
  loadTenants();
});
</script>

<template>
  <div class="page">
    <div class="page-title">插件治理</div>
    <div class="page-desc">查看插件注册元数据，并按租户维护受控业务插件授权；未授权插件在执行网关层被拒绝。</div>

    <el-card class="card" shadow="never">
      <div class="toolbar">
        <div class="section-title">插件目录</div>
        <div class="toolbar-actions">
          <el-button @click="loadCatalog">刷新</el-button>
        </div>
      </div>
      <el-table :data="catalog" border v-loading="catalogLoading">
        <el-table-column prop="pluginId" label="插件标识" min-width="180"/>
        <el-table-column prop="version" label="版本" width="100"/>
        <el-table-column label="访问级别" width="140">
          <template #default="scope">
            <el-tag :type="resolveAccessType(scope.row.access)">{{ resolveAccessText(scope.row.access) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costLevel" label="成本等级" width="110"/>
        <el-table-column label="幂等" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.idempotent ? 'success' : 'info'">
              {{ scope.row.idempotent ? '幂等' : '非幂等' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="能力说明" min-width="260"/>
      </el-table>
    </el-card>

    <el-card class="card" shadow="never">
      <div class="section-title">租户插件授权</div>
      <div class="toolbar">
        <el-select
          v-model="activeTenantId"
          filterable
          clearable
          placeholder="选择租户"
          style="width: 320px"
          :disabled="!isSysAdmin"
          @change="loadTenantPlugins"
        >
          <el-option
            v-for="tenant in tenants"
            :key="tenant.tenantId"
            :label="`${tenant.tenantName}（${tenant.tenantId}）`"
            :value="tenant.tenantId"
          />
        </el-select>
        <div class="toolbar-actions">
          <el-button :disabled="!activeTenantId" @click="loadTenantPlugins">刷新</el-button>
        </div>
        <div class="toolbar-tip" v-if="!isSysAdmin">仅系统管理员可维护租户插件授权。</div>
      </div>
      <el-table v-if="activeTenantId" :data="tenantPlugins" border v-loading="tenantLoading">
        <el-table-column prop="pluginId" label="插件标识" min-width="180"/>
        <el-table-column label="访问级别" width="140">
          <template #default="scope">
            <el-tag :type="resolveAccessType(scope.row.access)">{{ resolveAccessText(scope.row.access) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costLevel" label="成本等级" width="110"/>
        <el-table-column label="授权状态" width="170">
          <template #default="scope">
            <el-tag :type="resolveGrantType(scope.row)">{{ resolveGrantText(scope.row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="授权操作" width="140">
          <template #default="scope">
            <el-switch
              :model-value="scope.row.granted === true"
              :loading="savingPluginId === scope.row.pluginId"
              :disabled="!scope.row.grantable || !isSysAdmin"
              @change="handleGrantToggle(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="description" label="能力说明" min-width="240"/>
      </el-table>
      <div class="empty-tip" v-else>请选择租户后查看授权状态。</div>
    </el-card>
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

.empty-tip {
  color: #94a3b8;
  font-size: 13px;
  padding: 12px 0;
}
</style>
