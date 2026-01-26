<script setup lang="ts">
import {computed, nextTick, reactive, ref, watch} from 'vue';
import type {ElTree} from 'element-plus';
import {ElMessage} from 'element-plus';
import {useAuthStore} from '@/store/auth';
import {
  fetchPermissions,
  fetchTenantPermissionPool,
  PermissionView,
  TenantPermissionRequest,
  updateTenantPermissionPool
} from '@/api/rbac';
import {fetchTenants, TenantView} from '@/api/tenant';

const authStore = useAuthStore();
const permissions = ref<PermissionView[]>([]);
const tenants = ref<TenantView[]>([]);
const loading = ref(false);
const tenantLoading = ref(false);
const poolLoading = ref(false);
const poolSaving = ref(false);
const treeRef = ref<InstanceType<typeof ElTree> | null>(null);
const treeFilterText = ref('');
const selectedPermIds = ref<number[]>([]);

const poolForm = reactive({
  tenantId: ''
});

/**
 * 权限树节点定义。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
interface PermissionTreeNode {
  id?: number;
  permCode?: string;
  permName?: string;
  permType?: string;
  status?: number;
  nodeKey: string | number;
  children?: PermissionTreeNode[];
}

/**
 * 权限树属性配置。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const treeProps = {
  label: 'permName',
  children: 'children'
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
  tenantLoading.value = true;
  try {
    tenants.value = await fetchTenants();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载租户失败');
  } finally {
    tenantLoading.value = false;
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
    selectedPermIds.value = pool.map((item) => item.id);
    await applyTreeCheckedKeys();
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
  const checkedKeys = treeRef.value?.getCheckedKeys() ?? [];
  const permIds = checkedKeys.map((key) => Number(key)).filter((value) => Number.isFinite(value));
  const payload: TenantPermissionRequest = {permIds};
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

/**
 * 刷新权限数据并同步租户权限池。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleRefreshPermissions = async () => {
  await loadPermissions();
  if (poolForm.tenantId) {
    await loadTenantPool();
  }
};

/**
 * 应用租户权限池选中状态。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const applyTreeCheckedKeys = async () => {
  await nextTick();
  treeRef.value?.setCheckedKeys(selectedPermIds.value);
};

/**
 * 处理租户选中事件。
 *
 * @param tenant 租户信息
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleTenantSelect = (tenant: TenantView) => {
  poolForm.tenantId = tenant.tenantId;
  selectedPermIds.value = [];
  treeRef.value?.setCheckedKeys([]);
  loadTenantPool();
};

/**
 * 处理权限树搜索过滤。
 *
 * @param keyword 过滤关键字
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleTreeFilter = (keyword: string) => {
  treeRef.value?.filter(keyword);
};

/**
 * 权限列表更新后同步树选中状态。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const handlePermissionSync = async () => {
  if (!selectedPermIds.value.length) {
    return;
  }
  await applyTreeCheckedKeys();
};

/**
 * 权限树节点过滤规则。
 *
 * @param keyword 过滤关键字
 * @param data 节点数据
 * @return 是否匹配
 * @author 余炘洋
 * @date 2026-01-26
 */
const filterPermissionNode = (keyword: string, data: PermissionTreeNode) => {
  if (!keyword) {
    return true;
  }
  return (data.permName || '').includes(keyword);
};

/**
 * 转换权限类型为标签文案。
 *
 * @param permType 权限类型
 * @return 标签名称
 * @author 余炘洋
 * @date 2026-01-26
 */
const resolvePermissionTag = (permType?: string) => {
  const mapping: Record<string, string> = {
    MENU: '菜单',
    PAGE: '页面',
    BUTTON: '按钮',
    API: '页面',
    DATA: '数据'
  };
  return permType ? (mapping[permType] || permType) : '';
};

/**
 * 转换权限类型为标签样式。
 *
 * @param permType 权限类型
 * @return 标签类型
 * @author 余炘洋
 * @date 2026-01-26
 */
const resolvePermissionTagType = (permType?: string) => {
  const mapping: Record<string, string> = {
    MENU: 'success',
    PAGE: 'warning',
    BUTTON: 'info',
    API: 'warning',
    DATA: 'danger'
  };
  return permType ? (mapping[permType] || 'info') : 'info';
};

/**
 * 扁平权限列表转树形结构。
 *
 * @param list 权限列表
 * @return 树形节点
 * @author 余炘洋
 * @date 2026-01-26
 */
