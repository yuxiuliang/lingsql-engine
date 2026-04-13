<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue';
import {ElMessage} from 'element-plus';
import {useAuthStore} from '@/store/auth';
import {
  fetchOrgGovernanceCatalog,
  fetchOrgUnits,
  type OrgGovernanceCatalogView,
  type OrgGovernanceOptionView,
  type OrgUnitView
} from '@/api/organization';
import {
  applyUserScopeReleaseRecord,
  createUserScopeReleaseRecord,
  createRollbackUserScopeReleaseRecord,
  createUser,
  DataScopePreviewView,
  DataScopeRule,
  fetchRoles,
  fetchUserScopeReleaseRecords,
  fetchUsers,
  previewUserDataScope,
  resetUserPassword,
  RoleView,
  UserScopeReleaseCreateRequest,
  UserScopeReleaseRollbackRequest,
  UserScopeReleaseReviewRequest,
  UserScopeReleaseView,
  reviewUserScopeReleaseRecord,
  updateUser,
  UserDataScopePreviewRequest,
  UserCreateRequest,
  UserUpdateRequest,
  UserView
} from '@/api/rbac';

const authStore = useAuthStore();
const loading = ref(false);
const dialogVisible = ref(false);
const passwordVisible = ref(false);
const editingId = ref<number | null>(null);
const previewLoading = ref(false);
const previewResult = ref<DataScopePreviewView | null>(null);
const baselineDataScope = ref<DataScopeRule | null>(null);
const baselineRoleIds = ref<number[]>([]);
const baselineOrgUnitIds = ref<number[]>([]);
const sampleSql = ref('');
const governanceCatalog = ref<OrgGovernanceCatalogView | null>(null);
const releaseRecords = ref<UserScopeReleaseView[]>([]);
const releaseLoading = ref(false);
const releaseSubmitting = ref(false);
const users = ref<UserView[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const roleOptions = ref<RoleView[]>([]);
const orgTree = ref<OrgUnitView[]>([]);
const scopeTypeOptions = [
  {label: '继承角色', value: 'INHERIT'},
  {label: '全部数据', value: 'ALL'},
  {label: '本租户', value: 'TENANT'},
  {label: '仅本人', value: 'SELF'},
  {label: '指定组织', value: 'ORG'},
  {label: '组织及下级', value: 'ORG_SUBTREE'},
  {label: '关系目标组织', value: 'RELATION'},
  {label: '关系目标组织及下级', value: 'RELATION_SUBTREE'},
  {label: '自定义条件', value: 'CUSTOM'}
];

const defaultRelationTypeLabelMapping: Record<string, string> = {
  SUPERVISE: '主管',
  COOPERATE: '协同',
  SERVICE: '服务',
  OWNERSHIP: '归口',
  MATRIX: '矩阵'
};

const releaseStatusLabelMap: Record<string, string> = {
  DRAFT: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  APPLIED: '已应用'
};

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
  roleIds: [],
  orgUnitIds: [],
  primaryOrgUnitId: undefined,
  dataScope: {
    scopeType: 'INHERIT',
    scopeColumn: '',
    scopeRule: '',
    relationTypes: []
  }
});

const resetForm = reactive({
  newPassword: ''
});

/**
 * 创建默认数据权限配置。
 *
 * @return 默认数据权限
 * @author 余炘洋
 * @date 2026-04-11
 */
const createDefaultDataScope = (): DataScopeRule => {
  return {
    scopeType: 'INHERIT',
    scopeColumn: '',
    scopeRule: '',
    relationTypes: []
  };
};

/**
 * 格式化数据权限文本。
 *
 * @param scopeType 范围类型
 * @return 文本
 * @author 余炘洋
 * @date 2026-04-11
 */
const formatScopeType = (scopeType?: string) => {
  const mapping: Record<string, string> = {
    INHERIT: '继承角色',
    ALL: '全部数据',
    TENANT: '本租户',
    SELF: '仅本人',
    ORG: '指定组织',
    ORG_SUBTREE: '组织及下级',
    RELATION: '关系目标组织',
    RELATION_SUBTREE: '关系目标组织及下级',
    CUSTOM: '自定义条件'
  };
  return mapping[scopeType || 'INHERIT'] || scopeType || '继承角色';
};

