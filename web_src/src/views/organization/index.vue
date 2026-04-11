<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import {useAuthStore} from '@/store/auth';
import {
  createOrgRelation,
  createOrgUnit,
  createOrgUnitType,
  deleteOrgRelation,
  deleteOrgUnit,
  deleteOrgUnitType,
  fetchOrgRelations,
  fetchOrgUnits,
  fetchOrgUnitTypes,
  OrgUnitRelationView,
  OrgUnitTypeView,
  OrgUnitView,
  updateOrgRelation,
  updateOrgUnit,
  updateOrgUnitType
} from '@/api/organization';
import {fetchUsers, UserView} from '@/api/rbac';

interface UnitFormModel {
  tenantId: string;
  unitCode: string;
  unitName: string;
  typeCode: string;
  leaderUserId: number | null;
  parentId: number | null;
  sort: number;
  status: number;
  remark: string;
  tags: string[];
  attributeJson: string;
}

interface TypeFormModel {
  tenantId: string;
  typeCode: string;
  typeName: string;
  sort: number;
  status: number;
  remark: string;
}

interface RelationFormModel {
  tenantId: string;
  sourceOrgUnitId?: number;
  targetOrgUnitId?: number;
  relationType: string;
  relationName: string;
  sort: number;
  status: number;
  remark: string;
}

const authStore = useAuthStore();
const loading = ref(false);
const typeLoading = ref(false);
const relationLoading = ref(false);
const userLoading = ref(false);
const unitDialogVisible = ref(false);
const typeDialogVisible = ref(false);
const relationDialogVisible = ref(false);
const editingUnitId = ref<number | null>(null);
const editingTypeId = ref<number | null>(null);
const editingRelationId = ref<number | null>(null);
const typeList = ref<OrgUnitTypeView[]>([]);
const orgTree = ref<OrgUnitView[]>([]);
const relationList = ref<OrgUnitRelationView[]>([]);
const userOptions = ref<UserView[]>([]);

const relationTypeOptions = [
  {label: '主管', value: 'SUPERVISE'},
  {label: '协同', value: 'COOPERATE'},
  {label: '服务', value: 'SERVICE'},
  {label: '归口', value: 'OWNERSHIP'},
  {label: '矩阵', value: 'MATRIX'}
];

const unitForm = reactive<UnitFormModel>({
  tenantId: '',
  unitCode: '',
  unitName: '',
  typeCode: '',
  leaderUserId: null,
  parentId: null,
  sort: 0,
  status: 1,
  remark: '',
  tags: [],
  attributeJson: ''
});

const typeForm = reactive<TypeFormModel>({
  tenantId: '',
  typeCode: '',
  typeName: '',
  sort: 0,
  status: 1,
  remark: ''
});

const relationForm = reactive<RelationFormModel>({
  tenantId: '',
  relationType: '',
  relationName: '',
  sort: 0,
  status: 1,
  remark: ''
});

/**
 * 判断是否系统管理员。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const isSysAdmin = computed(() => authStore.isSysAdmin);

/**
 * 当前选择租户ID。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const activeTenantId = computed(() => {
  return isSysAdmin.value ? authStore.activeTenantId : authStore.tenantId;
});

/**
 * 判断是否已选择租户。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const hasTenantSelected = computed(() => {
  return !isSysAdmin.value || !!activeTenantId.value;
});

/**
 * 组织节点选项。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const unitOptions = computed(() => {
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
 * 扁平化组织节点。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const flatUnits = computed(() => {
  const result: OrgUnitView[] = [];
  const walk = (nodes: OrgUnitView[]) => {
    nodes.forEach((node) => {
      result.push(node);
      walk(node.children || []);
    });
  };
  walk(orgTree.value);
  return result;
});

/**
 * 规范化标签列表，避免空值和重复项。
 *
 * @param tags 标签列表
 * @return 处理后的标签列表
 * @author 余炘洋
 * @date 2026-04-11
 */
const normalizeTags = (tags: string[]) => {
  return Array.from(new Set((tags || [])
    .map((tag) => tag.trim())
    .filter(Boolean)));
};

/**
 * 格式化用户名称。
 *
 * @param user 用户信息
 * @return 展示名称
 * @author 余炘洋
 * @date 2026-04-11
 */
