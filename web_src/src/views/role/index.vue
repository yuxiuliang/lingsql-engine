<script setup lang="ts">
import {computed, reactive, ref} from 'vue';
import {ElMessage} from 'element-plus';
import {useAuthStore} from '@/store/auth';
import {
  PermissionView,
  RoleCreateRequest,
  RoleUpdateRequest,
  RoleView,
  createRole,
  fetchPermissions,
  fetchRoles,
  updateRole
} from '@/api/rbac';

const authStore = useAuthStore();
const roles = ref<RoleView[]>([]);
const permissions = ref<PermissionView[]>([]);
const loading = ref(false);
const dialogVisible = ref(false);
const editingId = ref<number | null>(null);

const searchForm = reactive({
  tenantId: ''
});

const form = reactive<RoleCreateRequest & RoleUpdateRequest>({
  tenantId: '',
  roleCode: '',
  roleName: '',
  status: 1,
  permIds: []
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
 * 加载角色列表。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const loadRoles = async () => {
  loading.value = true;
  try {
    const tenantId = isSysAdmin.value ? searchForm.tenantId || undefined : undefined;
    roles.value = await fetchRoles(tenantId);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载角色失败');
  } finally {
    loading.value = false;
  }
};

/**
 * 加载权限列表。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const loadPermissions = async () => {
  try {
    permissions.value = await fetchPermissions();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载权限失败');
  }
};

/**
 * 新增角色。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleCreate = () => {
  editingId.value = null;
  Object.assign(form, {
    tenantId: '',
    roleCode: '',
    roleName: '',
    status: 1,
    permIds: []
  });
  dialogVisible.value = true;
};

/**
 * 编辑角色。
 *
 * @param row 角色信息
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleEdit = (row: RoleView) => {
  editingId.value = row.id;
  Object.assign(form, {
    tenantId: row.tenantId || '',
    roleCode: row.roleCode,
    roleName: row.roleName,
    status: row.status ?? 1,
    permIds: row.permIds || []
  });
  dialogVisible.value = true;
};

/**
 * 保存角色。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleSave = async () => {
  if (!form.roleName) {
    ElMessage.warning('请输入角色名称');
    return;
  }
  try {
    if (editingId.value) {
      await updateRole(editingId.value, {
        roleName: form.roleName,
        status: form.status,
        permIds: form.permIds
      });
      ElMessage.success('角色已更新');
    } else {
      if (!form.roleCode) {
        ElMessage.warning('请输入角色编码');
        return;
      }
      await createRole({
        tenantId: form.tenantId || undefined,
        roleCode: form.roleCode,
        roleName: form.roleName,
        status: form.status,
        permIds: form.permIds
      });
      ElMessage.success('角色已创建');
    }
    dialogVisible.value = false;
    await loadRoles();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  }
};

/**
 * 处理查询。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleSearch = () => {
  loadRoles();
};

/**
 * 处理刷新。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleRefresh = () => {
  loadRoles();
};

loadRoles();
loadPermissions();
</script>

<template>
  <div class="page">
    <div class="page-title">角色管理</div>
    <div class="page-desc">维护角色与权限分配。</div>

    <el-card class="card" shadow="never">
      <div class="toolbar">
        <div class="filters">
          <el-input v-if="isSysAdmin" v-model="searchForm.tenantId" placeholder="租户ID" style="width: 180px"/>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleRefresh">刷新</el-button>
        </div>
        <el-button type="primary" @click="handleCreate">新增角色</el-button>
      </div>

      <el-table :data="roles" border v-loading="loading">
        <el-table-column prop="id" label="ID" width="80"/>
        <el-table-column prop="tenantId" label="租户" min-width="120"/>
        <el-table-column prop="roleCode" label="角色编码" min-width="160"/>
        <el-table-column prop="roleName" label="角色名称" min-width="140"/>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 0 ? 'info' : 'success'">{{ scope.row.status === 0 ? '停用' : '启用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="角色信息" width="560px">
      <el-form label-width="110px">
        <el-form-item label="租户ID" v-if="isSysAdmin">
          <el-input v-model="form.tenantId" placeholder="留空表示全局"/>
        </el-form-item>
        <el-form-item label="角色编码">
          <el-input v-model="form.roleCode" placeholder="例如：ROLE_SALES" :disabled="!!editingId"/>
        </el-form-item>
        <el-form-item label="角色名称">
          <el-input v-model="form.roleName" placeholder="例如：销售"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option :value="1" label="启用"/>
            <el-option :value="0" label="停用"/>
          </el-select>
        </el-form-item>
        <el-form-item label="权限">
          <el-select v-model="form.permIds" multiple filterable style="width: 100%">
            <el-option
              v-for="perm in permissions"
              :key="perm.id"
              :value="perm.id"
              :label="`${perm.permName} (${perm.permCode})`"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
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
}

.toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.filters {
  display: flex;
  gap: 12px;
  align-items: center;
}
</style>