const listToTree = (list: PermissionView[]): PermissionTreeNode[] => {
  const sortedList = [...list].sort((a, b) => a.permCode.localeCompare(b.permCode));
  const nodeMap = new Map<string, PermissionTreeNode>();
  const roots: PermissionTreeNode[] = [];

  /**
   * 获取或创建权限节点。
   *
   * @param code 权限编码
   * @param source 权限对象
   * @return 权限节点
   * @author 余炘洋
   * @date 2026-01-26
   */
  const ensureNode = (code: string, source?: PermissionView): PermissionTreeNode => {
    const existing = nodeMap.get(code);
    if (existing) {
      if (source) {
        existing.id = source.id;
        existing.permCode = source.permCode;
        existing.permName = source.permName;
        existing.permType = source.permType;
        existing.status = source.status;
        existing.nodeKey = source.id;
      }
      return existing;
    }
    const node: PermissionTreeNode = {
      id: source?.id,
      permCode: source?.permCode || code,
      permName: source?.permName || code,
      permType: source?.permType,
      status: source?.status,
      nodeKey: source?.id ?? `virtual-${code}`,
      children: []
    };
    nodeMap.set(code, node);
    return node;
  };

  /**
   * 绑定父子关系。
   *
   * @param parent 父节点
   * @param child 子节点
   * @author 余炘洋
   * @date 2026-01-26
   */
  const attachChild = (parent: PermissionTreeNode, child: PermissionTreeNode) => {
    if (!parent.children) {
      parent.children = [];
    }
    if (!parent.children.some((item) => item.nodeKey === child.nodeKey)) {
      parent.children.push(child);
    }
  };

  sortedList.forEach((item) => {
    const segments = item.permCode.split(':');
    let prefix = '';
    let parentNode: PermissionTreeNode | null = null;
    segments.forEach((segment, index) => {
      prefix = index === 0 ? segment : `${prefix}:${segment}`;
      const currentNode = prefix === item.permCode ? ensureNode(prefix, item) : ensureNode(prefix);
      if (parentNode) {
        attachChild(parentNode, currentNode);
      } else if (!roots.some((root) => root.nodeKey === currentNode.nodeKey)) {
        roots.push(currentNode);
      }
      parentNode = currentNode;
    });
  });

  return roots;
};

/**
 * 权限树数据。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const permissionTree = computed(() => listToTree(permissions.value));

watch(treeFilterText, (value) => {
  handleTreeFilter(value);
});

watch(permissions, () => {
  handlePermissionSync();
});

loadPermissions();
loadTenants();
</script>

<template>
  <div class="page">
    <div class="page-title">权限管理</div>
    <div class="page-desc">查看权限清单并配置租户权限池。</div>
    <el-row class="board" :gutter="16">
      <el-col :span="8">
        <el-card class="card" shadow="never">
          <div class="section-title">租户列表</div>
          <el-table
            class="tenant-table"
            :data="tenants"
            v-loading="tenantLoading"
            highlight-current-row
            :row-key="(row) => row.tenantId"
            :current-row-key="poolForm.tenantId"
            @row-click="handleTenantSelect"
          >
            <el-table-column prop="tenantId" label="租户ID" min-width="140"/>
            <el-table-column prop="status" label="状态" width="90">
              <template #default="scope">
                <el-tag :type="scope.row.status === 0 ? 'info' : 'success'">
                  {{ scope.row.status === 0 ? '停用' : '启用' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!isSysAdmin" class="tenant-hint">仅系统管理员可查看租户列表。</div>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card class="card" shadow="never">
          <div class="section-title">权限配置</div>
          <div class="permission-toolbar">
            <el-input
              v-model="treeFilterText"
              placeholder="搜索权限名称"
              clearable
              class="permission-search"
            />
            <div class="permission-actions">
              <el-button @click="handleRefreshPermissions">刷新</el-button>
              <el-button type="primary" :loading="poolSaving" :disabled="!poolForm.tenantId" @click="handleSavePool">
                保存
              </el-button>
            </div>
          </div>
          <div class="permission-summary">
            当前租户：<span class="tenant-id">{{ poolForm.tenantId || '未选择' }}</span>
          </div>
          <el-tree
            ref="treeRef"
            class="permission-tree"
            :data="permissionTree"
            :props="treeProps"
            :filter-node-method="filterPermissionNode"
            node-key="nodeKey"
            default-expand-all
            show-checkbox
            v-loading="loading || poolLoading"
          >
            <template #default="{ data }">
              <div class="permission-node">
                <span class="permission-name">{{ data.permName }}</span>
                <span v-if="data.permCode" class="permission-code">{{ data.permCode }}</span>
                <el-tag
                  v-if="data.permType"
                  size="small"
                  :type="resolvePermissionTagType(data.permType)"
                  class="permission-tag"
                >
                  {{ resolvePermissionTag(data.permType) }}
                </el-tag>
              </div>
            </template>
          </el-tree>
        </el-card>
      </el-col>
    </el-row>
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

.section-title {
  font-weight: 600;
  margin-bottom: 12px;
}

.board {
  margin-top: 8px;
}

.tenant-table {
  width: 100%;
}

.tenant-hint {
  margin-top: 12px;
  color: #94a3b8;
  font-size: 13px;
}

.permission-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.permission-search {
  width: 260px;
}

.permission-actions {
  display: flex;
  gap: 8px;
}

.permission-summary {
  margin-bottom: 8px;
  color: #64748b;
}

.tenant-id {
  color: #0f172a;
  font-weight: 600;
}

.permission-tree {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
  max-height: 520px;
  overflow: auto;
}

.permission-node {
  display: flex;
  align-items: center;
  gap: 8px;
}

.permission-name {
  font-weight: 500;
}

.permission-code {
  font-size: 12px;
  color: #94a3b8;
}

.permission-tag {
  margin-left: 4px;
}
</style>