const formatUserLabel = (user: UserView) => {
  if (!user.nickname) {
    return user.username;
  }
  return `${user.nickname} (${user.username})`;
};

/**
 * 格式化负责人显示。
 *
 * @param row 组织节点
 * @return 负责人文本
 * @author 余炘洋
 * @date 2026-04-11
 */
const formatLeader = (row: OrgUnitView) => {
  if (row.leaderNickname && row.leaderUsername) {
    return `${row.leaderNickname} (${row.leaderUsername})`;
  }
  return row.leaderNickname || row.leaderUsername || '-';
};

/**
 * 根据节点 ID 获取名称。
 *
 * @param id 节点ID
 * @return 节点名称
 * @author 余炘洋
 * @date 2026-04-11
 */
const findUnitName = (id?: number) => {
  if (!id) {
    return '-';
  }
  return flatUnits.value.find((item) => item.id === id)?.unitName || `#${id}`;
};

/**
 * 重置组织类型表单。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const resetTypeForm = () => {
  Object.assign(typeForm, {
    tenantId: activeTenantId.value || '',
    typeCode: '',
    typeName: '',
    sort: 0,
    status: 1,
    remark: ''
  });
};

/**
 * 重置组织节点表单。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const resetUnitForm = () => {
  Object.assign(unitForm, {
    tenantId: activeTenantId.value || '',
    unitCode: '',
    unitName: '',
    typeCode: typeList.value[0]?.typeCode || '',
    leaderUserId: null,
    parentId: null,
    sort: 0,
    status: 1,
    remark: '',
    tags: [],
    attributeJson: ''
  });
};

/**
 * 重置组织关系表单。
 *
 * @param source 可选的源节点
 * @author 余炘洋
 * @date 2026-04-11
 */
const resetRelationForm = (source?: OrgUnitView) => {
  Object.assign(relationForm, {
    tenantId: activeTenantId.value || '',
    sourceOrgUnitId: source?.id,
    targetOrgUnitId: undefined,
    relationType: '',
    relationName: '',
    sort: 0,
    status: 1,
    remark: ''
  });
};

/**
 * 校验扩展属性 JSON 格式。
 *
 * @param attributeJson 扩展属性
 * @return 是否有效
 * @author 余炘洋
 * @date 2026-04-11
 */
const validateAttributeJson = (attributeJson: string) => {
  const content = attributeJson.trim();
  if (!content) {
    return true;
  }
  try {
    JSON.parse(content);
    return true;
  } catch (error) {
    ElMessage.warning('扩展属性必须是合法的 JSON');
    return false;
  }
};