/**
 * 组装数据权限请求。
 *
 * @return 数据权限配置
 * @author 余炘洋
 * @date 2026-04-11
 */
const buildDataScopePayload = (): DataScopeRule => {
  return {
    scopeType: form.dataScope?.scopeType || 'INHERIT',
    scopeColumn: form.dataScope?.scopeColumn || '',
    scopeRule: form.dataScope?.scopeRule || '',
    relationTypes: (form.dataScope?.relationTypes || []).filter(Boolean)
  };
};

/**
 * 解析当前表单的生效数据权限预览。
 *
 * @return 预览信息
 * @author 余炘洋
 * @date 2026-04-11
 */
const effectiveScopePreview = computed(() => {
  const source = form.dataScope?.scopeType && form.dataScope.scopeType !== 'INHERIT' ? '用户覆盖' : '继承角色';
  const rule = buildDataScopePayload();
  const extra = rule.scopeType === 'CUSTOM'
    ? `自定义条件：${rule.scopeRule || '未填写'}`
    : rule.scopeType === 'RELATION' || rule.scopeType === 'RELATION_SUBTREE'
      ? `关系类型：${(rule.relationTypes || []).join(' / ') || '未选择'}`
    : `字段：${rule.scopeColumn || (rule.scopeType === 'SELF' ? 'creator_id' : rule.scopeType === 'ORG' || rule.scopeType === 'ORG_SUBTREE' ? 'org_code' : '默认')}`;
  return {
    title: `当前配置：${formatScopeType(rule.scopeType)}（${source}）`,
    description: extra
  };
});

/**
 * 校验用户数据权限表单。
 *
 * @return 是否通过
 * @author 余炘洋
 * @date 2026-04-11
 */
