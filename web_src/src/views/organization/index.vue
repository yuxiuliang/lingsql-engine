<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import {useAuthStore} from '@/store/auth';
import {
  applyOrgTemplate,
  createOrgRelationExecution,
  createOrgRelation,
  createOrgUnit,
  createOrgUnitType,
  deleteOrgRelation,
  deleteOrgUnit,
  deleteOrgUnitType,
  fetchOrgRelationExecutions,
  fetchOrgRelationExecutionTimeline,
  fetchOrgRelationRouteResolve,
  fetchOrgRelationWorkbench,
  fetchOrgRelations,
  fetchOrgTemplates,
  fetchOrgUnits,
  fetchOrgUnitTypes,
  OrgRelationExecutionView,
  OrgRelationExecutionTimelineView,
  OrgRelationOpsTaskView,
  OrgRelationRouteResolveView,
  OrgRelationWorkbenchView,
  OrgTemplateView,
  OrgUnitRelationView,
  OrgUnitTypeView,
  OrgUnitView,
  updateOrgRelationExecution,
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

interface ExecutionCreateFormModel {
  sourceOrgUnitId?: number;
  relationType: string;
  relationId?: number;
  executionTitle: string;
  executionContent: string;
  priority: string;
  slaHours: number;
  dueTime: string;
  assigneeUserId: number | null;
}

interface ExecutionUpdateFormModel {
  status: string;
  priority: string;
  slaHours: number;
  dueTime: string;
  assigneeUserId: number | null;
  resultSummary: string;
}

const authStore = useAuthStore();
const loading = ref(false);
const typeLoading = ref(false);
const relationLoading = ref(false);
const workbenchLoading = ref(false);
const routePreviewLoading = ref(false);
const executionLoading = ref(false);
const executionTimelineLoading = ref(false);
const userLoading = ref(false);
const templateLoading = ref(false);
const templateApplying = ref(false);
const leaderSearchKeyword = ref('');
const unitDialogVisible = ref(false);
const typeDialogVisible = ref(false);
const relationDialogVisible = ref(false);
const templateDialogVisible = ref(false);
const executionDialogVisible = ref(false);
const executionUpdateDialogVisible = ref(false);
const executionTimelineDialogVisible = ref(false);
const editingUnitId = ref<number | null>(null);
const editingTypeId = ref<number | null>(null);
const editingRelationId = ref<number | null>(null);
const editingExecutionId = ref<number | null>(null);
const typeList = ref<OrgUnitTypeView[]>([]);
const orgTree = ref<OrgUnitView[]>([]);
const relationList = ref<OrgUnitRelationView[]>([]);
const relationWorkbench = ref<OrgRelationWorkbenchView | null>(null);
const routePreviewResult = ref<OrgRelationRouteResolveView | null>(null);
const executionList = ref<OrgRelationExecutionView[]>([]);
const executionTimelineList = ref<OrgRelationExecutionTimelineView[]>([]);
const userOptions = ref<UserView[]>([]);
const templateList = ref<OrgTemplateView[]>([]);
const selectedTemplateCode = ref('');
const templateRootUnitName = ref('');
const routePreviewForm = reactive({
  sourceOrgUnitId: undefined as number | undefined,
  relationType: ''
});

const relationTypeOptions = [
  {label: '主管', value: 'SUPERVISE'},
  {label: '协同', value: 'COOPERATE'},
  {label: '服务', value: 'SERVICE'},
  {label: '归口', value: 'OWNERSHIP'},
  {label: '矩阵', value: 'MATRIX'}
];

const relationTypeLabelMap: Record<string, string> = relationTypeOptions.reduce((mapping, item) => {
  mapping[item.value] = item.label;
  return mapping;
}, {} as Record<string, string>);

const readinessLabelMap: Record<string, string> = {
  READY: '已就绪',
  PENDING: '待补齐',
  BLOCKED: '阻塞'
};

const executionStatusLabelMap: Record<string, string> = {
  TODO: '待处理',
  IN_PROGRESS: '处理中',
  BLOCKED: '阻塞',
  DONE: '已完成'
};

const slaStatusLabelMap: Record<string, string> = {
  ON_TRACK: '进行中',
  DUE_SOON: '即将超时',
  OVERDUE: '已逾期',
  COMPLETED_ON_TIME: '按时完成',
  COMPLETED_LATE: '超时完成'
};

const executionTimelineEventLabelMap: Record<string, string> = {
  CREATED: '创建',
  UPDATED: '更新',
  ESCALATED: '升级'
};

const escalationStatusLabelMap: Record<string, string> = {
  NORMAL: '正常',
  DUE_SOON: '预警',
  ESCALATED: '已升级',
  RESOLVED: '已消警'
};

/**
 * 当前选中的模板。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const selectedTemplate = computed(() => {
  return templateList.value.find((item) => item.templateCode === selectedTemplateCode.value) || null;
});

/**
 * 当前页面是否适合展示会话关系语义。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const canDisplaySessionRelationScope = computed(() => {
  return !isSysAdmin.value && !!authStore.tenantId && activeTenantId.value === authStore.tenantId;
});

/**
 * 当前会话已生效的关系语义条目。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const relationScopeEntries = computed(() => {
  return relationTypeOptions
    .map((item) => ({
      relationType: item.value,
      label: item.label,
      codes: (authStore.relationScopes?.[item.value] || []).filter(Boolean)
    }))
    .filter((item) => item.codes.length > 0);
});

/**
 * 当前关系消费任务列表。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
const relationWorkbenchTasks = computed(() => relationWorkbench.value?.tasks || []);
const executionStats = computed(() => {
  const list = executionList.value || [];
  return {
    total: list.length,
    todo: list.filter((item) => item.status === 'TODO').length,
    inProgress: list.filter((item) => item.status === 'IN_PROGRESS').length,
    blocked: list.filter((item) => item.status === 'BLOCKED').length,
    done: list.filter((item) => item.status === 'DONE').length,
    overdue: list.filter((item) => item.overdue).length,
    escalated: list.filter((item) => item.escalationStatus === 'ESCALATED').length
  };
});

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

const executionCreateForm = reactive<ExecutionCreateFormModel>({
  relationType: '',
  executionTitle: '',
  executionContent: '',
  priority: 'P2',
  slaHours: 48,
  dueTime: '',
  assigneeUserId: null
});

const executionUpdateForm = reactive<ExecutionUpdateFormModel>({
  status: 'TODO',
  priority: 'P2',
  slaHours: 48,
  dueTime: '',
  assigneeUserId: null,
  resultSummary: ''
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
 * 将指定用户补入负责人候选，避免编辑态下已选负责人因搜索结果切换而丢失。
 *
 * @param user 用户信息
 * @author 余炘洋
 * @date 2026-04-11
 */
const upsertUserOption = (user?: Partial<UserView> | null) => {
  if (!user?.id) {
    return;
  }
  const normalized: UserView = {
    id: user.id,
    tenantId: user.tenantId || activeTenantId.value || '',
    username: user.username || String(user.id),
    nickname: user.nickname || user.username || ''
  };
  const mapping = new Map<number, UserView>();
  userOptions.value.forEach((item) => {
    mapping.set(item.id, item);
  });
  mapping.set(normalized.id, {
    ...(mapping.get(normalized.id) || normalized),
    ...normalized
  });
  userOptions.value = Array.from(mapping.values());
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
 * 重置执行项创建表单。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
const resetExecutionCreateForm = () => {
  Object.assign(executionCreateForm, {
    sourceOrgUnitId: undefined,
    relationType: '',
    relationId: undefined,
    executionTitle: '',
    executionContent: '',
    priority: 'P2',
    slaHours: 48,
    dueTime: '',
    assigneeUserId: null
  });
};

/**
 * 重置执行项更新表单。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
const resetExecutionUpdateForm = () => {
  Object.assign(executionUpdateForm, {
    status: 'TODO',
    priority: 'P2',
    slaHours: 48,
    dueTime: '',
    assigneeUserId: null,
    resultSummary: ''
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
const loadUsers = async (keyword = '', pinnedUser?: Partial<UserView>) => {
  if (!hasTenantSelected.value) {
    userOptions.value = [];
    return;
  }
  userLoading.value = true;
  try {
    const result = await fetchUsers({
      tenantId: activeTenantId.value || undefined,
      keyword: keyword.trim() || undefined,
      page: 1,
      size: 50
    });
    userOptions.value = result.list || [];
    upsertUserOption(pinnedUser);
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
 * 加载组织关系消费工作台。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
const loadRelationWorkbench = async () => {
  if (!hasTenantSelected.value) {
    relationWorkbench.value = null;
    return;
  }
  workbenchLoading.value = true;
  try {
    relationWorkbench.value = await fetchOrgRelationWorkbench(activeTenantId.value || undefined);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载关系消费工作台失败');
  } finally {
    workbenchLoading.value = false;
  }
};

/**
 * 加载组织关系执行台账。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
const loadExecutions = async () => {
  if (!hasTenantSelected.value) {
    executionList.value = [];
    return;
  }
  executionLoading.value = true;
  try {
    executionList.value = await fetchOrgRelationExecutions(activeTenantId.value || undefined);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载执行台账失败');
  } finally {
    executionLoading.value = false;
  }
};

/**
 * 加载执行轨迹。
 *
 * @param executionId 执行项ID
 * @author 余炘洋
 * @date 2026-04-12
 */
const loadExecutionTimeline = async (executionId: number) => {
  executionTimelineLoading.value = true;
  try {
    executionTimelineList.value = await fetchOrgRelationExecutionTimeline(executionId);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载执行轨迹失败');
  } finally {
    executionTimelineLoading.value = false;
  }
};

/**
 * 解析组织关系路由预览。
 *
 * @param sourceOrgUnitId 源组织ID
 * @param relationType    关系类型
 * @author 余炘洋
 * @date 2026-04-12
 */
const handleResolveRoutePreview = async (sourceOrgUnitId?: number, relationType?: string) => {
  const resolvedSourceOrgUnitId = sourceOrgUnitId ?? routePreviewForm.sourceOrgUnitId;
  const resolvedRelationType = (relationType ?? routePreviewForm.relationType).trim();
  if (!resolvedSourceOrgUnitId || !resolvedRelationType) {
    ElMessage.warning('请先选择源组织和关系类型');
    return;
  }
  routePreviewForm.sourceOrgUnitId = resolvedSourceOrgUnitId;
  routePreviewForm.relationType = resolvedRelationType;
  routePreviewLoading.value = true;
  try {
    routePreviewResult.value = await fetchOrgRelationRouteResolve(
      resolvedSourceOrgUnitId,
      resolvedRelationType,
      activeTenantId.value || undefined
    );
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '解析组织路由失败');
  } finally {
    routePreviewLoading.value = false;
  }
};

/**
 * 打开执行项创建弹窗。
 *
 * @param options 预填信息
 * @author 余炘洋
 * @date 2026-04-12
 */
const openExecutionCreateDialog = async (options: {
  sourceOrgUnitId?: number;
  relationType?: string;
  relationId?: number;
  sceneName?: string;
  targetOrgUnitName?: string;
  assigneeUserId?: number;
  assigneeName?: string;
}) => {
  resetExecutionCreateForm();
  executionCreateForm.sourceOrgUnitId = options.sourceOrgUnitId;
  executionCreateForm.relationType = options.relationType || '';
  executionCreateForm.relationId = options.relationId;
  executionCreateForm.executionTitle = `${options.sceneName || relationTypeLabelMap[options.relationType || ''] || options.relationType || '关系执行'} - ${options.targetOrgUnitName || '目标组织'}`;
  executionCreateForm.priority = options.relationType === 'SERVICE' || options.relationType === 'SUPERVISE' || options.relationType === 'MATRIX'
    ? 'P1'
    : 'P2';
  executionCreateForm.slaHours = resolveDefaultSlaHours(executionCreateForm.priority);
  executionCreateForm.dueTime = buildDefaultDueTime(executionCreateForm.slaHours);
  executionCreateForm.assigneeUserId = options.assigneeUserId ?? null;
  if (options.assigneeUserId) {
    await loadUsers('', {
      id: options.assigneeUserId,
      tenantId: activeTenantId.value || '',
      username: options.assigneeName || String(options.assigneeUserId),
      nickname: options.assigneeName || ''
    });
  } else {
    await loadUsers();
  }
  executionDialogVisible.value = true;
};

/**
 * 从工作台任务创建执行项。
 *
 * @param task 任务
 * @author 余炘洋
 * @date 2026-04-12
 */
const handleOpenExecutionFromTask = async (task: OrgRelationOpsTaskView) => {
  const relation = relationList.value.find((item) => item.id === task.relationId);
  if (!relation?.sourceOrgUnitId || !task.relationType) {
    ElMessage.warning('当前任务缺少可用路由信息，无法创建执行项');
    return;
  }
  await openExecutionCreateDialog({
    sourceOrgUnitId: relation.sourceOrgUnitId,
    relationType: task.relationType,
    relationId: task.relationId,
    sceneName: task.sceneName,
    targetOrgUnitName: task.targetOrgUnitName,
    assigneeUserId: task.targetLeaderUserId,
    assigneeName: task.targetLeaderName
  });
};

/**
 * 从路由解析主结果创建执行项。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
const handleOpenExecutionFromRoutePrimary = async () => {
  if (!routePreviewResult.value?.sourceOrgUnitId || !routePreviewResult.value?.relationType || !routePreviewResult.value?.primaryRelationId) {
    ElMessage.warning('当前没有可用的主路由结果');
    return;
  }
  await openExecutionCreateDialog({
    sourceOrgUnitId: routePreviewResult.value.sourceOrgUnitId,
    relationType: routePreviewResult.value.relationType,
    relationId: routePreviewResult.value.primaryRelationId,
    sceneName: relationTypeLabelMap[routePreviewResult.value.relationType] || routePreviewResult.value.relationType,
    targetOrgUnitName: routePreviewResult.value.primaryTargetOrgUnitName,
    assigneeUserId: routePreviewResult.value.primaryLeaderUserId,
    assigneeName: routePreviewResult.value.primaryLeaderName
  });
};

/**
 * 从路由候选创建执行项。
 *
 * @param relationId 候选关系ID
 * @param targetOrgUnitName 目标组织名称
 * @param assigneeUserId 处理人ID
 * @param assigneeName 处理人名称
 * @author 余炘洋
 * @date 2026-04-12
 */
const handleOpenExecutionFromRouteCandidate = async (
  relationId?: number,
  targetOrgUnitName?: string,
  assigneeUserId?: number,
  assigneeName?: string
) => {
  if (!routePreviewResult.value?.sourceOrgUnitId || !routePreviewResult.value?.relationType || !relationId) {
    ElMessage.warning('当前候选路由缺少必要信息');
    return;
  }
  await openExecutionCreateDialog({
    sourceOrgUnitId: routePreviewResult.value.sourceOrgUnitId,
    relationType: routePreviewResult.value.relationType,
    relationId,
    sceneName: relationTypeLabelMap[routePreviewResult.value.relationType] || routePreviewResult.value.relationType,
    targetOrgUnitName,
    assigneeUserId,
    assigneeName
  });
};

/**
 * 保存执行项创建。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
const handleSaveExecutionCreate = async () => {
  if (!executionCreateForm.sourceOrgUnitId || !executionCreateForm.relationType || !executionCreateForm.executionTitle.trim()) {
    ElMessage.warning('请补全源组织、关系类型和执行标题');
    return;
  }
  try {
    await createOrgRelationExecution({
      tenantId: activeTenantId.value || undefined,
      sourceOrgUnitId: executionCreateForm.sourceOrgUnitId,
      relationType: executionCreateForm.relationType,
      relationId: executionCreateForm.relationId,
      executionTitle: executionCreateForm.executionTitle.trim(),
      executionContent: executionCreateForm.executionContent.trim() || undefined,
      priority: executionCreateForm.priority,
      slaHours: executionCreateForm.slaHours,
      dueTime: executionCreateForm.dueTime || undefined,
      assigneeUserId: executionCreateForm.assigneeUserId || undefined
    });
    executionDialogVisible.value = false;
    ElMessage.success('执行项已创建');
    await loadExecutions();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建执行项失败');
  }
};

/**
 * 打开执行项更新弹窗。
 *
 * @param row 执行项
 * @author 余炘洋
 * @date 2026-04-12
 */
const handleEditExecution = async (row: OrgRelationExecutionView) => {
  editingExecutionId.value = row.id;
  resetExecutionUpdateForm();
  executionUpdateForm.status = row.status || 'TODO';
  executionUpdateForm.priority = row.priority || 'P2';
  executionUpdateForm.slaHours = row.slaHours || resolveDefaultSlaHours(row.priority);
  executionUpdateForm.dueTime = row.dueTime || '';
  executionUpdateForm.assigneeUserId = row.assigneeUserId ?? null;
  executionUpdateForm.resultSummary = row.resultSummary || '';
  if (row.assigneeUserId) {
    await loadUsers('', {
      id: row.assigneeUserId,
      tenantId: activeTenantId.value || '',
      username: row.assigneeName || String(row.assigneeUserId),
      nickname: row.assigneeName || ''
    });
  } else {
    await loadUsers();
  }
  executionUpdateDialogVisible.value = true;
};

/**
 * 打开执行轨迹弹窗。
 *
 * @param row 执行项
 * @author 余炘洋
 * @date 2026-04-12
 */
const handleViewExecutionTimeline = async (row: OrgRelationExecutionView) => {
  if (!row.id) {
    ElMessage.warning('未找到可查看的执行项');
    return;
  }
  executionTimelineList.value = [];
  executionTimelineDialogVisible.value = true;
  await loadExecutionTimeline(row.id);
};

/**
 * 保存执行项更新。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
const handleSaveExecutionUpdate = async () => {
  if (!editingExecutionId.value) {
    ElMessage.warning('未找到要更新的执行项');
    return;
  }
  if (!executionUpdateForm.status) {
    ElMessage.warning('请选择执行状态');
    return;
  }
  try {
    await updateOrgRelationExecution(editingExecutionId.value, {
      status: executionUpdateForm.status,
      priority: executionUpdateForm.priority,
      slaHours: executionUpdateForm.slaHours,
      dueTime: executionUpdateForm.dueTime || undefined,
      assigneeUserId: executionUpdateForm.assigneeUserId || undefined,
      resultSummary: executionUpdateForm.resultSummary.trim() || undefined
    });
    executionUpdateDialogVisible.value = false;
    ElMessage.success('执行项已更新');
    await loadExecutions();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新执行项失败');
  }
};

/**
 * 远程搜索负责人候选。
 *
 * @param keyword 关键字
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleLeaderSearch = (keyword: string) => {
  leaderSearchKeyword.value = keyword;
  void loadUsers(keyword);
};

/**
 * 加载组织模板列表。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const loadTemplates = async () => {
  templateLoading.value = true;
  try {
    templateList.value = await fetchOrgTemplates();
    if (!selectedTemplateCode.value && templateList.value.length > 0) {
      selectedTemplateCode.value = templateList.value[0].templateCode;
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载组织模板失败');
  } finally {
    templateLoading.value = false;
  }
};

/**
 * 打开模板初始化弹窗。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleOpenTemplateDialog = async () => {
  if (!hasTenantSelected.value) {
    ElMessage.warning('请先选择要管理的租户');
    return;
  }
  await loadTemplates();
  templateRootUnitName.value = authStore.activeTenantName || authStore.tenantName || '';
  templateDialogVisible.value = true;
};

/**
 * 应用当前选中的组织模板。
 *
 * @author 余炘洋
 * @date 2026-04-11
 */
const handleApplyTemplate = async () => {
  if (!selectedTemplateCode.value) {
    ElMessage.warning('请先选择一个组织模板');
    return;
  }
  templateApplying.value = true;
  try {
    const message = await applyOrgTemplate({
      tenantId: activeTenantId.value || undefined,
      templateCode: selectedTemplateCode.value,
      rootUnitName: templateRootUnitName.value.trim() || undefined
    });
    ElMessage.success(message || '组织模板初始化成功');
    templateDialogVisible.value = false;
    await refreshData();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '初始化组织模板失败');
  } finally {
    templateApplying.value = false;
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
    relationWorkbench.value = null;
    executionList.value = [];
    routePreviewResult.value = null;
    routePreviewForm.sourceOrgUnitId = undefined;
    routePreviewForm.relationType = '';
    userOptions.value = [];
    return;
  }
  await Promise.all([loadTypes(), loadOrgTree(), loadRelations(), loadRelationWorkbench(), loadExecutions()]);
};

