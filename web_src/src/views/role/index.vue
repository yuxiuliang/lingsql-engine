<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue';
import {ElMessage} from 'element-plus';
import {useAuthStore} from '@/store/auth';
import {fetchOrgUnits, OrgUnitView} from '@/api/organization';
import {
  createRole,
  DataScopePreviewView,
  DataScopeRule,
  fetchPermissions,
  fetchRoles,
  PermissionView,
  previewRoleDataScope,
  RoleCreateRequest,
  RoleDataScopePreviewRequest,
  RoleUpdateRequest,
  RoleView,
  updateRole
} from '@/api/rbac';

const authStore = useAuthStore();
const roles = ref<RoleView[]>([]);
const permissions = ref<PermissionView[]>([]);
const orgTree = ref<OrgUnitView[]>([]);
const loading = ref(false);
const dialogVisible = ref(false);
const editingId = ref<number | null>(null);
const previewLoading = ref(false);
const previewResult = ref<DataScopePreviewView | null>(null);
const previewOrgUnitIds = ref<number[]>([]);
const baselineDataScope = ref<DataScopeRule | null>(null);
const sampleSql = ref('');
const scopeTypeOptions = [
  {label: '全部数据', value: 'ALL'},
  {label: '本租户', value: 'TENANT'},
  {label: '仅本人', value: 'SELF'},
  {label: '指定组织', value: 'ORG'},
  {label: '组织及下级', value: 'ORG_SUBTREE'},
  {label: '关系目标组织', value: 'RELATION'},
  {label: '关系目标组织及下级', value: 'RELATION_SUBTREE'},
  {label: '自定义条件', value: 'CUSTOM'}
];