const validateDataScope = () => {
  const scopeType = form.dataScope?.scopeType || 'INHERIT';
  if (scopeType === 'CUSTOM' && !form.dataScope?.scopeRule?.trim()) {
    ElMessage.warning('选择自定义条件时必须填写 SQL 条件片段');
    return false;
  }
  if ((scopeType === 'RELATION' || scopeType === 'RELATION_SUBTREE') && !(form.dataScope?.relationTypes || []).length) {
    ElMessage.warning('关系型数据权限至少选择一个关系类型');
    return false;
  }
  if ((scopeType === 'ORG' || scopeType === 'ORG_SUBTREE') && !(form.orgUnitIds || []).length) {
    ElMessage.warning('组织范围权限要求当前用户至少绑定一个组织节点');
    return false;
  }
  return true;
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

const currentUserId = computed(() => authStore.userId || 0);

const resolveCatalogOptions = (
  options?: OrgGovernanceOptionView[],
  fallbackNameMapping?: Record<string, string>
) => {
  const mapping = new Map<string, {label: string; value: string}>();
  (options || []).forEach((item) => {
    if (!item?.code) {
      return;
    }
    const code = item.code.trim().toUpperCase();
    const name = item.name?.trim() || fallbackNameMapping?.[code] || code;
    mapping.set(code, {
      label: name === code ? code : `${name} (${code})`,
      value: code
    });
  });
  Object.entries(fallbackNameMapping || {}).forEach(([code, name]) => {
    if (!mapping.has(code)) {
      mapping.set(code, {
        label: `${name} (${code})`,
        value: code
      });
    }
  });
  return Array.from(mapping.values());
};

const relationTypeOptions = computed(() => resolveCatalogOptions(
  governanceCatalog.value?.relationTypeOptions,
  defaultRelationTypeLabelMapping
));

/**
 * 组织节点选项。
 *
 * @author 余炘洋
 * @date 2026-04-11
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
 * 加载组织树。
 *
 * @author 余炘洋
 * @date 2026-04-11
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

const loadGovernanceCatalog = async () => {
  try {
    if (!hasTenantSelected.value) {
      governanceCatalog.value = null;
      return;
    }
    governanceCatalog.value = await fetchOrgGovernanceCatalog(activeTenantId.value || undefined);
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载组织治理字典失败';
    if (message.includes('无权限')) {
      governanceCatalog.value = null;
      return;
    }
    ElMessage.error(message);
  }
};

const loadReleaseRecords = async () => {
  try {
    if (!hasTenantSelected.value) {
      releaseRecords.value = [];
      return;
    }
    releaseLoading.value = true;
    releaseRecords.value = await fetchUserScopeReleaseRecords(activeTenantId.value || undefined);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载用户数据权限发布单失败');
  } finally {
    releaseLoading.value = false;
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
    roleIds: [],
    orgUnitIds: [],
    primaryOrgUnitId: undefined,
    dataScope: createDefaultDataScope()
  });
  previewResult.value = null;
  baselineDataScope.value = createDefaultDataScope();
  baselineRoleIds.value = [];
  baselineOrgUnitIds.value = [];
  sampleSql.value = '';
  await Promise.all([loadRoles(), loadOrgs()]);
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
    roleIds: row.roleIds || [],
    orgUnitIds: row.orgUnitIds || [],
    primaryOrgUnitId: row.primaryOrgUnitId,
    dataScope: {
      scopeType: row.dataScope?.scopeType || 'INHERIT',
      scopeColumn: row.dataScope?.scopeColumn || '',
      scopeRule: row.dataScope?.scopeRule || '',
      relationTypes: row.dataScope?.relationTypes || []
    }
  });
  previewResult.value = null;
  baselineDataScope.value = {
    scopeType: row.dataScope?.scopeType || 'INHERIT',
    scopeColumn: row.dataScope?.scopeColumn || '',
    scopeRule: row.dataScope?.scopeRule || '',
    relationTypes: row.dataScope?.relationTypes || []
  };
  baselineRoleIds.value = [...(row.roleIds || [])];
  baselineOrgUnitIds.value = [...(row.orgUnitIds || [])];
  sampleSql.value = '';
  await Promise.all([loadRoles(), loadOrgs()]);
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
  if (!validateDataScope()) {
    return;
  }
  try {
    if (editingId.value) {
      await updateUser(editingId.value, {
        nickname: form.nickname,
        status: form.status,
        isExperience: form.isExperience,
        roleIds: form.roleIds,
        orgUnitIds: form.orgUnitIds,
        primaryOrgUnitId: form.primaryOrgUnitId,
        dataScope: buildDataScopePayload()
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
        roleIds: form.roleIds,
        orgUnitIds: form.orgUnitIds,
        primaryOrgUnitId: form.primaryOrgUnitId,
        dataScope: buildDataScopePayload()
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
  Promise.all([loadUsers(), loadRoles()]);
};

/**
 * 预览用户数据权限命中范围。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
const handlePreviewDataScope = async () => {
  const payload: UserDataScopePreviewRequest = {
    tenantId: activeTenantId.value || undefined,
    dataScope: buildDataScopePayload(),
    userId: editingId.value || undefined,
    baselineDataScope: baselineDataScope.value || createDefaultDataScope(),
    orgUnitIds: form.orgUnitIds || [],
    baselineOrgUnitIds: baselineOrgUnitIds.value || [],
    roleIds: form.roleIds || [],
    baselineRoleIds: baselineRoleIds.value || [],
    sampleSql: sampleSql.value || undefined
  };
  previewLoading.value = true;
  try {
    previewResult.value = await previewUserDataScope(payload);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '预览数据权限失败');
  } finally {
    previewLoading.value = false;
  }
};

const handleCreateScopeRelease = async () => {
  if (!editingId.value) {
    ElMessage.warning('请先选择已有用户后再生成发布单');
    return;
  }
  const changeRemark = window.prompt('可选：请输入本次用户数据权限变更说明', '') || '';
  const payload: UserScopeReleaseCreateRequest = {
    tenantId: activeTenantId.value || undefined,
    changeRemark: changeRemark.trim() || undefined,
    previewRequest: {
      tenantId: activeTenantId.value || undefined,
      userId: editingId.value,
      dataScope: buildDataScopePayload(),
      baselineDataScope: baselineDataScope.value || createDefaultDataScope(),
      orgUnitIds: form.orgUnitIds || [],
      baselineOrgUnitIds: baselineOrgUnitIds.value || [],
      roleIds: form.roleIds || [],
      baselineRoleIds: baselineRoleIds.value || [],
      sampleSql: sampleSql.value || undefined
    }
  };
  releaseSubmitting.value = true;
  try {
    await createUserScopeReleaseRecord(payload);
    ElMessage.success('用户数据权限发布单已创建');
    await loadReleaseRecords();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建用户数据权限发布单失败');
  } finally {
    releaseSubmitting.value = false;
  }
};

const handleReviewScopeRelease = async (record: UserScopeReleaseView, decision: 'APPROVED' | 'REJECTED') => {
  if (!record?.id) {
    return;
  }
  const reviewComment = window.prompt(
    decision === 'APPROVED' ? '可选：请输入审核通过说明' : '可选：请输入驳回原因',
    ''
  ) || '';
  const payload: UserScopeReleaseReviewRequest = {
    decision,
    reviewComment: reviewComment.trim() || undefined
  };
  releaseSubmitting.value = true;
  try {
    await reviewUserScopeReleaseRecord(record.id, payload);
    ElMessage.success(decision === 'APPROVED' ? '发布单已审核通过' : '发布单已驳回');
    await loadReleaseRecords();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审核用户数据权限发布单失败');
  } finally {
    releaseSubmitting.value = false;
  }
};

const handleApplyScopeRelease = async (record: UserScopeReleaseView) => {
  if (!record?.id) {
    return;
  }
  if (!window.confirm(`确认应用发布单“${record.releaseTitle || record.username}”吗？`)) {
    return;
  }
  releaseSubmitting.value = true;
  try {
    await applyUserScopeReleaseRecord(record.id);
    ElMessage.success('用户数据权限发布单已应用');
    await Promise.all([loadUsers(), loadReleaseRecords()]);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '应用用户数据权限发布单失败');
  } finally {
    releaseSubmitting.value = false;
  }
};

const handleCreateRollbackScopeRelease = async (record: UserScopeReleaseView) => {
  if (!record?.id) {
    return;
  }
  const changeRemark = window.prompt('可选：请输入本次用户数据权限回滚说明', '') || '';
  const payload: UserScopeReleaseRollbackRequest = {
    changeRemark: changeRemark.trim() || undefined
  };
  releaseSubmitting.value = true;
  try {
    await createRollbackUserScopeReleaseRecord(record.id, payload);
    ElMessage.success('用户数据权限回滚单已生成');
    await loadReleaseRecords();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '生成用户数据权限回滚单失败');
  } finally {
    releaseSubmitting.value = false;
  }
};

const resolveReleaseStatusLabel = (status?: string) => {
  return releaseStatusLabelMap[status || 'DRAFT'] || status || '待审核';
};

const resolveReleaseStatusType = (status?: string) => {
  if (status === 'APPROVED') {
    return 'success';
  }
  if (status === 'REJECTED') {
    return 'danger';
  }
  if (status === 'APPLIED') {
    return 'primary';
  }
  return 'warning';
};

watch(
  () => form.orgUnitIds,
  (value) => {
    const ids = value || [];
    if (!ids.length) {
      form.primaryOrgUnitId = undefined;
      return;
    }
    if (!form.primaryOrgUnitId || !ids.includes(form.primaryOrgUnitId)) {
      form.primaryOrgUnitId = ids[0];
    }
  },
  {deep: true}
);

watch(
  () => activeTenantId.value,
  () => {
    page.value = 1;
    dialogVisible.value = false;
    passwordVisible.value = false;
    editingId.value = null;
    previewResult.value = null;
    loadOrgs();
    loadGovernanceCatalog();
    loadRoles();
    loadUsers();
    loadReleaseRecords();
  },
  {immediate: true}
);
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
        <el-table-column label="组织归属" min-width="220">
          <template #default="scope">
            <span>{{ (scope.row.orgUnitNames || []).join(' / ') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数据权限" min-width="140">
          <template #default="scope">
            <el-tag type="warning" effect="plain">{{ formatScopeType(scope.row.dataScope?.scopeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效权限" min-width="140">
          <template #default="scope">
            <el-tag type="success" effect="plain">{{ formatScopeType(scope.row.effectiveDataScope?.scopeType) }}</el-tag>
          </template>
        </el-table-column>
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

    <el-card v-if="hasTenantSelected" class="card" shadow="never">
      <div class="toolbar">
        <div class="filters">
          <span class="tips">用户数据权限发布单</span>
        </div>
        <div class="toolbar-actions">
          <el-button size="small" :loading="releaseLoading" @click="loadReleaseRecords">刷新发布单</el-button>
        </div>
      </div>

      <el-table :data="releaseRecords" border v-loading="releaseLoading">
        <el-table-column prop="createTime" label="创建时间" min-width="160"/>
        <el-table-column prop="releaseTitle" label="发布单标题" min-width="220" show-overflow-tooltip/>
        <el-table-column prop="username" label="用户" min-width="160" show-overflow-tooltip/>
        <el-table-column label="状态" width="110">
          <template #default="{row}">
            <el-tag :type="resolveReleaseStatusType(row.releaseStatus)">{{ resolveReleaseStatusLabel(row.releaseStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="impactSummary" label="影响摘要" min-width="260" show-overflow-tooltip/>
        <el-table-column prop="requestedByName" label="创建人" min-width="140" show-overflow-tooltip/>
        <el-table-column prop="reviewedByName" label="审核人" min-width="140" show-overflow-tooltip/>
        <el-table-column prop="appliedByName" label="应用人" min-width="140" show-overflow-tooltip/>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{row}">
            <el-tag
              v-if="row.releaseStatus === 'DRAFT' && row.requestedByUserId === currentUserId"
              type="warning"
              effect="plain"
            >
              需他人审核
            </el-tag>
            <el-button
              v-if="row.releaseStatus === 'DRAFT' && row.requestedByUserId !== currentUserId"
              link
              type="success"
              :disabled="releaseSubmitting"
              @click="handleReviewScopeRelease(row, 'APPROVED')"
            >
              审核通过
            </el-button>
            <el-button
              v-if="row.releaseStatus === 'DRAFT' && row.requestedByUserId !== currentUserId"
              link
              type="danger"
              :disabled="releaseSubmitting"
              @click="handleReviewScopeRelease(row, 'REJECTED')"
            >
              驳回
            </el-button>
            <el-tag
              v-if="row.releaseStatus === 'APPROVED' && row.requestedByUserId === currentUserId"
              type="warning"
              effect="plain"
            >
              需他人应用
            </el-tag>
            <el-button
              v-if="row.releaseStatus === 'APPROVED' && row.requestedByUserId !== currentUserId"
              link
              type="primary"
              :disabled="releaseSubmitting"
              @click="handleApplyScopeRelease(row)"
            >
              应用
            </el-button>
            <el-button
              v-if="row.releaseStatus === 'APPLIED'"
              link
              type="warning"
              :disabled="releaseSubmitting"
              @click="handleCreateRollbackScopeRelease(row)"
            >
              生成回滚单
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="用户信息" width="760px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="当前租户" v-if="isSysAdmin">
          <el-tag type="success">{{ activeTenantName || activeTenantId || '未选择' }}</el-tag>
        </el-form-item>
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="请输入账号" :disabled="!!editingId"/>
        </el-form-item>
        <el-form-item label="初始密码" v-if="!editingId">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password autocomplete="new-password"/>
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
        <el-form-item label="组织归属">
          <el-select v-model="form.orgUnitIds" multiple filterable clearable style="width: 100%" placeholder="可选多个组织节点">
            <el-option v-for="org in orgOptions" :key="org.value" :value="org.value" :label="org.label"/>
          </el-select>
        </el-form-item>
        <el-form-item label="主归属组织">
          <el-select v-model="form.primaryOrgUnitId" clearable style="width: 100%" placeholder="默认取首个归属节点">
            <el-option
              v-for="org in orgOptions.filter((item) => (form.orgUnitIds || []).includes(item.value))"
              :key="org.value"
              :value="org.value"
              :label="org.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="数据权限">
          <el-select v-model="form.dataScope!.scopeType" style="width: 100%">
            <el-option v-for="item in scopeTypeOptions" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.dataScope?.scopeType === 'INHERIT'">
          <el-alert
            type="info"
            :closable="false"
            title="继承角色时，不会写入用户覆盖配置，实际范围由角色数据权限决定。"
          />
        </el-form-item>
        <el-form-item v-if="form.dataScope?.scopeType === 'ORG' || form.dataScope?.scopeType === 'ORG_SUBTREE'">
          <el-alert
            type="warning"
            :closable="false"
            title="组织范围权限要求当前用户至少绑定一个组织节点，否则查询会被安全策略拦截。"
          />
        </el-form-item>
        <el-form-item v-if="form.dataScope?.scopeType === 'RELATION' || form.dataScope?.scopeType === 'RELATION_SUBTREE'">
          <el-alert
            type="warning"
            :closable="false"
            title="关系型数据权限同样要求当前用户至少绑定一个组织节点，系统会按所选关系类型匹配目标组织。"
          />
        </el-form-item>
        <el-form-item>
          <el-alert
            type="success"
            :closable="false"
            :title="effectiveScopePreview.title"
            :description="effectiveScopePreview.description"
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
            placeholder="例如 status = 1 and org_code in ('HN_CS')"
          />
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
            <el-button v-if="editingId && previewResult" plain :loading="releaseSubmitting" @click="handleCreateScopeRelease">生成发布单</el-button>
            <span class="preview-tip">预览会同时考虑用户覆盖、角色继承、直属组织和关系型数据权限。</span>
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
                <div class="preview-stat-value">{{ previewResult.effectiveSource || 'DEFAULT_TENANT' }}</div>
              </div>
              <div class="preview-stat-item">
                <div class="preview-stat-label">受影响角色数</div>
                <div class="preview-stat-value">{{ previewResult.affectedRoleCount || 0 }}</div>
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
            <div v-if="previewResult.roleCandidates?.length" class="candidate-list">
              <div class="candidate-title">角色叠加解释</div>
              <div class="candidate-grid">
                <div v-for="item in previewResult.roleCandidates" :key="item.roleId || item.roleCode" class="candidate-item" :class="{selected: item.selected}">
                  <div class="candidate-name">{{ item.roleName || item.roleCode }}</div>
                  <div class="candidate-desc">{{ item.summary || '-' }}</div>
                  <div class="candidate-flag">{{ item.selected ? '当前生效' : '候选规则' }}</div>
                </div>
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

    <el-dialog v-model="passwordVisible" title="重置密码" width="420px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="新密码">
          <el-input v-model="resetForm.newPassword" type="password" show-password placeholder="请输入新密码" autocomplete="new-password"/>
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

.candidate-list {
  margin-top: 14px;
}

.candidate-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  margin-bottom: 10px;
}

.candidate-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.candidate-item {
  border: 1px solid #dbe4f0;
  border-radius: 10px;
  background: #ffffff;
  padding: 12px;
}

.candidate-item.selected {
  border-color: #93c5fd;
  background: #eff6ff;
}

.candidate-name {
  font-weight: 600;
  color: #0f172a;
}

.candidate-desc {
  margin-top: 8px;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.candidate-flag {
  margin-top: 10px;
  font-size: 12px;
  color: #2563eb;
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
