<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue';
import {ElMessage} from 'element-plus';
import {useAuthStore} from '@/store/auth';
import {
  createRole,
  DataScopeRule,
  fetchPermissions,
  fetchRoles,
  PermissionView,
  RoleCreateRequest,
  RoleUpdateRequest,
  RoleView,
  updateRole
} from '@/api/rbac';

const authStore = useAuthStore();
const roles = ref<RoleView[]>([]);
const permissions = ref<PermissionView[]>([]);
const loading = ref(false);
const dialogVisible = ref(false);
const editingId = ref<number | null>(null);
const scopeTypeOptions = [
  {label: '全部数据', value: 'ALL'},
  {label: '本租户', value: 'TENANT'},
  {label: '仅本人', value: 'SELF'},
  {label: '指定组织', value: 'ORG'},
  {label: '组织及下级', value: 'ORG_SUBTREE'},
  {label: '自定义条件', value: 'CUSTOM'}
];

const form = reactive<RoleCreateRequest & RoleUpdateRequest>({
  tenantId: '',
  roleCode: '',
  roleName: '',
  status: 1,
  permIds: [],
  dataScope: {
    scopeType: 'TENANT',
    scopeColumn: '',
    scopeRule: ''
  }
});

/**
 * 创建默认数据权限表单。
 *
 * @return 默认数据权限
 * @author 余炘洋
 * @date 2026-04-11
 */
const createDefaultDataScope = (): DataScopeRule => {
  return {
    scopeType: 'TENANT',
    scopeColumn: '',
    scopeRule: ''
  };
};

/**
 * 格式化数据权限标签。
 *
 * @param scopeType 范围类型
 * @return 展示文本
 * @author 余炘洋
 * @date 2026-04-11
 */
const formatScopeType = (scopeType?: string) => {
  const mapping: Record<string, string> = {
    ALL: '全部数据',
    TENANT: '本租户',
    SELF: '仅本人',
    ORG: '指定组织',
    ORG_SUBTREE: '组织及下级',
    CUSTOM: '自定义条件'
  };
  return mapping[scopeType || 'TENANT'] || scopeType || '本租户';
};

/**
 * 规范化数据权限请求。
 *
 * @return 数据权限配置
 * @author 余炘洋
 * @date 2026-04-11
 */
const buildDataScopePayload = (): DataScopeRule => {
  const scopeType = form.dataScope?.scopeType || 'TENANT';
  const scopeColumn = form.dataScope?.scopeColumn || '';
  const scopeRule = form.dataScope?.scopeRule || '';
  return {
    scopeType,
    scopeColumn,
    scopeRule
  };
};

/**
 * 校验数据权限表单。
 *
 * @return 是否通过
 * @author 余炘洋
 * @date 2026-04-11
 */
const validateDataScope = () => {
  if (form.dataScope?.scopeType !== 'CUSTOM') {
    return true;
  }
  if (form.dataScope.scopeRule?.trim()) {
    return true;
  }
  ElMessage.warning('选择自定义条件时必须填写 SQL 条件片段');
  return false;
};

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
 * 加载角色列表。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const loadRoles = async () => {
  if (!hasTenantSelected.value) {
    roles.value = [];
    return;
  }
  loading.value = true;
  try {
    roles.value = await fetchRoles(activeTenantId.value || undefined);
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
  if (!hasTenantSelected.value) {
    ElMessage.warning('请先选择要管理的租户');
    return;
  }
  editingId.value = null;
  Object.assign(form, {
    tenantId: activeTenantId.value || '',
    roleCode: '',
    roleName: '',
    status: 1,
    permIds: [],
    dataScope: createDefaultDataScope()
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
    permIds: row.permIds || [],
    dataScope: {
      scopeType: row.dataScope?.scopeType || 'TENANT',
      scopeColumn: row.dataScope?.scopeColumn || '',
      scopeRule: row.dataScope?.scopeRule || ''
    }
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
  if (!validateDataScope()) {
    return;
  }
  try {
    if (editingId.value) {
      await updateRole(editingId.value, {
        roleName: form.roleName,
        status: form.status,
        permIds: form.permIds,
        dataScope: buildDataScopePayload()
      });
      ElMessage.success('角色已更新');
    } else {
      if (!form.roleCode) {
        ElMessage.warning('请输入角色编码');
        return;
      }
      await createRole({
        tenantId: activeTenantId.value || undefined,
        roleCode: form.roleCode,
        roleName: form.roleName,
        status: form.status,
        permIds: form.permIds,
        dataScope: buildDataScopePayload()
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

watch(
  () => activeTenantId.value,
  () => {
    dialogVisible.value = false;
    editingId.value = null;
    loadRoles();
  },
  {immediate: true}
);

loadPermissions();
</script>

<template>
  <div class="page">
    <div class="page-title">角色管理</div>
    <div class="page-desc">维护角色与权限分配。</div>

    <el-card class="card" shadow="never">
      <div class="toolbar">
        <div class="filters">
          <el-button type="primary" :disabled="!hasTenantSelected" @click="handleSearch">查询</el-button>
          <el-button :disabled="!hasTenantSelected" @click="handleRefresh">刷新</el-button>
        </div>
        <div class="toolbar-actions">
          <span v-if="isSysAdmin" class="tips">提示：请先在顶部选择租户。</span>
          <el-button type="primary" :disabled="!hasTenantSelected" @click="handleCreate">新增角色</el-button>
        </div>
      </div>

      <el-empty v-if="!hasTenantSelected" description="请先在顶部选择要管理的租户"/>
      <el-table v-else :data="roles" border v-loading="loading">
        <el-table-column prop="id" label="ID" width="80"/>
        <el-table-column prop="tenantId" label="租户" min-width="120"/>
        <el-table-column prop="roleCode" label="角色编码" min-width="160"/>
        <el-table-column prop="roleName" label="角色名称" min-width="140"/>
        <el-table-column label="数据权限" min-width="160">
          <template #default="scope">
            <el-tag type="warning" effect="plain">{{ formatScopeType(scope.row.dataScope?.scopeType) }}</el-tag>
          </template>
        </el-table-column>
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

    <el-dialog v-model="dialogVisible" title="角色信息" width="560px" :close-on-click-modal="false">
      <el-form label-width="110px">
        <el-form-item label="当前租户" v-if="isSysAdmin">
          <el-tag type="success">{{ activeTenantName || activeTenantId || '未选择' }}</el-tag>
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
        <el-form-item label="数据权限">
          <el-select v-model="form.dataScope!.scopeType" style="width: 100%">
            <el-option v-for="item in scopeTypeOptions" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.dataScope?.scopeType === 'ORG' || form.dataScope?.scopeType === 'ORG_SUBTREE'">
          <el-alert
            type="info"
            :closable="false"
            title="组织范围会基于用户绑定的组织归属生效，角色本身不直接选择组织节点。"
          />
        </el-form-item>
        <el-form-item label="范围字段">
          <el-input
            v-model="form.dataScope!.scopeColumn"
            placeholder="SELF/ORG/ORG_SUBTREE 时可填写字段名，如 creator_id 或 org_code"
          />
        </el-form-item>
        <el-form-item label="自定义规则" v-if="form.dataScope?.scopeType === 'CUSTOM'">
          <el-input
            v-model="form.dataScope!.scopeRule"
            type="textarea"
            :rows="3"
            placeholder="例如 status = 1 and dept_code in ('A', 'B')"
          />
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

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.tips {
  color: #94a3b8;
  font-size: 13px;
}
</style>