const relationTypeOptions = [
  {label: '主管', value: 'SUPERVISE'},
  {label: '协同', value: 'COOPERATE'},
  {label: '服务', value: 'SERVICE'},
  {label: '归口', value: 'OWNERSHIP'},
  {label: '矩阵', value: 'MATRIX'}
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
    scopeRule: '',
    relationTypes: []
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
    scopeRule: '',
    relationTypes: []
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
    RELATION: '关系目标组织',
    RELATION_SUBTREE: '关系目标组织及下级',
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
    scopeRule,
    relationTypes: (form.dataScope?.relationTypes || []).filter(Boolean)
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
  if (form.dataScope?.scopeType === 'RELATION' || form.dataScope?.scopeType === 'RELATION_SUBTREE') {
    if ((form.dataScope?.relationTypes || []).length) {
      return true;
    }
    ElMessage.warning('选择关系型数据权限时必须至少选择一个关系类型');
    return false;
  }
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
 * 组织节点选项。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
const orgOptions = computed(() => {
  const options: Array<{label: string; value: number}> = [];
  const walk = (nodes: OrgUnitView[], prefix = '') => {
    nodes.forEach((node) => {
      options.push({
        label: `${prefix}${node.unitName} [${node.typeName || node.typeCode}]`,
        value: node.id
      });
      walk(node.children || [], `${prefix}└ `);
    });
  };
  walk(orgTree.value);
  return options;
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
 * 加载组织树。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
const loadOrgs = async () => {
  try {
    if (!hasTenantSelected.value) {
      orgTree.value = [];
      return;
    }
    orgTree.value = await fetchOrgUnits(activeTenantId.value || undefined);
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载组织架构失败';
    if (message.includes('无权限')) {
      orgTree.value = [];
      return;
    }
    ElMessage.error(message);
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
  previewOrgUnitIds.value = [];
  previewResult.value = null;
  baselineDataScope.value = createDefaultDataScope();
  sampleSql.value = '';
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
      scopeRule: row.dataScope?.scopeRule || '',
      relationTypes: row.dataScope?.relationTypes || []
    }
  });
  previewOrgUnitIds.value = [];
  previewResult.value = null;
  baselineDataScope.value = {
    scopeType: row.dataScope?.scopeType || 'TENANT',
    scopeColumn: row.dataScope?.scopeColumn || '',
    scopeRule: row.dataScope?.scopeRule || '',
    relationTypes: row.dataScope?.relationTypes || []
  };
  sampleSql.value = '';
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

/**
 * 预览角色数据权限命中范围。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
const handlePreviewDataScope = async () => {
  const payload: RoleDataScopePreviewRequest = {
    tenantId: activeTenantId.value || undefined,
    dataScope: buildDataScopePayload(),
    roleId: editingId.value || undefined,
    baselineDataScope: baselineDataScope.value || createDefaultDataScope(),
    orgUnitIds: previewOrgUnitIds.value || [],
    sampleSql: sampleSql.value || undefined
  };
  previewLoading.value = true;
  try {
    previewResult.value = await previewRoleDataScope(payload);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '预览数据权限失败');
  } finally {
    previewLoading.value = false;
  }
};

watch(
  () => activeTenantId.value,
  () => {
    dialogVisible.value = false;
    editingId.value = null;
    previewResult.value = null;
    loadRoles();
    loadOrgs();
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

    <el-dialog v-model="dialogVisible" title="角色信息" width="760px" :close-on-click-modal="false">
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
        <el-form-item v-if="form.dataScope?.scopeType === 'RELATION' || form.dataScope?.scopeType === 'RELATION_SUBTREE'">
          <el-alert
            type="warning"
            :closable="false"
            title="关系型数据权限会基于用户所属组织的关系语义命中目标组织，适用于主管、服务、矩阵等跨组织访问场景。"
          />
        </el-form-item>
        <el-form-item label="关系类型" v-if="form.dataScope?.scopeType === 'RELATION' || form.dataScope?.scopeType === 'RELATION_SUBTREE'">
          <el-select v-model="form.dataScope!.relationTypes" multiple filterable allow-create default-first-option style="width: 100%">
            <el-option v-for="item in relationTypeOptions" :key="item.value" :label="`${item.label} (${item.value})`" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item label="范围字段">
          <el-input
            v-model="form.dataScope!.scopeColumn"
            placeholder="SELF/ORG/ORG_SUBTREE/RELATION/RELATION_SUBTREE 时可填写字段名，如 creator_id 或 org_code"
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
        <el-form-item label="样本组织">
          <el-select v-model="previewOrgUnitIds" multiple filterable clearable style="width: 100%" placeholder="用于预览角色数据权限命中范围，不会参与保存">
            <el-option v-for="org in orgOptions" :key="org.value" :value="org.value" :label="org.label"/>
          </el-select>
        </el-form-item>
        <el-form-item label="样本 SQL">
          <el-input
            v-model="sampleSql"
            type="textarea"
            :rows="4"
            placeholder="可选，输入一条典型查询用于验证当前权限是否会通过安全校验，并查看改写后的 SQL"
          />
        </el-form-item>
        <el-form-item>
          <div class="preview-toolbar">
            <el-button type="primary" plain :loading="previewLoading" @click="handlePreviewDataScope">预览权限命中</el-button>
            <span class="preview-tip">建议至少选择一个样本组织，便于展开组织型/关系型权限的命中范围。</span>
          </div>
        </el-form-item>
        <el-form-item v-if="previewResult">
          <div class="preview-panel">
            <div class="preview-summary">{{ previewResult.summary || '暂无预览摘要' }}</div>
            <div class="preview-description">{{ previewResult.description || '暂无预览说明' }}</div>
            <div class="preview-impact">{{ previewResult.impactSummary || '暂无影响范围说明' }}</div>
            <div v-if="previewResult.sampleSqlMessage" class="preview-sql-result">
              <el-alert
                :type="previewResult.sampleSqlValid ? 'success' : 'error'"
                :closable="false"
                :title="previewResult.sampleSqlMessage"
              />
              <el-input
                v-if="previewResult.sampleRewrittenSql"
                class="preview-sql-textarea"
                type="textarea"
                :rows="4"
                :model-value="previewResult.sampleRewrittenSql"
                readonly
              />
            </div>
            <div v-if="previewResult.warnings?.length" class="preview-warning-list">
              <el-alert
                v-for="item in previewResult.warnings"
                :key="item"
                type="warning"
                :closable="false"
                :title="item"
              />
            </div>
            <div class="preview-stat-grid">
              <div class="preview-stat-item">
                <div class="preview-stat-label">直属组织</div>
                <div class="preview-stat-value">{{ (previewResult.directOrgCodes || []).join(' / ') || '无' }}</div>
              </div>
              <div class="preview-stat-item">
                <div class="preview-stat-label">直属组织及下级</div>
                <div class="preview-stat-value">{{ (previewResult.directOrgSubtreeCodes || []).join(' / ') || '无' }}</div>
              </div>
              <div class="preview-stat-item">
                <div class="preview-stat-label">命中组织数</div>
                <div class="preview-stat-value">{{ previewResult.matchedOrgCount || 0 }}</div>
              </div>
              <div class="preview-stat-item">
                <div class="preview-stat-label">生效来源</div>
                <div class="preview-stat-value">{{ previewResult.effectiveSource || 'ROLE_CONFIG' }}</div>
              </div>
              <div class="preview-stat-item">
                <div class="preview-stat-label">受影响用户数</div>
                <div class="preview-stat-value">{{ previewResult.affectedUserCount || 0 }}</div>
              </div>
            </div>
            <div v-if="previewResult.diff" class="diff-panel">
              <div class="diff-title">变更前后差异</div>
              <div class="diff-summary-grid">
                <div class="diff-summary-item">
                  <div class="diff-summary-label">基线</div>
                  <div class="diff-summary-value">{{ previewResult.diff.baselineSummary || '无' }}</div>
                </div>
                <div class="diff-summary-item">
                  <div class="diff-summary-label">调整后</div>
                  <div class="diff-summary-value">{{ previewResult.diff.targetSummary || '无' }}</div>
                </div>
                <div class="diff-summary-item">
                  <div class="diff-summary-label">新增组织</div>
                  <div class="diff-summary-value success">{{ previewResult.diff.addedOrgCount || 0 }}</div>
                </div>
                <div class="diff-summary-item">
                  <div class="diff-summary-label">移除组织</div>
                  <div class="diff-summary-value danger">{{ previewResult.diff.removedOrgCount || 0 }}</div>
                </div>
              </div>
              <div v-if="previewResult.diff.addedOrgs?.length" class="preview-tag-list">
                <el-tag v-for="item in previewResult.diff.addedOrgs" :key="`added-${item.orgUnitId}-${item.hitSource}`" type="success" class="preview-tag">
                  + {{ item.unitName || item.unitCode }}
                </el-tag>
              </div>
              <div v-if="previewResult.diff.removedOrgs?.length" class="preview-tag-list">
                <el-tag v-for="item in previewResult.diff.removedOrgs" :key="`removed-${item.orgUnitId}-${item.hitSource}`" type="danger" class="preview-tag">
                  - {{ item.unitName || item.unitCode }}
                </el-tag>
              </div>
            </div>
            <div v-if="previewResult.matchedOrgs?.length" class="preview-tag-list">
              <el-tag v-for="item in previewResult.matchedOrgs" :key="`${item.orgUnitId}-${item.hitSource}`" class="preview-tag">
                {{ item.unitName || item.unitCode }} · {{ item.hitSource }}
              </el-tag>
            </div>
          </div>
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

.preview-toolbar {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.preview-tip {
  color: #94a3b8;
  font-size: 13px;
}

.preview-panel {
  width: 100%;
  border: 1px solid #dbe4f0;
  border-radius: 12px;
  background: #f8fafc;
  padding: 16px;
}

.preview-summary {
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

.preview-description {
  margin-top: 8px;
  color: #475569;
  line-height: 1.7;
}

.preview-impact {
  margin-top: 8px;
  color: #1d4ed8;
  line-height: 1.7;
}

.preview-sql-result {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.preview-sql-textarea {
  width: 100%;
}

.preview-warning-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preview-stat-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.preview-stat-item {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #ffffff;
  padding: 12px;
}

.preview-stat-label {
  font-size: 12px;
  color: #64748b;
}

.preview-stat-value {
  margin-top: 8px;
  color: #0f172a;
  line-height: 1.7;
  word-break: break-all;
}

.preview-tag-list {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.preview-tag {
  margin-right: 0;
}

.diff-panel {
  margin-top: 14px;
}

.diff-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  margin-bottom: 10px;
}

.diff-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.diff-summary-item {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #ffffff;
  padding: 12px;
}

.diff-summary-label {
  font-size: 12px;
  color: #64748b;
}

.diff-summary-value {
  margin-top: 8px;
  color: #0f172a;
  line-height: 1.7;
  word-break: break-all;
}

.diff-summary-value.success {
  color: #15803d;
}

.diff-summary-value.danger {
  color: #b91c1c;
}
</style>
