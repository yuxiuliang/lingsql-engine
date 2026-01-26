<script setup lang="ts">
import {computed, reactive, ref} from 'vue';
import {ElMessage} from 'element-plus';
import {useAuthStore} from '@/store/auth';
import {PermissionView, TenantPermissionRequest, fetchPermissions, fetchTenantPermissionPool, updateTenantPermissionPool} from '@/api/rbac';
import {fetchTenantConfigs, TenantDbConfig} from '@/api/config';

const authStore = useAuthStore();
const permissions = ref<PermissionView[]>([]);
const tenantConfigs = ref<TenantDbConfig[]>([]);
const loading = ref(false);
const poolLoading = ref(false);
const poolSaving = ref(false);

const poolForm = reactive({
  tenantId: '',
  permIds: [] as number[]
});

/**
 * 判断是否系统管理员。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const isSysAdmin = computed(() => {
  return (authStore.roles || []).some((role) => ['SYS_ADMIN', 'SUPER_ADMIN'].includes(role));
});

/**
 * 加载权限列表。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const loadPermissions = async () => {
  loading.value = true;
  try {
    permissions.value = await fetchPermissions();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载权限失败');
  } finally {
    loading.value = false;
  }
};

/**
 * 加载租户列表。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const loadTenants = async () => {
  if (!isSysAdmin.value) {
    return;
  }
  try {
    tenantConfigs.value = await fetchTenantConfigs();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载租户失败');
  }
};

/**
 * 加载租户权限池。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const loadTenantPool = async () => {
  if (!poolForm.tenantId) {
    ElMessage.warning('请选择租户');
    return;
  }
  poolLoading.value = true;
  try {
    const pool = await fetchTenantPermissionPool(poolForm.tenantId);
    poolForm.permIds = pool.map((item) => item.id);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载权限池失败');
  } finally {
    poolLoading.value = false;
  }
};

/**
 * 保存租户权限池。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleSavePool = async () => {
  if (!poolForm.tenantId) {
    ElMessage.warning('请选择租户');
    return;
  }
  const payload: TenantPermissionRequest = {permIds: poolForm.permIds};
  poolSaving.value = true;
  try {
    await updateTenantPermissionPool(poolForm.tenantId, payload);
    ElMessage.success('权限池已更新');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新失败');
  } finally {
    poolSaving.value = false;
  }
};

loadPermissions();
loadTenants();
</script>

<template>
  <div class="page">
    <div class="page-title">权限管理</div>
    <div class="page-desc">查看权限清单并配置租户权限池。</div>

    <el-card class="card" shadow="never">
      <div class="toolbar">
        <div></div>
        <el-button @click="loadPermissions">刷新</el-button>
      </div>
      <el-table :data="permissions" border v-loading="loading">
        <el-table-column prop="id" label="ID" width="80"/>
        <el-table-column prop="permCode" label="权限编码" min-width="200"/>
        <el-table-column prop="permName" label="权限名称" min-width="160"/>
        <el-table-column prop="permType" label="类型" width="120"/>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 0 ? 'info' : 'success'">{{ scope.row.status === 0 ? '停用' : '启用' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="isSysAdmin" class="card" shadow="never" style="margin-top: 16px;">
      <div class="section-title">租户权限池配置</div>
      <div class="pool-form">
        <el-select v-model="poolForm.tenantId" placeholder="请选择租户" style="width: 220px" @change="loadTenantPool">
          <el-option v-for="tenant in tenantConfigs" :key="tenant.id" :label="tenant.tenantId" :value="tenant.tenantId"/>
        </el-select>
        <el-button :loading="poolLoading" @click="loadTenantPool">加载权限池</el-button>
      </div>
      <el-form label-width="110px" class="pool-form-detail">
        <el-form-item label="权限范围">
          <el-select v-model="poolForm.permIds" multiple filterable style="width: 100%">
            <el-option v-for="perm in permissions" :key="perm.id" :label="`${perm.permName} (${perm.permCode})`" :value="perm.id"/>
          </el-select>
        </el-form-item>
      </el-form>
      <div class="actions">
        <el-button type="primary" :loading="poolSaving" @click="handleSavePool">保存权限池</el-button>
      </div>
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
}

.toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-title {
  font-weight: 600;
  margin-bottom: 12px;
}

.pool-form {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.pool-form-detail {
  margin-top: 8px;
}

.actions {
  display: flex;
  justify-content: flex-end;
}
</style>