/**
 * 解析优先级标签样式。
 *
 * @param priority 优先级
 * @return 标签样式
 * @author 余炘洋
 * @date 2026-04-12
 */
const resolvePriorityTagType = (priority?: string) => {
  if (priority === 'P0') {
    return 'danger';
  }
  if (priority === 'P1') {
    return 'warning';
  }
  return 'info';
};

/**
 * 解析优先级默认 SLA 时长。
 *
 * @param priority 优先级
 * @return SLA 小时数
 * @author 余炘洋
 * @date 2026-04-12
 */
const resolveDefaultSlaHours = (priority?: string) => {
  if (priority === 'P0') {
    return 4;
  }
  if (priority === 'P1') {
    return 24;
  }
  return 48;
};

/**
 * 解析就绪状态文案。
 *
 * @param readinessStatus 就绪状态
 * @return 文案
 * @author 余炘洋
 * @date 2026-04-12
 */
const resolveReadinessLabel = (readinessStatus?: string) => {
  return readinessLabelMap[readinessStatus || ''] || '待确认';
};

/**
 * 解析就绪状态标签样式。
 *
 * @param readinessStatus 就绪状态
 * @return 标签样式
 * @author 余炘洋
 * @date 2026-04-12
 */
const resolveReadinessTagType = (readinessStatus?: string) => {
  if (readinessStatus === 'READY') {
    return 'success';
  }
  if (readinessStatus === 'BLOCKED') {
    return 'danger';
  }
  return 'warning';
};

