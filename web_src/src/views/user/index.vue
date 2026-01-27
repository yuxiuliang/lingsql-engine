<script setup lang="ts">
import {computed, reactive, ref} from 'vue';
import {ElMessage} from 'element-plus';
import {useAuthStore} from '@/store/auth';
import {
  createUser,
  fetchRoles,
  fetchUsers,
  resetUserPassword,
  RoleView,
  updateUser,
  UserCreateRequest,
  UserUpdateRequest,
  UserView
} from '@/api/rbac';

const authStore = useAuthStore();
const loading = ref(false);
const dialogVisible = ref(false);
const passwordVisible = ref(false);
const editingId = ref<number | null>(null);
const users = ref<UserView[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const roleOptions = ref<RoleView[]>([]);

const searchForm = reactive({
  keyword: ''
});

const form = reactive<UserCreateRequest & UserUpdateRequest>({
  tenantId: '',
  username: '',
  password: '',
  nickname: '',
  status: 1,
  isExperience: 0,
  roleIds: []
});

const resetForm = reactive({
  newPassword: ''
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
 * 当前选择租户ID。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const activeTenantId = computed(() => {
  return isSysAdmin.value ? authStore.activeTenantId : authStore.tenantId;
});

/**
 * 当前租户名称。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const activeTenantName = computed(() => {
  return isSysAdmin.value ? authStore.activeTenantName : authStore.tenantName;
});

/**
 * 判断是否已选择租户。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const hasTenantSelected = computed(() => {
  return !isSysAdmin.value || !!authStore.activeTenantId;
});

/**
 * 加载用户列表。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const loadUsers = async () => {
  if (!hasTenantSelected.value) {
    users.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await fetchUsers({
      tenantId: activeTenantId.value || undefined,
      keyword: searchForm.keyword,
      page: page.value,
      size: size.value
    });
    users.value = result.list || [];
    total.value = result.total || 0;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载用户失败');
  } finally {
    loading.value = false;
  }
};

/**
 * 加载角色列表。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const loadRoles = async () => {
  try {
    if (!hasTenantSelected.value) {
      roleOptions.value = [];
      return;
    }
    roleOptions.value = await fetchRoles(activeTenantId.value || undefined);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载角色失败');
  }
};

/**
 * 新增用户。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleCreate = async () => {
  if (!hasTenantSelected.value) {
    ElMessage.warning('请先选择要管理的租户');
    return;
  }
  editingId.value = null;
  Object.assign(form, {
    tenantId: activeTenantId.value || '',
    username: '',
    password: '',
    nickname: '',
    status: 1,
    isExperience: 0,
    roleIds: []
  });
  await loadRoles();
  dialogVisible.value = true;
};

/**
 * 编辑用户。
 *
 * @param row 用户信息
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleEdit = async (row: UserView) => {
  editingId.value = row.id;
  Object.assign(form, {
    tenantId: row.tenantId,
    username: row.username,
    password: '',
    nickname: row.nickname || '',
    status: row.status ?? 1,
    isExperience: row.isExperience ?? 0,
    roleIds: row.roleIds || []
  });
  await loadRoles();
  dialogVisible.value = true;
};

/**
 * 保存用户信息。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleSave = async () => {
  if (!form.username) {
    ElMessage.warning('请输入账号');
    return;
  }
  if (!editingId.value && !form.password) {
    ElMessage.warning('请输入初始密码');
    return;
  }
  try {
    if (editingId.value) {
      await updateUser(editingId.value, {
        nickname: form.nickname,
        status: form.status,
        isExperience: form.isExperience,
        roleIds: form.roleIds
      });
      ElMessage.success('用户已更新');
    } else {
      await createUser({
        tenantId: activeTenantId.value || undefined,
        username: form.username,
        password: form.password,
        nickname: form.nickname,
        status: form.status,
        isExperience: form.isExperience,
        roleIds: form.roleIds
      });
      ElMessage.success('用户已创建');
    }
    dialogVisible.value = false;
    await loadUsers();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  }
};

/**
 * 打开重置密码对话框。
 *
 * @param row 用户信息
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleResetPassword = (row: UserView) => {
  resetForm.newPassword = '';
  editingId.value = row.id;
  passwordVisible.value = true;
};

/**
 * 提交密码重置。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleSubmitReset = async () => {
  if (!editingId.value) {
    return;
  }
  if (!resetForm.newPassword) {
    ElMessage.warning('请输入新密码');
    return;
  }
  try {
    await resetUserPassword(editingId.value, {newPassword: resetForm.newPassword});
    ElMessage.success('密码已重置');
    passwordVisible.value = false;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重置失败');
  }
};

/**
 * 格式化状态文本。
 *
 * @param status 状态
 * @return 文本
 * @author 余炘洋
 * @date 2026-01-26
 */
const resolveStatusText = (status?: number): string => {
  return status === 0 ? '停用' : '启用';
};

/**
 * 格式化状态样式。
 *
 * @param status 状态
 * @return 类型
 * @author 余炘洋
 * @date 2026-01-26
 */
const resolveStatusType = (status?: number) => {
  return status === 0 ? 'info' : 'success';
};

/**
 * 处理分页大小变化。
 *
 * @param val 新大小
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleSizeChange = (val: number) => {
  size.value = val;
  loadUsers();
};

/**
 * 处理页码变化。
 *
 * @param val 新页码
 * @author 余炘洋
 * @date 2026-01-26
 */
const handlePageChange = (val: number) => {
  page.value = val;
  loadUsers();
};

/**
 * 触发查询。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleSearch = () => {
  page.value = 1;
  loadUsers();
};

/**
 * 刷新列表。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleRefresh = () => {
  loadUsers();
};

loadUsers();
</script>

<template>
  <div class="page">
    <div class="page-title">用户管理</div>
    <div class="page-desc">维护租户用户账号与角色绑定。</div>

    <el-card class="card" shadow="never">
      <div class="toolbar">
        <div class="filters">
          <el-input v-model="searchForm.keyword" placeholder="账号关键词" style="width: 200px"/>
          <el-button type="primary" :disabled="!hasTenantSelected" @click="handleSearch">查询</el-button>
          <el-button :disabled="!hasTenantSelected" @click="handleRefresh">刷新</el-button>
        </div>
        <div class="toolbar-actions">
          <span v-if="isSysAdmin" class="tips">提示：请先在顶部选择租户。</span>
          <el-button type="primary" :disabled="!hasTenantSelected" @click="handleCreate">新增用户</el-button>
        </div>
      </div>

      <el-empty v-if="!hasTenantSelected" description="请先在顶部选择要管理的租户"/>
      <el-table v-else :data="users" border v-loading="loading">
        <el-table-column prop="id" label="ID" width="80"/>
        <el-table-column prop="tenantId" label="租户" min-width="120"/>
        <el-table-column prop="username" label="账号" min-width="120"/>
        <el-table-column prop="nickname" label="昵称" min-width="120"/>
        <el-table-column prop="isExperience" label="体验账号" width="100">
          <template #default="scope">
            <el-tag v-if="scope.row.isExperience === 1" type="warning">是</el-tag>
            <span v-else>否</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="resolveStatusType(scope.row.status)">{{ resolveStatusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginTime" label="最近登录" min-width="160"/>
        <el-table-column label="操作" width="220">
          <template #default="scope">
            <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button size="small" type="warning" @click="handleResetPassword(scope.row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="page"
          :page-size="size"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="用户信息" width="520px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="当前租户" v-if="isSysAdmin">
          <el-tag type="success">{{ activeTenantName || activeTenantId || '未选择' }}</el-tag>
        </el-form-item>
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="请输入账号" :disabled="!!editingId"/>
        </el-form-item>
        <el-form-item label="初始密码" v-if="!editingId">
          <el-input v-model="form.password" placeholder="请输入密码" show-password/>
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="请输入昵称"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option :value="1" label="启用"/>
            <el-option :value="0" label="停用"/>
          </el-select>
        </el-form-item>
        <el-form-item label="体验账号">
          <el-select v-model="form.isExperience" style="width: 100%">
            <el-option :value="1" label="是"/>
            <el-option :value="0" label="否"/>
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple filterable style="width: 100%">
            <el-option v-for="role in roleOptions" :key="role.id" :value="role.id" :label="`${role.roleName} (${role.roleCode})`"/>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordVisible" title="重置密码" width="420px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="新密码">
          <el-input v-model="resetForm.newPassword" show-password placeholder="请输入新密码"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitReset">确认</el-button>
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
  flex-wrap: wrap;
  align-items: center;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.tips {
  color: #94a3b8;
  font-size: 13px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