/**
 * 加载组织类型。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const loadTypes = async () => {
  if (!hasTenantSelected.value) {
    typeList.value = [];
    return;
  }
  typeLoading.value = true;
  try {
    typeList.value = await fetchOrgUnitTypes(activeTenantId.value || undefined);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载组织类型失败');
  } finally {
    typeLoading.value = false;
  }
};

/**
 * 加载组织树。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const loadOrgTree = async () => {
  if (!hasTenantSelected.value) {
    orgTree.value = [];
    return;
  }
  loading.value = true;
  try {
    orgTree.value = await fetchOrgUnits(activeTenantId.value || undefined);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载组织架构失败');
  } finally {
    loading.value = false;
  }
};

/**
 * 加载组织关系。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const loadRelations = async () => {
  if (!hasTenantSelected.value) {
    relationList.value = [];
    return;
  }
  relationLoading.value = true;
  try {
    relationList.value = await fetchOrgRelations(activeTenantId.value || undefined);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载组织关系失败');
  } finally {
    relationLoading.value = false;
  }
};

/**
 * 加载负责人候选用户。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const loadUsers = async () => {
  if (!hasTenantSelected.value) {
    userOptions.value = [];
    return;
  }
  userLoading.value = true;
  try {
    const result = await fetchUsers({
      tenantId: activeTenantId.value || undefined,
      page: 1,
      size: 200
    });
    userOptions.value = result.list || [];
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载负责人候选失败';
    if (message.includes('无权限')) {
      userOptions.value = [];
      return;
    }
    ElMessage.error(message);
  } finally {
    userLoading.value = false;
  }
};

/**
 * 刷新页面数据。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const refreshData = async () => {
  if (!hasTenantSelected.value) {
    typeList.value = [];
    orgTree.value = [];
    relationList.value = [];
    userOptions.value = [];
    return;
  }
  await Promise.all([loadTypes(), loadOrgTree(), loadRelations(), loadUsers()]);
};

/**
 * 打开新增类型弹窗。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleCreateType = () => {
  editingTypeId.value = null;
  resetTypeForm();
  typeDialogVisible.value = true;
};

/**
 * 编辑组织类型。
 *
 * @param row 类型行
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleEditType = (row: OrgUnitTypeView) => {
  editingTypeId.value = row.id;
  Object.assign(typeForm, {
    tenantId: row.tenantId,
    typeCode: row.typeCode,
    typeName: row.typeName,
    sort: row.sort ?? 0,
    status: row.status ?? 1,
    remark: row.remark || ''
  });
  typeDialogVisible.value = true;
};

/**
 * 保存组织类型。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleSaveType = async () => {
  if (!typeForm.typeCode || !typeForm.typeName) {
    ElMessage.warning('请补全组织类型编码和名称');
    return;
  }
  try {
    if (editingTypeId.value) {
      await updateOrgUnitType(editingTypeId.value, {
        typeName: typeForm.typeName,
        sort: typeForm.sort,
        status: typeForm.status,
        remark: typeForm.remark
      });
      ElMessage.success('组织类型已更新');
    } else {
      await createOrgUnitType({
        tenantId: activeTenantId.value || undefined,
        typeCode: typeForm.typeCode,
        typeName: typeForm.typeName,
        sort: typeForm.sort,
        status: typeForm.status,
        remark: typeForm.remark
      });
      ElMessage.success('组织类型已创建');
    }
    typeDialogVisible.value = false;
    await refreshData();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存组织类型失败');
  }
};

/**
 * 删除组织类型。
 *
 * @param row 类型行
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleDeleteType = async (row: OrgUnitTypeView) => {
  try {
    await ElMessageBox.confirm(`确认删除组织类型“${row.typeName}”吗？`, '提示', {type: 'warning'});
    await deleteOrgUnitType(row.id);
    ElMessage.success('组织类型已删除');
    await refreshData();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '删除组织类型失败');
    }
  }
};

/**
 * 打开新增组织节点弹窗。
 *
 * @param parent 父节点
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleCreateUnit = async (parent?: OrgUnitView) => {
  editingUnitId.value = null;
  resetUnitForm();
  unitForm.parentId = parent?.id ?? null;
  await loadUsers();
  unitDialogVisible.value = true;
};

/**
 * 编辑组织节点。
 *
 * @param row 节点
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleEditUnit = async (row: OrgUnitView) => {
  editingUnitId.value = row.id;
  Object.assign(unitForm, {
    tenantId: row.tenantId,
    unitCode: row.unitCode,
    unitName: row.unitName,
    typeCode: row.typeCode,
    leaderUserId: row.leaderUserId ?? null,
    parentId: row.parentId ?? null,
    sort: row.sort ?? 0,
    status: row.status ?? 1,
    remark: row.remark || '',
    tags: [...(row.tags || [])],
    attributeJson: row.attributeJson || ''
  });
  await loadUsers();
  unitDialogVisible.value = true;
};

/**
 * 保存组织节点。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleSaveUnit = async () => {
  if (!unitForm.unitCode || !unitForm.unitName || !unitForm.typeCode) {
    ElMessage.warning('请补全组织编码、名称和类型');
    return;
  }
  if (editingUnitId.value && unitForm.parentId === editingUnitId.value) {
    ElMessage.warning('上级节点不能选择自己');
    return;
  }
  if (!validateAttributeJson(unitForm.attributeJson)) {
    return;
  }
  const payload = {
    unitName: unitForm.unitName,
    typeCode: unitForm.typeCode,
    leaderUserId: unitForm.leaderUserId || null,
    parentId: unitForm.parentId ?? null,
    sort: unitForm.sort,
    status: unitForm.status,
    remark: unitForm.remark,
    tags: normalizeTags(unitForm.tags),
    attributeJson: unitForm.attributeJson.trim()
  };
  try {
    if (editingUnitId.value) {
      await updateOrgUnit(editingUnitId.value, payload);
      ElMessage.success('组织节点已更新');
    } else {
      await createOrgUnit({
        tenantId: activeTenantId.value || undefined,
        unitCode: unitForm.unitCode,
        ...payload
      });
      ElMessage.success('组织节点已创建');
    }
    unitDialogVisible.value = false;
    await Promise.all([loadOrgTree(), loadRelations()]);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存组织节点失败');
  }
};

/**
 * 删除组织节点。
 *
 * @param row 节点
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleDeleteUnit = async (row: OrgUnitView) => {
  try {
    await ElMessageBox.confirm(`确认删除组织节点“${row.unitName}”吗？`, '提示', {type: 'warning'});
    await deleteOrgUnit(row.id);
    ElMessage.success('组织节点已删除');
    await Promise.all([loadOrgTree(), loadRelations()]);
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '删除组织节点失败');
    }
  }
};

/**
 * 打开新增组织关系弹窗。
 *
 * @param source 默认源节点
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleCreateRelation = (source?: OrgUnitView) => {
  if (flatUnits.value.length < 2) {
    ElMessage.warning('至少需要两个组织节点才能建立关系');
    return;
  }
  editingRelationId.value = null;
  resetRelationForm(source);
  relationDialogVisible.value = true;
};

/**
 * 编辑组织关系。
 *
 * @param row 关系行
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleEditRelation = (row: OrgUnitRelationView) => {
  editingRelationId.value = row.id;
  Object.assign(relationForm, {
    tenantId: row.tenantId,
    sourceOrgUnitId: row.sourceOrgUnitId,
    targetOrgUnitId: row.targetOrgUnitId,
    relationType: row.relationType,
    relationName: row.relationName || '',
    sort: row.sort ?? 0,
    status: row.status ?? 1,
    remark: row.remark || ''
  });
  relationDialogVisible.value = true;
};

/**
 * 保存组织关系。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleSaveRelation = async () => {
  if (!relationForm.sourceOrgUnitId || !relationForm.targetOrgUnitId || !relationForm.relationType) {
    ElMessage.warning('请补全源节点、目标节点和关系类型');
    return;
  }
  if (relationForm.sourceOrgUnitId === relationForm.targetOrgUnitId) {
    ElMessage.warning('源节点和目标节点不能相同');
    return;
  }
  const payload = {
    sourceOrgUnitId: relationForm.sourceOrgUnitId,
    targetOrgUnitId: relationForm.targetOrgUnitId,
    relationType: relationForm.relationType.trim(),
    relationName: relationForm.relationName.trim(),
    sort: relationForm.sort,
    status: relationForm.status,
    remark: relationForm.remark
  };
  try {
    if (editingRelationId.value) {
      await updateOrgRelation(editingRelationId.value, payload);
      ElMessage.success('组织关系已更新');
    } else {
      await createOrgRelation({
        tenantId: activeTenantId.value || undefined,
        ...payload
      });
      ElMessage.success('组织关系已创建');
    }
    relationDialogVisible.value = false;
    await loadRelations();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存组织关系失败');
  }
};

/**
 * 删除组织关系。
 *
 * @param row 关系行
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleDeleteRelation = async (row: OrgUnitRelationView) => {
  try {
    await ElMessageBox.confirm(`确认删除组织关系“${row.relationName || row.relationType}”吗？`, '提示', {type: 'warning'});
    await deleteOrgRelation(row.id);
    ElMessage.success('组织关系已删除');
    await loadRelations();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '删除组织关系失败');
    }
  }
};

watch(
  () => activeTenantId.value,
  () => {
    typeDialogVisible.value = false;
    unitDialogVisible.value = false;
    relationDialogVisible.value = false;
    editingTypeId.value = null;
    editingUnitId.value = null;
    editingRelationId.value = null;
    refreshData();
  },
  {immediate: true}
);
</script>

<template>
  <div class="page">
    <div class="page-title">组织架构</div>
    <div class="page-desc">维护租户内部的通用组织树、负责人、标签与跨树关系，兼容政府、学校、企业与连锁门店场景。</div>

    <el-card class="card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-actions">
          <span v-if="isSysAdmin" class="tips">提示：请先在顶部选择租户后再管理组织域。</span>
          <el-button :disabled="!hasTenantSelected" @click="refreshData">刷新</el-button>
          <el-button type="primary" :disabled="!hasTenantSelected" @click="handleCreateType">新增组织类型</el-button>
          <el-button type="primary" :disabled="!hasTenantSelected || typeList.length === 0" @click="handleCreateUnit()">新增组织节点</el-button>
          <el-button type="primary" :disabled="!hasTenantSelected || flatUnits.length < 2" @click="handleCreateRelation()">新增组织关系</el-button>
        </div>
      </div>

      <el-empty v-if="!hasTenantSelected" description="请先在顶部选择要管理的租户"/>
      <template v-else>
        <div class="section">
          <div class="section-title">组织类型</div>
          <el-table :data="typeList" border v-loading="typeLoading">
            <el-table-column prop="typeCode" label="类型编码" min-width="140"/>
            <el-table-column prop="typeName" label="类型名称" min-width="140"/>
            <el-table-column prop="unitCount" label="节点数" width="90"/>
            <el-table-column prop="sort" label="排序" width="80"/>
            <el-table-column label="状态" width="100">
              <template #default="{row}">
                <el-tag :type="row.status === 0 ? 'info' : 'success'">{{ row.status === 0 ? '停用' : '启用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip/>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{row}">
                <el-button link type="primary" @click="handleEditType(row)">编辑</el-button>
                <el-button link type="danger" @click="handleDeleteType(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="section">
          <div class="section-title">组织树</div>
          <el-table :data="orgTree" row-key="id" border default-expand-all v-loading="loading">
            <el-table-column prop="unitName" label="组织名称" min-width="220"/>
            <el-table-column prop="unitCode" label="组织编码" min-width="140"/>
            <el-table-column prop="typeName" label="组织类型" min-width="140"/>
            <el-table-column label="负责人" min-width="180">
              <template #default="{row}">
                <span>{{ formatLeader(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="标签" min-width="200">
              <template #default="{row}">
                <div v-if="row.tags?.length" class="tag-list">
                  <el-tag v-for="tag in row.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
                </div>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="attributeJson" label="扩展属性" min-width="220" show-overflow-tooltip/>
            <el-table-column prop="treeLevel" label="层级" width="80"/>
            <el-table-column prop="memberCount" label="成员数" width="90"/>
            <el-table-column prop="sort" label="排序" width="80"/>
            <el-table-column label="状态" width="100">
              <template #default="{row}">
                <el-tag :type="row.status === 0 ? 'info' : 'success'">{{ row.status === 0 ? '停用' : '启用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip/>
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{row}">
                <el-button link type="primary" @click="handleCreateUnit(row)">新增下级</el-button>
                <el-button link type="primary" @click="handleCreateRelation(row)">建关系</el-button>
                <el-button link type="primary" @click="handleEditUnit(row)">编辑</el-button>
                <el-button link type="danger" @click="handleDeleteUnit(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="section">
          <div class="section-title">组织关系</div>
          <el-table :data="relationList" border v-loading="relationLoading">
            <el-table-column label="源节点" min-width="180">
              <template #default="{row}">
                <span>{{ row.sourceOrgUnitName || findUnitName(row.sourceOrgUnitId) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="目标节点" min-width="180">
              <template #default="{row}">
                <span>{{ row.targetOrgUnitName || findUnitName(row.targetOrgUnitId) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="relationType" label="关系类型" min-width="140"/>
            <el-table-column prop="relationName" label="关系名称" min-width="140"/>
            <el-table-column prop="sort" label="排序" width="80"/>
            <el-table-column label="状态" width="100">
              <template #default="{row}">
                <el-tag :type="row.status === 0 ? 'info' : 'success'">{{ row.status === 0 ? '停用' : '启用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip/>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{row}">
                <el-button link type="primary" @click="handleEditRelation(row)">编辑</el-button>
                <el-button link type="danger" @click="handleDeleteRelation(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </template>
    </el-card>

    <el-dialog v-model="typeDialogVisible" :title="editingTypeId ? '编辑组织类型' : '新增组织类型'" width="520px">
      <el-form label-width="110px">
        <el-form-item label="类型编码">
          <el-input v-model="typeForm.typeCode" :disabled="!!editingTypeId" placeholder="如 GOVERNMENT / SCHOOL / DEPARTMENT"/>
        </el-form-item>
        <el-form-item label="类型名称">
          <el-input v-model="typeForm.typeName" placeholder="请输入组织类型名称"/>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="typeForm.sort" :min="0" style="width: 100%"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="typeForm.status" :active-value="1" :inactive-value="0"/>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" type="textarea" :rows="3" placeholder="可用于说明行业语义，比如行政层级、学段、部门等"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveType">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="unitDialogVisible" :title="editingUnitId ? '编辑组织节点' : '新增组织节点'" width="680px">
      <el-form label-width="120px">
        <el-form-item label="组织编码">
          <el-input v-model="unitForm.unitCode" :disabled="!!editingUnitId" placeholder="如 HN_CS_YL / GRADE_01 / STORE_1001"/>
        </el-form-item>
        <el-form-item label="组织名称">
          <el-input v-model="unitForm.unitName" placeholder="请输入组织名称"/>
        </el-form-item>
        <el-form-item label="组织类型">
          <el-select v-model="unitForm.typeCode" style="width: 100%">
            <el-option v-for="item in typeList" :key="item.id" :label="`${item.typeName} (${item.typeCode})`" :value="item.typeCode"/>
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-select
            v-model="unitForm.leaderUserId"
            clearable
            filterable
            :loading="userLoading"
            style="width: 100%"
            placeholder="可选，用于标识该组织节点的负责人"
          >
            <el-option v-for="user in userOptions" :key="user.id" :label="formatUserLabel(user)" :value="user.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="上级节点">
          <el-select v-model="unitForm.parentId" clearable style="width: 100%" placeholder="不选则作为根节点">
            <el-option
              v-for="item in unitOptions.filter((option) => option.value !== editingUnitId)"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="组织标签">
          <el-select
            v-model="unitForm.tags"
            multiple
            filterable
            allow-create
            default-first-option
            collapse-tags
            collapse-tags-tooltip
            style="width: 100%"
            placeholder="输入后回车，可用于标识走读/住校、直营/加盟等"
          />
        </el-form-item>
        <el-form-item label="扩展属性">
          <el-input
            v-model="unitForm.attributeJson"
            type="textarea"
            :rows="4"
            placeholder='请输入 JSON，例如 {"campusType":"boarding","region":"华中"}'
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="unitForm.sort" :min="0" style="width: 100%"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="unitForm.status" :active-value="1" :inactive-value="0"/>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="unitForm.remark" type="textarea" :rows="3" placeholder="可记录组织定位、业务范围或补充说明"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="unitDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveUnit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="relationDialogVisible" :title="editingRelationId ? '编辑组织关系' : '新增组织关系'" width="620px">
      <el-form label-width="120px">
        <el-form-item label="源节点">
          <el-select v-model="relationForm.sourceOrgUnitId" filterable style="width: 100%">
            <el-option v-for="item in unitOptions" :key="`source-${item.value}`" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item label="目标节点">
          <el-select v-model="relationForm.targetOrgUnitId" filterable style="width: 100%">
            <el-option v-for="item in unitOptions" :key="`target-${item.value}`" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item label="关系类型">
          <el-select
            v-model="relationForm.relationType"
            filterable
            allow-create
            default-first-option
            style="width: 100%"
            placeholder="如 SUPERVISE / COOPERATE / SERVICE"
          >
            <el-option v-for="item in relationTypeOptions" :key="item.value" :label="`${item.label} (${item.value})`" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item label="关系名称">
          <el-input v-model="relationForm.relationName" placeholder="如 主管单位 / 协同单位 / 服务归口"/>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="relationForm.sort" :min="0" style="width: 100%"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="relationForm.status" :active-value="1" :inactive-value="0"/>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="relationForm.remark" type="textarea" :rows="3" placeholder="用于说明关系用途，例如行政主管、业务协同、矩阵归属"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="relationDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRelation">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #0f172a;
}

.page-desc {
  font-size: 14px;
  color: #64748b;
}

.card {
  border-radius: 16px;
}

.toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}

.toolbar-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}

.tips {
  font-size: 13px;
  color: #94a3b8;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section + .section {
  margin-top: 24px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.muted {
  color: #94a3b8;
}
</style>