/**
 * 解析消费任务的目标负责人。
 *
 * @param task 消费任务
 * @return 负责人文案
 * @author 余炘洋
 * @date 2026-04-12
 */
const resolveTargetLeaderText = (task: OrgRelationOpsTaskView) => {
  return task.targetLeaderName || '未配置';
};

/**
 * 解析路由结果主负责人文案。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
const resolveRoutePreviewLeaderText = () => {
  return routePreviewResult.value?.primaryLeaderName || '未配置';
};

/**
 * 解析执行状态文案。
 *
 * @param status 状态
 * @return 文案
 * @author 余炘洋
 * @date 2026-04-12
 */
const resolveExecutionStatusLabel = (status?: string) => {
  return executionStatusLabelMap[status || ''] || '待确认';
};

/**
 * 解析执行状态标签样式。
 *
 * @param status 状态
 * @return 标签样式
 * @author 余炘洋
 * @date 2026-04-12
 */
const resolveExecutionStatusTagType = (status?: string) => {
  if (status === 'DONE') {
    return 'success';
  }
  if (status === 'IN_PROGRESS') {
    return 'primary';
  }
  if (status === 'BLOCKED') {
    return 'danger';
  }
  return 'info';
};

/**
 * 解析执行轨迹事件文案。
 *
 * @param eventType 事件类型
 * @return 文案
 * @author 余炘洋
 * @date 2026-04-12
 */
const resolveExecutionTimelineEventLabel = (eventType?: string) => {
  return executionTimelineEventLabelMap[eventType || ''] || '轨迹';
};

/**
 * 解析升级状态文案。
 *
 * @param escalationStatus 升级状态
 * @return 文案
 * @author 余炘洋
 * @date 2026-04-12
 */
const resolveEscalationStatusLabel = (escalationStatus?: string) => {
  return escalationStatusLabelMap[escalationStatus || ''] || '正常';
};

/**
 * 解析升级状态标签样式。
 *
 * @param escalationStatus 升级状态
 * @return 标签样式
 * @author 余炘洋
 * @date 2026-04-12
 */
const resolveEscalationStatusTagType = (escalationStatus?: string) => {
  if (escalationStatus === 'ESCALATED') {
    return 'danger';
  }
  if (escalationStatus === 'DUE_SOON') {
    return 'warning';
  }
  if (escalationStatus === 'RESOLVED') {
    return 'success';
  }
  return 'info';
};

/**
 * 解析 SLA 状态文案。
 *
 * @param slaStatus SLA 状态
 * @return 文案
 * @author 余炘洋
 * @date 2026-04-12
 */
const resolveSlaStatusLabel = (slaStatus?: string) => {
  return slaStatusLabelMap[slaStatus || ''] || '未设置';
};

/**
 * 解析 SLA 状态标签样式。
 *
 * @param slaStatus SLA 状态
 * @return 标签样式
 * @author 余炘洋
 * @date 2026-04-12
 */
const resolveSlaStatusTagType = (slaStatus?: string) => {
  if (slaStatus === 'OVERDUE' || slaStatus === 'COMPLETED_LATE') {
    return 'danger';
  }
  if (slaStatus === 'DUE_SOON') {
    return 'warning';
  }
  if (slaStatus === 'COMPLETED_ON_TIME') {
    return 'success';
  }
  return 'info';
};

/**
 * 计算默认截止时间。
 *
 * @param slaHours SLA 小时数
 * @return ISO 本地时间
 * @author 余炘洋
 * @date 2026-04-12
 */
const buildDefaultDueTime = (slaHours: number) => {
  const date = new Date();
  date.setMinutes(0, 0, 0);
  date.setHours(date.getHours() + Math.max(1, slaHours || 1));
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  const hour = `${date.getHours()}`.padStart(2, '0');
  const minute = `${date.getMinutes()}`.padStart(2, '0');
  const second = `${date.getSeconds()}`.padStart(2, '0');
  return `${year}-${month}-${day}T${hour}:${minute}:${second}`;
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
  leaderSearchKeyword.value = '';
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
  leaderSearchKeyword.value = '';
  await loadUsers('', row.leaderUserId ? {
    id: row.leaderUserId,
    tenantId: row.tenantId,
    username: row.leaderUsername || String(row.leaderUserId),
    nickname: row.leaderNickname || row.leaderUsername || ''
  } : undefined);
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
    await Promise.all([loadOrgTree(), loadRelations(), loadRelationWorkbench()]);
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
    await Promise.all([loadOrgTree(), loadRelations(), loadRelationWorkbench()]);
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
    await Promise.all([loadRelations(), loadRelationWorkbench()]);
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
    await Promise.all([loadRelations(), loadRelationWorkbench()]);
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
    templateDialogVisible.value = false;
    executionDialogVisible.value = false;
    executionUpdateDialogVisible.value = false;
    executionTimelineDialogVisible.value = false;
    editingTypeId.value = null;
    editingUnitId.value = null;
    editingRelationId.value = null;
    editingExecutionId.value = null;
    templateList.value = [];
    selectedTemplateCode.value = '';
    templateRootUnitName.value = '';
    relationWorkbench.value = null;
    executionList.value = [];
    executionTimelineList.value = [];
    routePreviewResult.value = null;
    routePreviewForm.sourceOrgUnitId = undefined;
    routePreviewForm.relationType = '';
    resetExecutionCreateForm();
    resetExecutionUpdateForm();
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
          <el-button type="success" :disabled="!hasTenantSelected" @click="handleOpenTemplateDialog">初始化模板</el-button>
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
          <div class="section-title">当前会话关系语义</div>
          <template v-if="canDisplaySessionRelationScope">
            <el-alert
              v-if="relationScopeEntries.length === 0"
              type="info"
              :closable="false"
              title="当前会话尚未命中已生效的跨组织关系，现阶段仍按直属组织与数据权限规则执行。"
            />
            <div v-else class="relation-scope-grid">
              <div v-for="item in relationScopeEntries" :key="item.relationType" class="relation-scope-card">
                <div class="relation-scope-head">
                  <div class="relation-scope-title">{{ item.label }}</div>
                  <el-tag size="small" effect="plain">{{ item.codes.length }} 个组织编码</el-tag>
                </div>
                <div class="relation-scope-code-list">
                  <el-tag
                    v-for="code in item.codes"
                    :key="`${item.relationType}-${code}`"
                    size="small"
                    effect="light"
                    class="relation-scope-code"
                  >
                    {{ code }}
                  </el-tag>
                </div>
                <div class="relation-scope-desc">
                  {{ relationTypeLabelMap[item.relationType] || item.relationType }}关系已进入当前登录会话，可被现有数据权限链直接消费。
                </div>
              </div>
            </div>
          </template>
          <el-alert
            v-else
            type="warning"
            :closable="false"
            title="当前页展示的是租户组织管理视角；系统管理员跨租户切换时，不展示个人会话关系语义，避免与所选租户产生歧义。"
          />
        </div>

        <div class="section">
          <div class="section-title">关系消费工作台</div>
          <div class="workbench-layout" v-loading="workbenchLoading">
            <div class="workbench-summary">
              <div class="workbench-stat">
                <div class="workbench-stat-label">总任务</div>
                <div class="workbench-stat-value">{{ relationWorkbench?.totalTasks || 0 }}</div>
              </div>
              <div class="workbench-stat danger">
                <div class="workbench-stat-label">P0</div>
                <div class="workbench-stat-value">{{ relationWorkbench?.p0Count || 0 }}</div>
              </div>
              <div class="workbench-stat warning">
                <div class="workbench-stat-label">P1</div>
                <div class="workbench-stat-value">{{ relationWorkbench?.p1Count || 0 }}</div>
              </div>
              <div class="workbench-stat info">
                <div class="workbench-stat-label">P2</div>
                <div class="workbench-stat-value">{{ relationWorkbench?.p2Count || 0 }}</div>
              </div>
            </div>

            <el-empty
              v-if="!workbenchLoading && relationWorkbenchTasks.length === 0"
              description="当前租户暂无可消费的组织关系任务，新增启用中的跨组织关系后会自动生成建议。"
            />

            <div v-else class="workbench-grid">
              <div v-for="task in relationWorkbenchTasks" :key="task.relationId || `${task.sceneCode}-${task.targetOrgUnitCode}`" class="workbench-card">
                <div class="workbench-card-head">
                  <div>
                    <div class="workbench-card-title">{{ task.relationName || task.sceneName || task.relationType }}</div>
                    <div class="workbench-card-subtitle">
                      {{ task.sourceOrgUnitName || '-' }} -> {{ task.targetOrgUnitName || '-' }}
                    </div>
                  </div>
                  <div class="workbench-tag-list">
                    <el-tag size="small" :type="resolvePriorityTagType(task.priority)">{{ task.priority || 'P2' }}</el-tag>
                    <el-tag size="small" effect="plain" :type="resolveReadinessTagType(task.readinessStatus)">
                      {{ resolveReadinessLabel(task.readinessStatus) }}
                    </el-tag>
                  </div>
                </div>
                <div class="workbench-scene">{{ task.sceneName || '关系消费' }} · {{ task.sceneCode || 'ORG_RELATION' }}</div>
                <div class="workbench-action">{{ task.suggestedAction || '建议补齐业务消费入口。' }}</div>
                <div class="workbench-meta-grid">
                  <div class="workbench-meta-item">
                    <div class="workbench-meta-label">路由范围</div>
                    <div class="workbench-meta-value">{{ task.routeScopeSummary || '-' }}</div>
                  </div>
                  <div class="workbench-meta-item">
                    <div class="workbench-meta-label">目标负责人</div>
                    <div class="workbench-meta-value">{{ resolveTargetLeaderText(task) }}</div>
                  </div>
                </div>
                <div v-if="task.missingCapabilities?.length" class="workbench-missing">
                  <el-tag
                    v-for="item in task.missingCapabilities"
                    :key="`${task.relationId}-${item}`"
                    size="small"
                    effect="light"
                    type="warning"
                  >
                    {{ item }}
                  </el-tag>
                </div>
                <div class="workbench-trigger">{{ task.triggerReason || '暂无触发原因' }}</div>
                <div class="workbench-footer">
                  <el-button
                    size="small"
                    type="success"
                    plain
                    @click="handleOpenExecutionFromTask(task)"
                  >
                    创建执行项
                  </el-button>
                  <el-button
                    size="small"
                    type="primary"
                    plain
                    @click="handleResolveRoutePreview(
                      relationList.find((item) => item.id === task.relationId)?.sourceOrgUnitId,
                      task.relationType
                    )"
                  >
                    解析路由
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="section">
          <div class="section-title">路由解析预览</div>
          <div class="route-preview-panel">
            <div class="route-preview-toolbar">
              <el-select v-model="routePreviewForm.sourceOrgUnitId" filterable placeholder="选择源组织" style="min-width: 240px">
                <el-option v-for="item in unitOptions" :key="`preview-${item.value}`" :label="item.label" :value="item.value"/>
              </el-select>
              <el-select v-model="routePreviewForm.relationType" filterable placeholder="选择关系类型" style="min-width: 220px">
                <el-option v-for="item in relationTypeOptions" :key="`preview-type-${item.value}`" :label="`${item.label} (${item.value})`" :value="item.value"/>
              </el-select>
              <el-button type="primary" :loading="routePreviewLoading" @click="handleResolveRoutePreview()">开始解析</el-button>
            </div>

            <el-empty
              v-if="!routePreviewLoading && !routePreviewResult"
              description="选择源组织与关系类型后，可直接预览主路由、候选目标和负责人。"
            />

            <div v-else class="route-preview-result" v-loading="routePreviewLoading">
              <el-alert
                v-if="routePreviewResult && !routePreviewResult.matched"
                type="warning"
                :closable="false"
                :title="(routePreviewResult.warnings && routePreviewResult.warnings[0]) || '当前未匹配到可用路由。'"
              />

              <template v-else-if="routePreviewResult">
                <div class="route-preview-head">
                  <div>
                    <div class="route-preview-title">
                      {{ routePreviewResult.sourceOrgUnitName || '-' }} / {{ relationTypeLabelMap[routePreviewResult.relationType || ''] || routePreviewResult.relationType || '-' }}
                    </div>
                    <div class="route-preview-subtitle">
                      候选 {{ routePreviewResult.candidateCount || 0 }} 条
                    </div>
                  </div>
                  <div class="workbench-tag-list">
                    <el-tag size="small" type="success">主路由</el-tag>
                    <el-tag v-if="routePreviewResult.ambiguous" size="small" type="warning">存在歧义</el-tag>
                  </div>
                </div>

                <div class="route-primary-card">
                  <div class="route-primary-title">{{ routePreviewResult.primaryRelationName || routePreviewResult.relationType }}</div>
                  <div class="route-primary-target">
                    {{ routePreviewResult.primaryTargetOrgUnitName || '-' }} [{{ routePreviewResult.primaryTargetOrgUnitCode || '-' }}]
                  </div>
                  <div class="route-primary-meta-grid">
                    <div class="route-primary-meta-item">
                      <div class="route-primary-meta-label">负责人</div>
                      <div class="route-primary-meta-value">{{ resolveRoutePreviewLeaderText() }}</div>
                    </div>
                    <div class="route-primary-meta-item">
                      <div class="route-primary-meta-label">覆盖范围</div>
                      <div class="route-primary-meta-value">{{ routePreviewResult.primaryRouteScopeSummary || '-' }}</div>
                    </div>
                  </div>
                  <div v-if="routePreviewResult.primaryScopeUnitCodes?.length" class="route-scope-codes">
                    <el-tag
                      v-for="code in routePreviewResult.primaryScopeUnitCodes"
                      :key="`scope-${code}`"
                      size="small"
                      effect="plain"
                    >
                      {{ code }}
                    </el-tag>
                  </div>
                  <div v-if="routePreviewResult.warnings?.length" class="route-warning-list">
                    <el-alert
                      v-for="warning in routePreviewResult.warnings"
                      :key="warning"
                      type="warning"
                      :closable="false"
                      :title="warning"
                    />
                  </div>
                  <div class="workbench-footer">
                    <el-button
                      size="small"
                      type="success"
                      plain
                      @click="handleOpenExecutionFromRoutePrimary()"
                    >
                      创建执行项
                    </el-button>
                  </div>
                </div>

                <div v-if="(routePreviewResult.candidates || []).length > 1" class="route-candidate-list">
                  <div class="route-candidate-title">候选目标</div>
                  <el-table :data="routePreviewResult.candidates || []" border>
                    <el-table-column prop="relationName" label="关系名称" min-width="140"/>
                    <el-table-column label="目标组织" min-width="180">
                      <template #default="{row}">
                        <span>{{ row.targetOrgUnitName || '-' }} [{{ row.targetOrgUnitCode || '-' }}]</span>
                      </template>
                    </el-table-column>
                    <el-table-column prop="targetLeaderName" label="负责人" min-width="160"/>
                    <el-table-column prop="scopeUnitCount" label="覆盖节点数" width="110"/>
                    <el-table-column prop="sort" label="排序" width="90"/>
                    <el-table-column label="操作" width="120">
                      <template #default="{row}">
                        <el-button
                          link
                          type="success"
                          @click="handleOpenExecutionFromRouteCandidate(row.relationId, row.targetOrgUnitName, row.targetLeaderUserId, row.targetLeaderName)"
                        >
                          按此创建
                        </el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </template>
            </div>
          </div>
        </div>

        <div class="section">
          <div class="section-title">执行台账</div>
          <div class="workbench-layout" v-loading="executionLoading">
            <div class="workbench-summary execution-summary">
              <div class="workbench-stat">
                <div class="workbench-stat-label">总执行项</div>
                <div class="workbench-stat-value">{{ executionStats.total }}</div>
              </div>
              <div class="workbench-stat info">
                <div class="workbench-stat-label">待处理</div>
                <div class="workbench-stat-value">{{ executionStats.todo }}</div>
              </div>
              <div class="workbench-stat warning">
                <div class="workbench-stat-label">处理中</div>
                <div class="workbench-stat-value">{{ executionStats.inProgress }}</div>
              </div>
              <div class="workbench-stat danger">
                <div class="workbench-stat-label">阻塞</div>
                <div class="workbench-stat-value">{{ executionStats.blocked }}</div>
              </div>
              <div class="workbench-stat danger">
                <div class="workbench-stat-label">已逾期</div>
                <div class="workbench-stat-value">{{ executionStats.overdue }}</div>
              </div>
              <div class="workbench-stat danger">
                <div class="workbench-stat-label">已升级</div>
                <div class="workbench-stat-value">{{ executionStats.escalated }}</div>
              </div>
            </div>
            <el-empty
              v-if="!executionLoading && executionList.length === 0"
              description="当前暂无执行台账，可从工作台或路由解析结果直接创建执行项。"
            />
            <el-table v-else :data="executionList" border>
              <el-table-column prop="executionTitle" label="执行标题" min-width="180"/>
              <el-table-column label="场景" min-width="140">
                <template #default="{row}">
                  <span>{{ row.sceneName || row.sceneCode || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="来源/目标" min-width="220">
                <template #default="{row}">
                  <span>{{ row.sourceOrgUnitName || '-' }} -> {{ row.targetOrgUnitName || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{row}">
                  <el-tag :type="resolveExecutionStatusTagType(row.status)">{{ resolveExecutionStatusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="优先级" width="90">
                <template #default="{row}">
                  <el-tag :type="resolvePriorityTagType(row.priority)">{{ row.priority || 'P2' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="SLA" width="110">
                <template #default="{row}">
                  <span>{{ row.slaHours || '-' }}h</span>
                </template>
              </el-table-column>
              <el-table-column label="SLA 状态" width="120">
                <template #default="{row}">
                  <el-tag :type="resolveSlaStatusTagType(row.slaStatus)">{{ resolveSlaStatusLabel(row.slaStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="升级状态" width="120">
                <template #default="{row}">
                  <el-tag :type="resolveEscalationStatusTagType(row.escalationStatus)">{{ resolveEscalationStatusLabel(row.escalationStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="升级级别" width="100">
                <template #default="{row}">
                  <span>{{ row.escalationLevel ?? 0 }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="dueTime" label="截止时间" min-width="180"/>
              <el-table-column prop="lastEscalatedTime" label="最近升级时间" min-width="180"/>
              <el-table-column prop="assigneeName" label="处理人" min-width="150"/>
              <el-table-column prop="requestedByName" label="创建人" min-width="150"/>
              <el-table-column prop="resultSummary" label="处理结果" min-width="180" show-overflow-tooltip/>
              <el-table-column prop="updateTime" label="更新时间" min-width="180"/>
              <el-table-column label="操作" width="170" fixed="right">
                <template #default="{row}">
                  <el-button link type="primary" @click="handleEditExecution(row)">更新进度</el-button>
                  <el-button link type="info" @click="handleViewExecutionTimeline(row)">查看轨迹</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
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

    <el-dialog v-model="templateDialogVisible" title="初始化组织模板" width="760px" destroy-on-close>
      <el-alert
        type="warning"
        :closable="false"
        title="模板初始化仅适用于空组织域租户，若当前租户已存在组织类型、节点或关系，后端会直接拒绝执行。"
      />
      <div class="template-layout" v-loading="templateLoading">
        <div class="template-list">
          <button
            v-for="item in templateList"
            :key="item.templateCode"
            type="button"
            class="template-card"
            :class="{active: selectedTemplateCode === item.templateCode}"
            @click="selectedTemplateCode = item.templateCode"
          >
            <div class="template-card-title">{{ item.templateName }}</div>
            <div class="template-card-industry">{{ item.industry || '通用' }}</div>
            <div class="template-card-meta">
              {{ item.typeCount || 0 }} 类 / {{ item.unitCount || 0 }} 节点 / {{ item.relationCount || 0 }} 关系
            </div>
          </button>
        </div>
        <div class="template-detail" v-if="selectedTemplate">
          <div class="template-detail-title">{{ selectedTemplate.templateName }}</div>
          <div class="template-detail-desc">{{ selectedTemplate.description || '暂无模板说明' }}</div>
          <el-form label-width="110px" class="template-form">
            <el-form-item label="根组织名称">
              <el-input
                v-model="templateRootUnitName"
                :placeholder="`留空则使用模板默认值 ${selectedTemplate.defaultRootUnitName || '根组织'}`"
              />
              <div class="muted">用于覆盖模板中的默认主体名称，适合将“政务中心 / 第一学校 / 集团总部”替换成客户自己的根组织名称。</div>
            </el-form-item>
          </el-form>
          <div class="template-stat-grid">
            <div class="template-stat-item">
              <div class="template-stat-label">适用行业</div>
              <div class="template-stat-value">{{ selectedTemplate.industry || '通用' }}</div>
            </div>
            <div class="template-stat-item">
              <div class="template-stat-label">组织类型</div>
              <div class="template-stat-value">{{ selectedTemplate.typeCount || 0 }}</div>
            </div>
            <div class="template-stat-item">
              <div class="template-stat-label">组织节点</div>
              <div class="template-stat-value">{{ selectedTemplate.unitCount || 0 }}</div>
            </div>
            <div class="template-stat-item">
              <div class="template-stat-label">组织关系</div>
              <div class="template-stat-value">{{ selectedTemplate.relationCount || 0 }}</div>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="templateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="templateApplying" @click="handleApplyTemplate">开始初始化</el-button>
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
            remote
            reserve-keyword
            :loading="userLoading"
            :remote-method="handleLeaderSearch"
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

    <el-dialog v-model="executionDialogVisible" title="创建执行项" width="680px">
      <el-form label-width="120px">
        <el-form-item label="源组织">
          <el-select v-model="executionCreateForm.sourceOrgUnitId" filterable style="width: 100%">
            <el-option v-for="item in unitOptions" :key="`execution-source-${item.value}`" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item label="关系类型">
          <el-select v-model="executionCreateForm.relationType" filterable style="width: 100%">
            <el-option v-for="item in relationTypeOptions" :key="`execution-type-${item.value}`" :label="`${item.label} (${item.value})`" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item label="执行标题">
          <el-input v-model="executionCreateForm.executionTitle" placeholder="请输入执行标题"/>
        </el-form-item>
        <el-form-item label="执行内容">
          <el-input v-model="executionCreateForm.executionContent" type="textarea" :rows="4" placeholder="请输入执行说明、协同背景或服务要求"/>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="executionCreateForm.priority" style="width: 100%">
            <el-option label="P0" value="P0"/>
            <el-option label="P1" value="P1"/>
            <el-option label="P2" value="P2"/>
          </el-select>
        </el-form-item>
        <el-form-item label="SLA(小时)">
          <el-input-number v-model="executionCreateForm.slaHours" :min="1" :max="720" style="width: 100%"/>
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker
            v-model="executionCreateForm.dueTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
            placeholder="不填则按当前时间 + SLA 自动生成"
          />
        </el-form-item>
        <el-form-item label="处理人">
          <el-select
            v-model="executionCreateForm.assigneeUserId"
            clearable
            filterable
            remote
            reserve-keyword
            :loading="userLoading"
            :remote-method="handleLeaderSearch"
            style="width: 100%"
            placeholder="可选，留空则由后端按路由负责人或阻塞规则处理"
          >
            <el-option v-for="user in userOptions" :key="`execution-user-${user.id}`" :label="formatUserLabel(user)" :value="user.id"/>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="executionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveExecutionCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="executionUpdateDialogVisible" title="更新执行进度" width="640px">
      <el-form label-width="120px">
        <el-form-item label="执行状态">
          <el-select v-model="executionUpdateForm.status" style="width: 100%">
            <el-option label="待处理" value="TODO"/>
            <el-option label="处理中" value="IN_PROGRESS"/>
            <el-option label="阻塞" value="BLOCKED"/>
            <el-option label="已完成" value="DONE"/>
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="executionUpdateForm.priority" style="width: 100%">
            <el-option label="P0" value="P0"/>
            <el-option label="P1" value="P1"/>
            <el-option label="P2" value="P2"/>
          </el-select>
        </el-form-item>
        <el-form-item label="SLA(小时)">
          <el-input-number v-model="executionUpdateForm.slaHours" :min="1" :max="720" style="width: 100%"/>
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker
            v-model="executionUpdateForm.dueTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
            placeholder="可选，留空则保留当前截止时间"
          />
        </el-form-item>
        <el-form-item label="处理人">
          <el-select
            v-model="executionUpdateForm.assigneeUserId"
            clearable
            filterable
            remote
            reserve-keyword
            :loading="userLoading"
            :remote-method="handleLeaderSearch"
            style="width: 100%"
            placeholder="处理中或完成时建议明确处理人"
          >
            <el-option v-for="user in userOptions" :key="`execution-update-user-${user.id}`" :label="formatUserLabel(user)" :value="user.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="处理结果">
          <el-input
            v-model="executionUpdateForm.resultSummary"
            type="textarea"
            :rows="4"
            placeholder="阻塞时可填写阻塞原因，完成时必须填写处理结果"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="executionUpdateDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveExecutionUpdate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="executionTimelineDialogVisible" title="执行轨迹" width="760px">
      <div class="execution-timeline-panel" v-loading="executionTimelineLoading">
        <el-empty
          v-if="!executionTimelineLoading && executionTimelineList.length === 0"
          description="当前执行项暂无轨迹记录"
        />
        <div v-else class="execution-timeline-list">
          <div v-for="item in executionTimelineList" :key="item.id" class="execution-timeline-item">
            <div class="execution-timeline-head">
              <div class="execution-timeline-title">{{ resolveExecutionTimelineEventLabel(item.eventType) }}</div>
              <div class="execution-timeline-time">{{ item.createTime || '--' }}</div>
            </div>
            <div class="execution-timeline-summary">{{ item.eventSummary || '执行项变更' }}</div>
            <div class="execution-timeline-actor">操作人：{{ item.actorName || '系统' }}</div>
            <div v-if="item.eventDetail" class="execution-timeline-detail">{{ item.eventDetail }}</div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="executionTimelineDialogVisible = false">关闭</el-button>
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

.relation-scope-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.relation-scope-card {
  border: 1px solid #dbe4f0;
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  padding: 14px;
}

.relation-scope-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.relation-scope-title {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}

.relation-scope-code-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.relation-scope-code {
  margin-right: 0;
}

.relation-scope-desc {
  margin-top: 12px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.workbench-layout {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.workbench-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px;
}

.workbench-stat {
  border: 1px solid #dbe4f0;
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  padding: 14px;
}

.workbench-stat.danger {
  border-color: #fecaca;
  background: linear-gradient(180deg, #fff7f7 0%, #fff1f2 100%);
}

.workbench-stat.warning {
  border-color: #fde68a;
  background: linear-gradient(180deg, #fffdf4 0%, #fef3c7 100%);
}

.workbench-stat.info {
  border-color: #bfdbfe;
  background: linear-gradient(180deg, #f8fbff 0%, #eff6ff 100%);
}

.workbench-stat-label {
  color: #64748b;
  font-size: 13px;
}

.workbench-stat-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 600;
  color: #0f172a;
}

.workbench-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
}

.workbench-card {
  border: 1px solid #dbe4f0;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  padding: 16px;
}

.workbench-card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.workbench-card-title {
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

.workbench-card-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
}

.workbench-tag-list {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.workbench-scene {
  margin-top: 12px;
  font-size: 12px;
  color: #2563eb;
}

.workbench-action {
  margin-top: 12px;
  font-size: 14px;
  color: #334155;
  line-height: 1.7;
}

.workbench-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.workbench-meta-item {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
  padding: 12px;
}

.workbench-meta-label {
  font-size: 12px;
  color: #64748b;
}

.workbench-meta-value {
  margin-top: 8px;
  font-size: 13px;
  color: #0f172a;
  line-height: 1.6;
}

.workbench-missing {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.workbench-trigger {
  margin-top: 12px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.7;
}

.workbench-footer {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

.route-preview-panel {
  border: 1px solid #dbe4f0;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  padding: 16px;
}

.route-preview-toolbar {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}

.route-preview-result {
  margin-top: 16px;
}

.route-preview-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.route-preview-title {
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

.route-preview-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
}

.route-primary-card {
  margin-top: 14px;
  border: 1px solid #bfdbfe;
  border-radius: 16px;
  background: linear-gradient(180deg, #f8fbff 0%, #eff6ff 100%);
  padding: 16px;
}

.route-primary-title {
  font-size: 15px;
  font-weight: 600;
  color: #1d4ed8;
}

.route-primary-target {
  margin-top: 8px;
  font-size: 18px;
  font-weight: 600;
  color: #0f172a;
}

.route-primary-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.route-primary-meta-item {
  border: 1px solid rgba(191, 219, 254, 0.8);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.8);
  padding: 12px;
}

.route-primary-meta-label {
  font-size: 12px;
  color: #64748b;
}

.route-primary-meta-value {
  margin-top: 8px;
  font-size: 13px;
  color: #0f172a;
  line-height: 1.7;
}

.route-scope-codes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.route-warning-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 14px;
}

.route-candidate-list {
  margin-top: 16px;
}

.route-candidate-title {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.execution-timeline-panel {
  min-height: 200px;
}

.execution-timeline-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.execution-timeline-item {
  border: 1px solid #dbe4f0;
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  padding: 14px;
}

.execution-timeline-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.execution-timeline-title {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}

.execution-timeline-time {
  font-size: 12px;
  color: #64748b;
}

.execution-timeline-summary {
  margin-top: 8px;
  font-size: 14px;
  color: #334155;
}

.execution-timeline-actor {
  margin-top: 8px;
  font-size: 12px;
  color: #64748b;
}

.execution-timeline-detail {
  margin-top: 10px;
  font-size: 12px;
  color: #475569;
  line-height: 1.8;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.template-layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 16px;
  margin-top: 16px;
}

.template-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.template-card {
  text-align: left;
  border: 1px solid #dbe4f0;
  border-radius: 12px;
  background: #f8fafc;
  padding: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.template-card.active {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 8px 18px rgba(64, 158, 255, 0.12);
}

.template-card-title {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}

.template-card-industry {
  margin-top: 4px;
  color: #409eff;
  font-size: 13px;
}

.template-card-meta {
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
}

.template-detail {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  padding: 18px;
}

.template-detail-title {
  font-size: 18px;
  font-weight: 600;
  color: #0f172a;
}

.template-detail-desc {
  margin-top: 10px;
  color: #475569;
  line-height: 1.7;
}

.template-form {
  margin-top: 16px;
}

.template-stat-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.template-stat-item {
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  padding: 14px;
}

.template-stat-label {
  font-size: 13px;
  color: #64748b;
}

.template-stat-value {
  margin-top: 8px;
  font-size: 20px;
  font-weight: 600;
  color: #0f172a;
}

.muted {
  color: #94a3b8;
}

@media (max-width: 900px) {
  .workbench-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workbench-meta-grid {
    grid-template-columns: 1fr;
  }

  .route-primary-meta-grid {
    grid-template-columns: 1fr;
  }

  .template-layout {
    grid-template-columns: 1fr;
  }

  .template-stat-grid {
    grid-template-columns: 1fr;
  }
}
</style>
