<script setup lang="ts">
import {computed, nextTick, reactive, ref, watch} from 'vue';
import type {ElTree} from 'element-plus';
import {ElMessage} from 'element-plus';
import {fetchOrgGovernanceCatalog, type OrgGovernanceCatalogView, type OrgGovernanceOptionView} from '@/api/organization';
import {useAuthStore} from '@/store/auth';
import type {MenuView} from '@/api/auth';
import {
  applyMenuReleaseRecord,
  clearTenantMenuRule,
  createMenuReleaseRecord,
  fetchMenuRules,
  fetchPermissions,
  fetchMenuReleaseRecords,
  fetchTenantMenuRules,
  MenuAdminView,
  MenuImpactPreviewRequest,
  MenuImpactPreviewView,
  MenuPreviewRequest,
  MenuReleaseCreateRequest,
  MenuReleaseReviewRequest,
  MenuReleaseView,
  MenuRuleUpdateRequest,
  PermissionView,
  previewMenuImpact,
  previewMenuTree,
  reviewMenuReleaseRecord,
  TenantMenuAdminView,
  TenantMenuRuleUpdateRequest,
  updateMenuRule,
  updateTenantMenuRule
} from '@/api/rbac';

interface RuleFormModel {
  orgTypeCodes: string[];
  orgUnitCodes: string[];
  orgTags: string[];
  requiredRelationTypes: string[];
}

interface TenantRuleFormModel {
  visible: number | null;
  status: number | null;
  orgTypeCodes: string[];
  orgUnitCodes: string[];
  orgTags: string[];
  requiredRelationTypes: string[];
}

interface PreviewFormModel {
  permissionCodes: string[];
  orgTypeCodes: string[];
  orgUnitCodes: string[];
  orgTags: string[];
  relationTypes: string[];
  experience: boolean;
  menuWhitelist: string[];
}

interface GovernanceOptionItem {
  label: string;
  value: string;
}

const authStore = useAuthStore();
const ruleTreeRef = ref<InstanceType<typeof ElTree> | null>(null);
const tenantTreeRef = ref<InstanceType<typeof ElTree> | null>(null);
const ruleLoading = ref(false);
const tenantRuleLoading = ref(false);
const permissionLoading = ref(false);
const saving = ref(false);
const tenantSaving = ref(false);
const tenantClearing = ref(false);
const previewLoading = ref(false);
const impactLoading = ref(false);
const releaseLoading = ref(false);
const releaseSubmitting = ref(false);
const governanceLoading = ref(false);
const ruleFilterText = ref('');
const tenantRuleFilterText = ref('');
const menuRules = ref<MenuAdminView[]>([]);
const tenantMenuRules = ref<TenantMenuAdminView[]>([]);
const previewMenus = ref<MenuView[]>([]);
const permissions = ref<PermissionView[]>([]);
const governanceCatalog = ref<OrgGovernanceCatalogView | null>(null);
const releaseRecords = ref<MenuReleaseView[]>([]);
const selectedMenuId = ref<number | null>(null);
const selectedTenantMenuCode = ref('');
const previewExecuted = ref(false);
const globalImpactResult = ref<MenuImpactPreviewView | null>(null);
const tenantImpactResult = ref<MenuImpactPreviewView | null>(null);

const releaseStatusLabelMap: Record<string, string> = {
  DRAFT: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  APPLIED: '已应用'
};

const releaseTypeLabelMap: Record<string, string> = {
  GLOBAL_RULE: '全局规则',
  TENANT_RULE: '租户覆盖'
};

const defaultRelationTypeLabelMapping: Record<string, string> = {
  SUPERVISE: '主管',
  COOPERATE: '协同',
  SERVICE: '服务',
  OWNERSHIP: '归口',
  MATRIX: '矩阵'
};

const ruleTreeProps = {
  label: 'menuName',
  children: 'children'
};

const tenantTreeProps = ruleTreeProps;
const globalTreeProps = ruleTreeProps;

const previewTreeProps = {
  label: 'menuName',
  children: 'children'
};

const ruleForm = reactive<RuleFormModel>({
  orgTypeCodes: [],
  orgUnitCodes: [],
  orgTags: [],
  requiredRelationTypes: []
});

const tenantRuleForm = reactive<TenantRuleFormModel>({
  visible: null,
  status: null,
  orgTypeCodes: [],
  orgUnitCodes: [],
  orgTags: [],
  requiredRelationTypes: []
});

const previewForm = reactive<PreviewFormModel>({
  permissionCodes: [],
  orgTypeCodes: [],
  orgUnitCodes: [],
  orgTags: [],
  relationTypes: [],
  experience: false,
  menuWhitelist: []
});

const globalTreeRef = ruleTreeRef;
const globalRuleLoading = ruleLoading;
const globalSaving = saving;
const globalRuleFilterText = ruleFilterText;
const globalRuleForm = ruleForm;

const isSysAdmin = computed(() => authStore.isSysAdmin);

const activeTenantId = computed(() => {
  return isSysAdmin.value ? authStore.activeTenantId : authStore.tenantId;
});

const activeTenantName = computed(() => {
  return isSysAdmin.value ? authStore.activeTenantName : authStore.tenantName;
});

const hasPreviewTenant = computed(() => {
  return !isSysAdmin.value || !!activeTenantId.value;
});

const hasTenantWorkspace = computed(() => {
  return !isSysAdmin.value || !!activeTenantId.value;
});

const buildCatalogLabel = (code: string, name?: string) => {
  if (name && name !== code) {
    return `${name} (${code})`;
  }
  return code;
};

const resolveCatalogOptions = (
  options?: OrgGovernanceOptionView[],
  fallbackNameMapping?: Record<string, string>
): GovernanceOptionItem[] => {
  const mapping = new Map<string, GovernanceOptionItem>();
  (options || []).forEach((item) => {
    if (!item?.code) {
      return;
    }
    const code = item.code.trim().toUpperCase();
    mapping.set(code, {
      label: buildCatalogLabel(code, item.name?.trim()),
      value: code
    });
  });
  Object.entries(fallbackNameMapping || {}).forEach(([code, name]) => {
    if (!mapping.has(code)) {
      mapping.set(code, {
        label: buildCatalogLabel(code, name),
        value: code
      });
    }
  });
  return Array.from(mapping.values());
};

const orgTypeOptions = computed(() => resolveCatalogOptions(governanceCatalog.value?.orgTypeOptions));

const orgUnitOptions = computed(() => resolveCatalogOptions(governanceCatalog.value?.orgUnitOptions));

const orgTagOptions = computed(() => resolveCatalogOptions(governanceCatalog.value?.orgTagOptions));

const relationTypeOptions = computed(() => resolveCatalogOptions(
  governanceCatalog.value?.relationTypeOptions,
  defaultRelationTypeLabelMapping
));

const allMenuNodes = computed(() => {
  const result: MenuAdminView[] = [];
  const walk = (nodes: MenuAdminView[]) => {
    nodes.forEach((node) => {
      result.push(node);
      walk(node.children || []);
    });
  };
  walk(menuRules.value);
  return result;
});

const selectedMenu = computed(() => {
  return allMenuNodes.value.find((item) => item.id === selectedMenuId.value) || null;
});

const selectedGlobalMenu = selectedMenu;

const tenantMenuNodes = computed(() => {
  const result: TenantMenuAdminView[] = [];
  const walk = (nodes: TenantMenuAdminView[]) => {
    nodes.forEach((node) => {
      result.push(node);
      walk(node.children || []);
    });
  };
  walk(tenantMenuRules.value);
  return result;
});

const selectedTenantMenu = computed(() => {
  return tenantMenuNodes.value.find((item) => item.menuCode === selectedTenantMenuCode.value) || null;
});

const ruleConfiguredCount = computed(() => {
  return allMenuNodes.value.filter((item) => hasRuleConfigured(item)).length;
});

const tenantOverrideCount = computed(() => {
  return tenantMenuNodes.value.filter((item) => item.overrideActive).length;
});

const previewMenuCount = computed(() => {
  const walk = (nodes: MenuView[]): number => {
    return nodes.reduce((sum, item) => sum + 1 + walk(item.children || []), 0);
  };
  return walk(previewMenus.value || []);
});

const selectedMenuScopeSummary = computed(() => {
  if (!selectedMenu.value) {
    return '请选择左侧菜单节点查看并编辑规则。';
  }
  const segments: string[] = [];
  if (selectedMenu.value.orgTypeCodes?.length) {
    segments.push(`组织类型 ${selectedMenu.value.orgTypeCodes.join(' / ')}`);
  }
  if (selectedMenu.value.orgUnitCodes?.length) {
    segments.push(`组织编码 ${selectedMenu.value.orgUnitCodes.join(' / ')}`);
  }
  if (selectedMenu.value.orgTags?.length) {
    segments.push(`组织标签 ${selectedMenu.value.orgTags.join(' / ')}`);
  }
  if (selectedMenu.value.requiredRelationTypes?.length) {
    segments.push(`关系语义 ${selectedMenu.value.requiredRelationTypes.join(' / ')}`);
  }
  return segments.length ? segments.join('；') : '当前菜单未配置额外组织适配规则，默认仅按原有权限与可见性控制。';
});

const selectedGlobalMenuSummary = selectedMenuScopeSummary;

const selectedTenantBaseSummary = computed(() => {
  if (!selectedTenantMenu.value) {
    return '请选择左侧租户菜单节点查看全局基线。';
  }
  return buildRuleSummary({
    orgTypeCodes: selectedTenantMenu.value.baseOrgTypeCodes,
    orgUnitCodes: selectedTenantMenu.value.baseOrgUnitCodes,
    orgTags: selectedTenantMenu.value.baseOrgTags,
    requiredRelationTypes: selectedTenantMenu.value.baseRequiredRelationTypes
  }, '全局菜单未配置组织适配规则。');
});

const selectedTenantOverrideSummary = computed(() => {
  if (!selectedTenantMenu.value) {
    return '请选择左侧租户菜单节点查看覆盖状态。';
  }
  if (!selectedTenantMenu.value.overrideActive) {
    return '当前节点未配置租户覆盖，运行时完全继承全局菜单配置。';
  }
  return buildRuleSummary({
    orgTypeCodes: selectedTenantMenu.value.overrideOrgTypeCodes,
    orgUnitCodes: selectedTenantMenu.value.overrideOrgUnitCodes,
    orgTags: selectedTenantMenu.value.overrideOrgTags,
    requiredRelationTypes: selectedTenantMenu.value.overrideRequiredRelationTypes
  }, '当前覆盖仅调整显示/启用状态，组织规则未单独覆盖。');
});

const normalizeValues = (values?: string[]) => {
  return Array.from(new Set((values || [])
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => item.toUpperCase())));
};

const buildRuleSummary = (rule: {
  orgTypeCodes?: string[];
  orgUnitCodes?: string[];
  orgTags?: string[];
  requiredRelationTypes?: string[];
}, fallback: string) => {
  const segments: string[] = [];
  if (rule.orgTypeCodes?.length) {
    segments.push(`组织类型 ${rule.orgTypeCodes.join(' / ')}`);
  }
  if (rule.orgUnitCodes?.length) {
    segments.push(`组织编码 ${rule.orgUnitCodes.join(' / ')}`);
  }
  if (rule.orgTags?.length) {
    segments.push(`组织标签 ${rule.orgTags.join(' / ')}`);
  }
  if (rule.requiredRelationTypes?.length) {
    segments.push(`关系语义 ${rule.requiredRelationTypes.join(' / ')}`);
  }
  return segments.length ? segments.join('；') : fallback;
};

const resolveVisibilityLabel = (value?: number | null) => {
  return value === 0 ? '隐藏' : '显示';
};

const resolveStatusLabel = (value?: number | null) => {
  return value === 0 ? '停用' : '启用';
};

const resolveReleaseStatusLabel = (value?: string) => {
  return releaseStatusLabelMap[value || 'DRAFT'] || value || '待审核';
};

const resolveReleaseStatusType = (value?: string) => {
  if (value === 'APPROVED') {
    return 'success';
  }
  if (value === 'REJECTED') {
    return 'danger';
  }
  if (value === 'APPLIED') {
    return 'primary';
  }
  return 'warning';
};

const resolveReleaseTypeLabel = (value?: string) => {
  return releaseTypeLabelMap[value || 'GLOBAL_RULE'] || value || '全局规则';
};

const hasRuleConfigured = (menu?: MenuAdminView | null) => {
  if (!menu) {
    return false;
  }
  return !!(
    menu.orgTypeCodes?.length
    || menu.orgUnitCodes?.length
    || menu.orgTags?.length
    || menu.requiredRelationTypes?.length
  );
};

const syncRuleForm = (menu?: MenuAdminView | null) => {
  Object.assign(ruleForm, {
    orgTypeCodes: [...(menu?.orgTypeCodes || [])],
    orgUnitCodes: [...(menu?.orgUnitCodes || [])],
    orgTags: [...(menu?.orgTags || [])],
    requiredRelationTypes: [...(menu?.requiredRelationTypes || [])]
  });
};

const syncTenantRuleForm = (menu?: TenantMenuAdminView | null) => {
  Object.assign(tenantRuleForm, {
    visible: menu?.overrideVisible ?? null,
    status: menu?.overrideStatus ?? null,
    orgTypeCodes: [...(menu?.overrideOrgTypeCodes || [])],
    orgUnitCodes: [...(menu?.overrideOrgUnitCodes || [])],
    orgTags: [...(menu?.overrideOrgTags || [])],
    requiredRelationTypes: [...(menu?.overrideRequiredRelationTypes || [])]
  });
};

const fillPreviewFromSession = () => {
  previewForm.permissionCodes = [...(authStore.permissions || [])];
  previewForm.relationTypes = Object.keys(authStore.relationScopes || {});
  previewForm.experience = !!authStore.experience;
  previewForm.menuWhitelist = [];
};

const loadMenuRules = async () => {
  if (!isSysAdmin.value) {
    menuRules.value = [];
    selectedMenuId.value = null;
    return;
  }
  ruleLoading.value = true;
  try {
    const result = await fetchMenuRules();
    menuRules.value = result || [];
    if (!selectedMenuId.value && allMenuNodes.value.length) {
      selectedMenuId.value = allMenuNodes.value[0].id || null;
    } else if (selectedMenuId.value && !allMenuNodes.value.some((item) => item.id === selectedMenuId.value)) {
      selectedMenuId.value = allMenuNodes.value[0]?.id || null;
    }
    syncRuleForm(selectedMenu.value);
    await nextTick();
    if (selectedMenuId.value) {
      ruleTreeRef.value?.setCurrentKey(selectedMenuId.value);
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载菜单规则失败');
  } finally {
    ruleLoading.value = false;
  }
};

const loadTenantMenuRuleTree = async () => {
  if (!hasTenantWorkspace.value || !activeTenantId.value) {
    tenantMenuRules.value = [];
    selectedTenantMenuCode.value = '';
    return;
  }
  tenantRuleLoading.value = true;
  try {
    tenantMenuRules.value = await fetchTenantMenuRules(isSysAdmin.value ? activeTenantId.value : undefined);
    if (!selectedTenantMenuCode.value && tenantMenuNodes.value.length) {
      selectedTenantMenuCode.value = tenantMenuNodes.value[0].menuCode || '';
    } else if (selectedTenantMenuCode.value && !tenantMenuNodes.value.some((item) => item.menuCode === selectedTenantMenuCode.value)) {
      selectedTenantMenuCode.value = tenantMenuNodes.value[0]?.menuCode || '';
    }
    syncTenantRuleForm(selectedTenantMenu.value);
    await nextTick();
    if (selectedTenantMenuCode.value) {
      tenantTreeRef.value?.setCurrentKey(selectedTenantMenuCode.value);
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载租户菜单覆盖失败');
  } finally {
    tenantRuleLoading.value = false;
  }
};

const loadPermissions = async () => {
  permissionLoading.value = true;
  try {
    permissions.value = await fetchPermissions();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载权限列表失败');
  } finally {
    permissionLoading.value = false;
  }
};

const loadGovernanceCatalog = async () => {
  if (!hasTenantWorkspace.value || !activeTenantId.value) {
    governanceCatalog.value = null;
    return;
  }
  governanceLoading.value = true;
  try {
    governanceCatalog.value = await fetchOrgGovernanceCatalog(activeTenantId.value || undefined);
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载组织治理字典失败';
    if (message.includes('无权限')) {
      governanceCatalog.value = null;
      return;
    }
    ElMessage.error(message);
  } finally {
    governanceLoading.value = false;
  }
};

const loadReleaseRecords = async () => {
  if (!hasTenantWorkspace.value || !activeTenantId.value) {
    releaseRecords.value = [];
    return;
  }
  releaseLoading.value = true;
  try {
    releaseRecords.value = await fetchMenuReleaseRecords(activeTenantId.value || undefined);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载菜单发布单失败');
  } finally {
    releaseLoading.value = false;
  }
};

const handleRuleFilter = (keyword: string, data: MenuAdminView) => {
  if (!keyword) {
    return true;
  }
  return `${data.menuName || ''}${data.menuCode || ''}`.toLowerCase().includes(keyword.toLowerCase());
};

const handleTenantRuleFilter = (keyword: string, data: TenantMenuAdminView) => {
  if (!keyword) {
    return true;
  }
  return `${data.menuName || ''}${data.menuCode || ''}`.toLowerCase().includes(keyword.toLowerCase());
};

const handleSelectMenu = (data: MenuAdminView) => {
  selectedMenuId.value = data.id || null;
  syncRuleForm(data);
};

const handleSelectGlobalMenu = handleSelectMenu;

const handleSelectTenantMenu = (data: TenantMenuAdminView) => {
  selectedTenantMenuCode.value = data.menuCode || '';
  syncTenantRuleForm(data);
};

const handleResetRule = () => {
  syncRuleForm(selectedMenu.value);
};

const handleResetGlobalRule = handleResetRule;

const handleResetTenantRule = () => {
  syncTenantRuleForm(selectedTenantMenu.value);
};

const handleSaveRule = async () => {
  if (!selectedMenu.value?.id) {
    ElMessage.warning('请先选择要维护的菜单节点');
    return;
  }
  const payload: MenuRuleUpdateRequest = {
    orgTypeCodes: normalizeValues(ruleForm.orgTypeCodes),
    orgUnitCodes: normalizeValues(ruleForm.orgUnitCodes),
    orgTags: normalizeValues(ruleForm.orgTags),
    requiredRelationTypes: normalizeValues(ruleForm.requiredRelationTypes)
  };
  saving.value = true;
  try {
    const updated = await updateMenuRule(selectedMenu.value.id, payload);
    ElMessage.success(`菜单规则已更新：${updated.menuName || updated.menuCode || selectedMenu.value.menuCode}`);
    const keepMenuId = selectedMenu.value.id;
    globalImpactResult.value = null;
    await loadMenuRules();
    selectedMenuId.value = keepMenuId;
    syncRuleForm(allMenuNodes.value.find((item) => item.id === keepMenuId) || null);
    await loadTenantMenuRuleTree();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存菜单规则失败');
  } finally {
    saving.value = false;
  }
};

const handleSaveGlobalRule = handleSaveRule;

const handleGlobalRuleFilter = handleRuleFilter;

const handleSaveTenantRule = async () => {
  if (!selectedTenantMenu.value?.menuCode) {
    ElMessage.warning('请先选择要维护的租户菜单节点');
    return;
  }
  if (!activeTenantId.value) {
    ElMessage.warning('请先选择租户');
    return;
  }
  const payload: TenantMenuRuleUpdateRequest = {
    tenantId: isSysAdmin.value ? activeTenantId.value : undefined,
    visible: tenantRuleForm.visible,
    status: tenantRuleForm.status,
    orgTypeCodes: normalizeValues(tenantRuleForm.orgTypeCodes),
    orgUnitCodes: normalizeValues(tenantRuleForm.orgUnitCodes),
    orgTags: normalizeValues(tenantRuleForm.orgTags),
    requiredRelationTypes: normalizeValues(tenantRuleForm.requiredRelationTypes)
  };
  tenantSaving.value = true;
  try {
    const updated = await updateTenantMenuRule(selectedTenantMenu.value.menuCode, payload);
    ElMessage.success(`租户菜单覆盖已更新：${updated.menuName || updated.menuCode || selectedTenantMenu.value.menuCode}`);
    const keepMenuCode = selectedTenantMenu.value.menuCode;
    tenantImpactResult.value = null;
    await loadTenantMenuRuleTree();
    selectedTenantMenuCode.value = keepMenuCode;
    syncTenantRuleForm(tenantMenuNodes.value.find((item) => item.menuCode === keepMenuCode) || null);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存租户菜单覆盖失败');
  } finally {
    tenantSaving.value = false;
  }
};

const handleClearTenantRule = async () => {
  if (!selectedTenantMenu.value?.menuCode || !selectedTenantMenu.value.overrideActive) {
    ElMessage.warning('当前节点没有租户覆盖可清除');
    return;
  }
  if (!activeTenantId.value) {
    ElMessage.warning('请先选择租户');
    return;
  }
  if (!window.confirm(`确认清除“${selectedTenantMenu.value.menuName || selectedTenantMenu.value.menuCode}”的租户覆盖吗？`)) {
    return;
  }
  tenantClearing.value = true;
  try {
    await clearTenantMenuRule(selectedTenantMenu.value.menuCode, isSysAdmin.value ? activeTenantId.value : undefined);
    ElMessage.success('租户菜单覆盖已清除');
    const keepMenuCode = selectedTenantMenu.value.menuCode;
    tenantImpactResult.value = null;
    await loadTenantMenuRuleTree();
    selectedTenantMenuCode.value = keepMenuCode;
    syncTenantRuleForm(tenantMenuNodes.value.find((item) => item.menuCode === keepMenuCode) || null);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '清除租户菜单覆盖失败');
  } finally {
    tenantClearing.value = false;
  }
};

const buildPreviewPayload = (): MenuPreviewRequest => {
  return {
    tenantId: isSysAdmin.value ? activeTenantId.value || undefined : undefined,
    permissionCodes: normalizeValues(previewForm.permissionCodes),
    orgTypeCodes: normalizeValues(previewForm.orgTypeCodes),
    orgUnitCodes: normalizeValues(previewForm.orgUnitCodes),
    orgTags: normalizeValues(previewForm.orgTags),
    relationTypes: normalizeValues(previewForm.relationTypes),
    experience: previewForm.experience,
    menuWhitelist: normalizeValues(previewForm.menuWhitelist)
  };
};

const handlePreview = async () => {
  if (!hasPreviewTenant.value) {
    ElMessage.warning('请先选择要预览的租户');
    return;
  }
  const payload = buildPreviewPayload();
  previewLoading.value = true;
  try {
    previewMenus.value = await previewMenuTree(payload);
    previewExecuted.value = true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '预览菜单失败');
  } finally {
    previewLoading.value = false;
  }
};

const handlePreviewGlobalImpact = async () => {
  if (!selectedGlobalMenu?.value?.id) {
    ElMessage.warning('请先选择要分析的全局菜单节点');
    return;
  }
  if (!hasPreviewTenant.value) {
    ElMessage.warning('影响分析需要先选择租户');
    return;
  }
  const payload: MenuImpactPreviewRequest = {
    tenantId: isSysAdmin.value ? activeTenantId.value || undefined : undefined,
    menuId: selectedGlobalMenu.value.id,
    globalRule: {
      orgTypeCodes: normalizeValues(globalRuleForm.orgTypeCodes),
      orgUnitCodes: normalizeValues(globalRuleForm.orgUnitCodes),
      orgTags: normalizeValues(globalRuleForm.orgTags),
      requiredRelationTypes: normalizeValues(globalRuleForm.requiredRelationTypes)
    },
    previewContext: buildPreviewPayload()
  };
  impactLoading.value = true;
  try {
    globalImpactResult.value = await previewMenuImpact(payload);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '全局菜单影响分析失败');
  } finally {
    impactLoading.value = false;
  }
};

const handlePreviewTenantImpact = async () => {
  if (!selectedTenantMenu.value?.menuCode) {
    ElMessage.warning('请先选择要分析的租户菜单节点');
    return;
  }
  if (!hasPreviewTenant.value) {
    ElMessage.warning('影响分析需要先选择租户');
    return;
  }
  const payload: MenuImpactPreviewRequest = {
    tenantId: isSysAdmin.value ? activeTenantId.value || undefined : undefined,
    menuCode: selectedTenantMenu.value.menuCode,
    tenantRule: {
      tenantId: isSysAdmin.value ? activeTenantId.value || undefined : undefined,
      visible: tenantRuleForm.visible,
      status: tenantRuleForm.status,
      orgTypeCodes: normalizeValues(tenantRuleForm.orgTypeCodes),
      orgUnitCodes: normalizeValues(tenantRuleForm.orgUnitCodes),
      orgTags: normalizeValues(tenantRuleForm.orgTags),
      requiredRelationTypes: normalizeValues(tenantRuleForm.requiredRelationTypes)
    },
    previewContext: buildPreviewPayload()
  };
  impactLoading.value = true;
  try {
    tenantImpactResult.value = await previewMenuImpact(payload);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '租户菜单影响分析失败');
  } finally {
    impactLoading.value = false;
  }
};

const handleCreateGlobalRelease = async () => {
  if (!selectedGlobalMenu.value?.id) {
    ElMessage.warning('请先选择要生成发布单的全局菜单');
    return;
  }
  if (!hasPreviewTenant.value) {
    ElMessage.warning('生成发布单前请先选择租户');
    return;
  }
  const changeRemark = window.prompt('可选：请输入本次全局菜单变更说明', '') || '';
  const payload: MenuReleaseCreateRequest = {
    tenantId: isSysAdmin.value ? activeTenantId.value || undefined : undefined,
    changeRemark: changeRemark.trim() || undefined,
    impactRequest: {
      tenantId: isSysAdmin.value ? activeTenantId.value || undefined : undefined,
      menuId: selectedGlobalMenu.value.id,
      globalRule: {
        orgTypeCodes: normalizeValues(globalRuleForm.orgTypeCodes),
        orgUnitCodes: normalizeValues(globalRuleForm.orgUnitCodes),
        orgTags: normalizeValues(globalRuleForm.orgTags),
        requiredRelationTypes: normalizeValues(globalRuleForm.requiredRelationTypes)
      },
      previewContext: buildPreviewPayload()
    }
  };
  releaseSubmitting.value = true;
  try {
    await createMenuReleaseRecord(payload);
    ElMessage.success('全局菜单发布单已创建');
    await loadReleaseRecords();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建全局菜单发布单失败');
  } finally {
    releaseSubmitting.value = false;
  }
};

const handleCreateTenantRelease = async () => {
  if (!selectedTenantMenu.value?.menuCode) {
    ElMessage.warning('请先选择要生成发布单的租户菜单');
    return;
  }
  if (!hasPreviewTenant.value) {
    ElMessage.warning('生成发布单前请先选择租户');
    return;
  }
  const changeRemark = window.prompt('可选：请输入本次租户菜单覆盖说明', '') || '';
  const payload: MenuReleaseCreateRequest = {
    tenantId: isSysAdmin.value ? activeTenantId.value || undefined : undefined,
    changeRemark: changeRemark.trim() || undefined,
    impactRequest: {
      tenantId: isSysAdmin.value ? activeTenantId.value || undefined : undefined,
      menuCode: selectedTenantMenu.value.menuCode,
      tenantRule: {
        tenantId: isSysAdmin.value ? activeTenantId.value || undefined : undefined,
        visible: tenantRuleForm.visible,
        status: tenantRuleForm.status,
        orgTypeCodes: normalizeValues(tenantRuleForm.orgTypeCodes),
        orgUnitCodes: normalizeValues(tenantRuleForm.orgUnitCodes),
        orgTags: normalizeValues(tenantRuleForm.orgTags),
        requiredRelationTypes: normalizeValues(tenantRuleForm.requiredRelationTypes)
      },
      previewContext: buildPreviewPayload()
    }
  };
  releaseSubmitting.value = true;
  try {
    await createMenuReleaseRecord(payload);
    ElMessage.success('租户菜单发布单已创建');
    await loadReleaseRecords();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建租户菜单发布单失败');
  } finally {
    releaseSubmitting.value = false;
  }
};

const handleReviewRelease = async (record: MenuReleaseView, decision: 'APPROVED' | 'REJECTED') => {
  if (!record?.id) {
    return;
  }
  const reviewComment = window.prompt(
    decision === 'APPROVED' ? '可选：请输入审核通过说明' : '可选：请输入驳回原因',
    ''
  ) || '';
  const payload: MenuReleaseReviewRequest = {
    decision,
    reviewComment: reviewComment.trim() || undefined
  };
  releaseSubmitting.value = true;
  try {
    await reviewMenuReleaseRecord(record.id, payload);
    ElMessage.success(decision === 'APPROVED' ? '发布单已审核通过' : '发布单已驳回');
    await loadReleaseRecords();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审核菜单发布单失败');
  } finally {
    releaseSubmitting.value = false;
  }
};

const handleApplyRelease = async (record: MenuReleaseView) => {
  if (!record?.id) {
    return;
  }
  if (!window.confirm(`确认应用发布单“${record.releaseTitle || record.menuName || record.menuCode}”吗？`)) {
    return;
  }
  releaseSubmitting.value = true;
  try {
    await applyMenuReleaseRecord(record.id);
    ElMessage.success('发布单已应用');
    await Promise.all([
      loadMenuRules(),
      loadTenantMenuRuleTree(),
      loadReleaseRecords()
    ]);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '应用菜单发布单失败');
  } finally {
    releaseSubmitting.value = false;
  }
};

const handleResetPreview = () => {
  fillPreviewFromSession();
  previewMenus.value = [];
  previewExecuted.value = false;
  globalImpactResult.value = null;
  tenantImpactResult.value = null;
};

watch(ruleFilterText, (value) => {
  ruleTreeRef.value?.filter(value);
});

watch(
  () => activeTenantId.value,
  async () => {
    if (!isSysAdmin.value) {
      fillPreviewFromSession();
    }
    if (!activeTenantId.value) {
      tenantMenuRules.value = [];
      releaseRecords.value = [];
      selectedTenantMenuCode.value = '';
      previewMenus.value = [];
      previewExecuted.value = false;
      governanceCatalog.value = null;
      globalImpactResult.value = null;
      tenantImpactResult.value = null;
      return;
    }
    await Promise.all([
      loadTenantMenuRuleTree(),
      loadGovernanceCatalog(),
      loadReleaseRecords()
    ]);
  },
  {immediate: true}
);

watch(
  () => selectedMenuId.value,
  () => {
    syncRuleForm(selectedMenu.value);
    globalImpactResult.value = null;
  }
);

watch(tenantRuleFilterText, (value) => {
  tenantTreeRef.value?.filter(value);
});

watch(
  () => selectedTenantMenuCode.value,
  () => {
    syncTenantRuleForm(selectedTenantMenu.value);
    tenantImpactResult.value = null;
  }
);

loadMenuRules();
loadPermissions();
fillPreviewFromSession();
</script>

<template>
  <div class="page">
    <div class="page-title">菜单治理</div>
    <div class="page-desc">统一管理全局菜单规则、租户覆盖策略，并按指定组织上下文预览最终可见菜单。</div>

    <el-alert
      v-if="!isSysAdmin"
      type="info"
      :closable="false"
      title="当前账号仅开放菜单命中预览能力。全局菜单规则的查询与维护仅对系统管理员开放。"
    />

    <div v-if="isSysAdmin" class="stats-grid">
      <div class="stat-card">
        <div class="stat-label">全局菜单数</div>
        <div class="stat-value">{{ allMenuNodes.length }}</div>
      </div>
      <div class="stat-card highlight">
        <div class="stat-label">全局规则已配置</div>
        <div class="stat-value">{{ ruleConfiguredCount }}</div>
      </div>
      <div class="stat-card accent">
        <div class="stat-label">当前租户覆盖数</div>
        <div class="stat-value">{{ hasTenantWorkspace ? tenantOverrideCount : '-' }}</div>
      </div>
    </div>

    <div v-if="isSysAdmin" class="workspace-grid">
      <el-card class="card tree-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>全局菜单规则树</span>
            <el-input v-model="globalRuleFilterText" size="small" clearable placeholder="搜索菜单名称或编码" class="filter-input"/>
          </div>
        </template>
        <el-tree
          ref="globalTreeRef"
          :data="menuRules"
          :props="globalTreeProps"
          node-key="id"
          highlight-current
          default-expand-all
          :filter-node-method="handleGlobalRuleFilter"
          class="menu-tree"
          v-loading="globalRuleLoading"
          @node-click="handleSelectGlobalMenu"
        >
          <template #default="{ data }">
            <div class="menu-tree-node">
              <div class="menu-tree-main">
                <span class="menu-tree-name">{{ data.menuName }}</span>
                <span class="menu-tree-code">{{ data.menuCode }}</span>
              </div>
              <el-tag v-if="hasRuleConfigured(data)" type="success" effect="light" size="small">已配置</el-tag>
            </div>
          </template>
        </el-tree>
      </el-card>

      <el-card class="card editor-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>全局规则编辑</span>
            <el-tag v-if="selectedGlobalMenu?.permCode" type="info" effect="plain">{{ selectedGlobalMenu?.permCode }}</el-tag>
          </div>
        </template>

        <el-empty v-if="!selectedGlobalMenu" description="请选择左侧全局菜单节点"/>
        <template v-else>
          <div class="menu-meta">
            <div class="menu-meta-item">
              <div class="menu-meta-label">菜单编码</div>
              <div class="menu-meta-value">{{ selectedGlobalMenu.menuCode }}</div>
            </div>
            <div class="menu-meta-item">
              <div class="menu-meta-label">父级菜单</div>
              <div class="menu-meta-value">{{ selectedGlobalMenu.parentMenuCode || '根节点' }}</div>
            </div>
            <div class="menu-meta-item">
              <div class="menu-meta-label">路由路径</div>
              <div class="menu-meta-value">{{ selectedGlobalMenu.path || '-' }}</div>
            </div>
            <div class="menu-meta-item">
              <div class="menu-meta-label">组件标识</div>
              <div class="menu-meta-value">{{ selectedGlobalMenu.component || '-' }}</div>
            </div>
          </div>

          <el-alert
            class="summary-alert"
            type="info"
            :closable="false"
            :title="selectedGlobalMenuSummary"
          />

          <el-form label-width="136px" class="rule-form">
            <el-form-item label="组织类型编码">
              <el-select
                v-model="globalRuleForm.orgTypeCodes"
                multiple
                filterable
                allow-create
                default-first-option
                collapse-tags
                collapse-tags-tooltip
                :loading="governanceLoading"
                style="width: 100%"
                placeholder="例如 GOVERNMENT_STANDARD / SCHOOL_ORG / CHAIN_STORE"
              >
                <el-option
                  v-for="item in orgTypeOptions"
                  :key="`global-org-type-${item.value}`"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="直接组织编码">
              <el-select
                v-model="globalRuleForm.orgUnitCodes"
                multiple
                filterable
                allow-create
                default-first-option
                collapse-tags
                collapse-tags-tooltip
                :loading="governanceLoading"
                style="width: 100%"
                placeholder="例如 STORE_001 / SCHOOL_MAIN_CAMPUS"
              >
                <el-option
                  v-for="item in orgUnitOptions"
                  :key="`global-org-unit-${item.value}`"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="组织标签">
              <el-select
                v-model="globalRuleForm.orgTags"
                multiple
                filterable
                allow-create
                default-first-option
                collapse-tags
                collapse-tags-tooltip
                :loading="governanceLoading"
                style="width: 100%"
                placeholder="例如 直营 / 校区 / 政务 / 财务"
              >
                <el-option
                  v-for="item in orgTagOptions"
                  :key="`global-org-tag-${item.value}`"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="关系语义">
              <el-select
                v-model="globalRuleForm.requiredRelationTypes"
                multiple
                filterable
                allow-create
                default-first-option
                collapse-tags
                collapse-tags-tooltip
                :loading="governanceLoading"
                style="width: 100%"
                placeholder="命中任一关系类型即可显示"
              >
                <el-option
                  v-for="item in relationTypeOptions"
                  :key="item.value"
                  :label="`${item.label} (${item.value})`"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-form>

          <div class="editor-actions">
            <el-button @click="handleResetGlobalRule">重置</el-button>
            <el-button :loading="impactLoading" @click="handlePreviewGlobalImpact">分析影响</el-button>
            <el-button :loading="releaseSubmitting" @click="handleCreateGlobalRelease">生成发布单</el-button>
            <el-button type="primary" :loading="globalSaving" @click="handleSaveGlobalRule">保存全局规则</el-button>
          </div>

          <div v-if="globalImpactResult" class="impact-panel">
            <div class="impact-title">发布前影响分析</div>
            <div class="impact-summary">{{ globalImpactResult.impactSummary || '暂无影响摘要' }}</div>
            <div class="impact-grid">
              <div class="impact-item">
                <div class="impact-label">基线命中</div>
                <div class="impact-value">{{ globalImpactResult.baselineMatched ? '命中' : '未命中' }}</div>
              </div>
              <div class="impact-item">
                <div class="impact-label">变更后命中</div>
                <div class="impact-value">{{ globalImpactResult.targetMatched ? '命中' : '未命中' }}</div>
              </div>
              <div class="impact-item">
                <div class="impact-label">基线菜单数</div>
                <div class="impact-value">{{ globalImpactResult.baselineMenuCount || 0 }}</div>
              </div>
              <div class="impact-item">
                <div class="impact-label">变更后菜单数</div>
                <div class="impact-value">{{ globalImpactResult.targetMenuCount || 0 }}</div>
              </div>
            </div>
            <div class="impact-rule-grid">
              <div class="impact-rule-item">
                <div class="impact-label">基线规则</div>
                <div class="impact-rule-value">{{ globalImpactResult.baselineRuleSummary || '未配置额外规则' }}</div>
              </div>
              <div class="impact-rule-item">
                <div class="impact-label">目标规则</div>
                <div class="impact-rule-value">{{ globalImpactResult.targetRuleSummary || '未配置额外规则' }}</div>
              </div>
            </div>
            <div v-if="globalImpactResult.changedDimensions?.length" class="impact-tag-list">
              <el-tag v-for="item in globalImpactResult.changedDimensions" :key="`global-impact-${item}`" type="warning">
                {{ item }}
              </el-tag>
            </div>
            <div v-if="globalImpactResult.addedMenus?.length" class="impact-diff-section">
              <div class="impact-diff-title success">新增命中菜单</div>
              <div class="impact-tag-list">
                <el-tag v-for="item in globalImpactResult.addedMenus" :key="`global-added-${item.menuCode}`" type="success">
                  {{ item.menuName || item.menuCode }}
                </el-tag>
              </div>
            </div>
            <div v-if="globalImpactResult.removedMenus?.length" class="impact-diff-section">
              <div class="impact-diff-title danger">移除命中菜单</div>
              <div class="impact-tag-list">
                <el-tag v-for="item in globalImpactResult.removedMenus" :key="`global-removed-${item.menuCode}`" type="danger">
                  {{ item.menuName || item.menuCode }}
                </el-tag>
              </div>
            </div>
          </div>
        </template>
      </el-card>
    </div>

    <el-card class="card tenant-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>租户菜单覆盖</span>
          <el-tag v-if="hasTenantWorkspace" type="success" effect="light">
            {{ activeTenantName || activeTenantId || authStore.tenantName || authStore.tenantId || '当前租户' }}
          </el-tag>
          <el-tag v-else type="warning" effect="light">未选择租户</el-tag>
        </div>
      </template>

      <el-empty
        v-if="!hasTenantWorkspace"
        description="请先在顶部选择要治理的租户，再配置租户级菜单覆盖。"
      />
      <div v-else class="workspace-grid">
        <el-card class="card inner-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>租户覆盖树</span>
              <el-input v-model="tenantRuleFilterText" size="small" clearable placeholder="搜索菜单名称或编码" class="filter-input"/>
            </div>
          </template>
          <el-tree
            ref="tenantTreeRef"
            :data="tenantMenuRules"
            :props="tenantTreeProps"
            node-key="menuCode"
            highlight-current
            default-expand-all
            :filter-node-method="handleTenantRuleFilter"
            class="menu-tree"
            v-loading="tenantRuleLoading"
            @node-click="handleSelectTenantMenu"
          >
            <template #default="{ data }">
              <div class="menu-tree-node">
                <div class="menu-tree-main">
                  <span class="menu-tree-name">{{ data.menuName }}</span>
                  <span class="menu-tree-code">{{ data.menuCode }}</span>
                </div>
                <div class="tag-group">
                  <el-tag v-if="data.overrideActive" type="warning" effect="light" size="small">已覆盖</el-tag>
                  <el-tag :type="data.status === 0 ? 'danger' : 'success'" effect="plain" size="small">
                    {{ resolveStatusLabel(data.status) }}
                  </el-tag>
                  <el-tag :type="data.visible === 0 ? 'info' : 'primary'" effect="plain" size="small">
                    {{ resolveVisibilityLabel(data.visible) }}
                  </el-tag>
                </div>
              </div>
            </template>
          </el-tree>
        </el-card>

        <el-card class="card inner-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>租户覆盖编辑</span>
              <el-tag v-if="selectedTenantMenu?.permCode" type="info" effect="plain">{{ selectedTenantMenu?.permCode }}</el-tag>
            </div>
          </template>

          <el-empty v-if="!selectedTenantMenu" description="请选择左侧租户菜单节点"/>
          <template v-else>
            <div class="menu-meta">
              <div class="menu-meta-item">
                <div class="menu-meta-label">菜单编码</div>
                <div class="menu-meta-value">{{ selectedTenantMenu.menuCode }}</div>
              </div>
              <div class="menu-meta-item">
                <div class="menu-meta-label">父级菜单</div>
                <div class="menu-meta-value">{{ selectedTenantMenu.parentMenuCode || '根节点' }}</div>
              </div>
              <div class="menu-meta-item">
                <div class="menu-meta-label">当前生效状态</div>
                <div class="menu-meta-value">{{ resolveStatusLabel(selectedTenantMenu.status) }} / {{ resolveVisibilityLabel(selectedTenantMenu.visible) }}</div>
              </div>
              <div class="menu-meta-item">
                <div class="menu-meta-label">覆盖状态</div>
                <div class="menu-meta-value">{{ selectedTenantMenu.overrideActive ? '已配置租户覆盖' : '继承全局' }}</div>
              </div>
            </div>

            <el-alert class="summary-alert" type="info" :closable="false" :title="selectedTenantBaseSummary"/>
            <el-alert class="summary-alert" type="warning" :closable="false" :title="selectedTenantOverrideSummary"/>

            <el-form label-width="136px" class="rule-form">
              <el-form-item label="显示策略">
                <el-select v-model="tenantRuleForm.visible" clearable style="width: 100%" placeholder="不选则继承全局">
                  <el-option :value="1" label="显示"/>
                  <el-option :value="0" label="隐藏"/>
                </el-select>
              </el-form-item>
              <el-form-item label="启用策略">
                <el-select v-model="tenantRuleForm.status" clearable style="width: 100%" placeholder="不选则继承全局">
                  <el-option :value="1" label="启用"/>
                  <el-option :value="0" label="停用"/>
                </el-select>
              </el-form-item>
              <el-form-item label="组织类型覆盖">
                <el-select
                  v-model="tenantRuleForm.orgTypeCodes"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  collapse-tags
                  collapse-tags-tooltip
                  :loading="governanceLoading"
                  style="width: 100%"
                  placeholder="留空表示显式清空该规则"
                >
                  <el-option
                    v-for="item in orgTypeOptions"
                    :key="`tenant-org-type-${item.value}`"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="组织编码覆盖">
                <el-select
                  v-model="tenantRuleForm.orgUnitCodes"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  collapse-tags
                  collapse-tags-tooltip
                  :loading="governanceLoading"
                  style="width: 100%"
                  placeholder="留空表示显式清空该规则"
                >
                  <el-option
                    v-for="item in orgUnitOptions"
                    :key="`tenant-org-unit-${item.value}`"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="组织标签覆盖">
                <el-select
                  v-model="tenantRuleForm.orgTags"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  collapse-tags
                  collapse-tags-tooltip
                  :loading="governanceLoading"
                  style="width: 100%"
                  placeholder="留空表示显式清空该规则"
                >
                  <el-option
                    v-for="item in orgTagOptions"
                    :key="`tenant-org-tag-${item.value}`"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="关系语义覆盖">
                <el-select
                  v-model="tenantRuleForm.requiredRelationTypes"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  collapse-tags
                  collapse-tags-tooltip
                  :loading="governanceLoading"
                  style="width: 100%"
                  placeholder="留空表示显式清空该规则"
                >
                  <el-option
                    v-for="item in relationTypeOptions"
                    :key="`tenant-${item.value}`"
                    :label="`${item.label} (${item.value})`"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
            </el-form>

            <div class="editor-actions">
              <el-button @click="handleResetTenantRule">重置</el-button>
              <el-button v-if="selectedTenantMenu.overrideActive" :loading="tenantClearing" @click="handleClearTenantRule">清除租户覆盖</el-button>
              <el-button :loading="impactLoading" @click="handlePreviewTenantImpact">分析影响</el-button>
              <el-button :loading="releaseSubmitting" @click="handleCreateTenantRelease">生成发布单</el-button>
              <el-button type="primary" :loading="tenantSaving" @click="handleSaveTenantRule">保存租户覆盖</el-button>
            </div>

            <div v-if="tenantImpactResult" class="impact-panel">
              <div class="impact-title">发布前影响分析</div>
              <div class="impact-summary">{{ tenantImpactResult.impactSummary || '暂无影响摘要' }}</div>
              <div class="impact-grid">
                <div class="impact-item">
                  <div class="impact-label">基线命中</div>
                  <div class="impact-value">{{ tenantImpactResult.baselineMatched ? '命中' : '未命中' }}</div>
                </div>
                <div class="impact-item">
                  <div class="impact-label">变更后命中</div>
                  <div class="impact-value">{{ tenantImpactResult.targetMatched ? '命中' : '未命中' }}</div>
                </div>
                <div class="impact-item">
                  <div class="impact-label">基线菜单数</div>
                  <div class="impact-value">{{ tenantImpactResult.baselineMenuCount || 0 }}</div>
                </div>
                <div class="impact-item">
                  <div class="impact-label">变更后菜单数</div>
                  <div class="impact-value">{{ tenantImpactResult.targetMenuCount || 0 }}</div>
                </div>
              </div>
              <div class="impact-rule-grid">
                <div class="impact-rule-item">
                  <div class="impact-label">基线规则</div>
                  <div class="impact-rule-value">{{ tenantImpactResult.baselineRuleSummary || '未配置额外规则' }}</div>
                </div>
                <div class="impact-rule-item">
                  <div class="impact-label">目标规则</div>
                  <div class="impact-rule-value">{{ tenantImpactResult.targetRuleSummary || '未配置额外规则' }}</div>
                </div>
              </div>
              <div v-if="tenantImpactResult.changedDimensions?.length" class="impact-tag-list">
                <el-tag v-for="item in tenantImpactResult.changedDimensions" :key="`tenant-impact-${item}`" type="warning">
                  {{ item }}
                </el-tag>
              </div>
              <div v-if="tenantImpactResult.addedMenus?.length" class="impact-diff-section">
                <div class="impact-diff-title success">新增命中菜单</div>
                <div class="impact-tag-list">
                  <el-tag v-for="item in tenantImpactResult.addedMenus" :key="`tenant-added-${item.menuCode}`" type="success">
                    {{ item.menuName || item.menuCode }}
                  </el-tag>
                </div>
              </div>
              <div v-if="tenantImpactResult.removedMenus?.length" class="impact-diff-section">
                <div class="impact-diff-title danger">移除命中菜单</div>
                <div class="impact-tag-list">
                  <el-tag v-for="item in tenantImpactResult.removedMenus" :key="`tenant-removed-${item.menuCode}`" type="danger">
                    {{ item.menuName || item.menuCode }}
                  </el-tag>
                </div>
              </div>
            </div>
          </template>
        </el-card>
      </div>
    </el-card>

    <el-card class="card preview-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>菜单命中预览</span>
          <el-tag v-if="hasPreviewTenant" type="success" effect="light">
            {{ activeTenantName || activeTenantId || authStore.tenantName || authStore.tenantId || '当前租户' }}
          </el-tag>
          <el-tag v-else type="warning" effect="light">未选择租户</el-tag>
        </div>
      </template>

      <div class="preview-layout">
        <div class="preview-form">
          <el-form label-width="132px">
            <el-form-item label="权限编码">
              <el-select
                v-model="previewForm.permissionCodes"
                multiple
                filterable
                allow-create
                default-first-option
                collapse-tags
                collapse-tags-tooltip
                style="width: 100%"
                :loading="permissionLoading"
                placeholder="可从权限清单选择，也可手工输入"
              >
                <el-option
                  v-for="item in permissions"
                  :key="item.id"
                  :label="`${item.permName} (${item.permCode})`"
                  :value="item.permCode"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="组织类型编码">
              <el-select
                v-model="previewForm.orgTypeCodes"
                multiple
                filterable
                allow-create
                default-first-option
                collapse-tags
                collapse-tags-tooltip
                :loading="governanceLoading"
                style="width: 100%"
                placeholder="例如 CHAIN_STORE / GOVERNMENT_STANDARD"
              >
                <el-option
                  v-for="item in orgTypeOptions"
                  :key="`preview-org-type-${item.value}`"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="直接组织编码">
              <el-select
                v-model="previewForm.orgUnitCodes"
                multiple
                filterable
                allow-create
                default-first-option
                collapse-tags
                collapse-tags-tooltip
                :loading="governanceLoading"
                style="width: 100%"
                placeholder="例如 STORE_001 / GOV_SERVICE_HALL"
              >
                <el-option
                  v-for="item in orgUnitOptions"
                  :key="`preview-org-unit-${item.value}`"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="组织标签">
              <el-select
                v-model="previewForm.orgTags"
                multiple
                filterable
                allow-create
                default-first-option
                collapse-tags
                collapse-tags-tooltip
                :loading="governanceLoading"
                style="width: 100%"
                placeholder="例如 直营 / 服务 / 教务"
              >
                <el-option
                  v-for="item in orgTagOptions"
                  :key="`preview-org-tag-${item.value}`"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="关系语义">
              <el-select
                v-model="previewForm.relationTypes"
                multiple
                filterable
                allow-create
                default-first-option
                collapse-tags
                collapse-tags-tooltip
                :loading="governanceLoading"
                style="width: 100%"
                placeholder="例如 SUPERVISE / SERVICE"
              >
                <el-option
                  v-for="item in relationTypeOptions"
                  :key="`preview-${item.value}`"
                  :label="`${item.label} (${item.value})`"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="体验账号模式">
              <el-switch v-model="previewForm.experience"/>
            </el-form-item>
            <el-form-item label="菜单白名单">
              <el-select
                v-model="previewForm.menuWhitelist"
                multiple
                filterable
                allow-create
                default-first-option
                collapse-tags
                collapse-tags-tooltip
                style="width: 100%"
                placeholder="仅在体验账号模式下生效"
              />
            </el-form-item>
          </el-form>

          <div class="preview-actions">
            <el-button @click="fillPreviewFromSession">填充当前会话</el-button>
            <el-button @click="handleResetPreview">清空结果</el-button>
            <el-button type="primary" :loading="previewLoading" @click="handlePreview">开始预览</el-button>
          </div>
        </div>

        <div class="preview-result">
          <div class="preview-summary">
            <div class="preview-stat">
              <div class="preview-stat-label">命中菜单数</div>
              <div class="preview-stat-value">{{ previewExecuted ? previewMenuCount : '-' }}</div>
            </div>
            <div class="preview-hint">
              预览结果会同时考虑菜单权限、组织适配规则、关系语义与体验账号白名单，规则编辑区的“分析影响”也会复用这里的样本上下文。
            </div>
          </div>

          <div class="preview-tree-panel" v-loading="previewLoading">
            <el-empty v-if="previewExecuted && previewMenus.length === 0" description="当前上下文没有命中任何菜单"/>
            <el-empty v-else-if="!previewExecuted" description="填写预览条件后点击“开始预览”"/>
            <el-tree
              v-else
              :data="previewMenus"
              :props="previewTreeProps"
              node-key="menuCode"
              default-expand-all
              class="menu-tree preview-tree"
            >
              <template #default="{ data }">
                <div class="menu-tree-node">
                  <div class="menu-tree-main">
                    <span class="menu-tree-name">{{ data.menuName }}</span>
                    <span class="menu-tree-code">{{ data.menuCode }}</span>
                  </div>
                  <span class="menu-tree-path">{{ data.path }}</span>
                </div>
              </template>
            </el-tree>
          </div>
        </div>
      </div>
    </el-card>

    <el-card v-if="hasTenantWorkspace" class="card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>菜单发布单</span>
          <div class="tag-group">
            <el-tag type="info" effect="plain">租户：{{ activeTenantName || activeTenantId }}</el-tag>
            <el-button size="small" :loading="releaseLoading" @click="loadReleaseRecords">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="releaseRecords" border v-loading="releaseLoading">
        <el-table-column prop="createTime" label="创建时间" min-width="160"/>
        <el-table-column prop="releaseTitle" label="发布单标题" min-width="220" show-overflow-tooltip/>
        <el-table-column label="类型" width="110">
          <template #default="{row}">
            <el-tag type="info" effect="plain">{{ resolveReleaseTypeLabel(row.releaseType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="menuName" label="菜单" min-width="180" show-overflow-tooltip/>
        <el-table-column label="状态" width="110">
          <template #default="{row}">
            <el-tag :type="resolveReleaseStatusType(row.releaseStatus)">{{ resolveReleaseStatusLabel(row.releaseStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="impactSummary" label="影响摘要" min-width="280" show-overflow-tooltip/>
        <el-table-column prop="requestedByName" label="创建人" min-width="140" show-overflow-tooltip/>
        <el-table-column prop="reviewedByName" label="审核人" min-width="140" show-overflow-tooltip/>
        <el-table-column prop="appliedByName" label="应用人" min-width="140" show-overflow-tooltip/>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{row}">
            <el-button
              v-if="row.releaseStatus === 'DRAFT'"
              link
              type="success"
              :disabled="releaseSubmitting"
              @click="handleReviewRelease(row, 'APPROVED')"
            >
              审核通过
            </el-button>
            <el-button
              v-if="row.releaseStatus === 'DRAFT'"
              link
              type="danger"
              :disabled="releaseSubmitting"
              @click="handleReviewRelease(row, 'REJECTED')"
            >
              驳回
            </el-button>
            <el-button
              v-if="row.releaseStatus === 'APPROVED'"
              link
              type="primary"
              :disabled="releaseSubmitting"
              @click="handleApplyRelease(row)"
            >
              应用
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
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

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.stat-card {
  border: 1px solid #dbe4f0;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  padding: 16px;
}

.stat-card.highlight {
  border-color: #bfdbfe;
  background: linear-gradient(180deg, #f8fbff 0%, #eff6ff 100%);
}

.stat-card.accent {
  border-color: #fde68a;
  background: linear-gradient(180deg, #fffdf4 0%, #fef3c7 100%);
}

.stat-label {
  font-size: 13px;
  color: #64748b;
}

.stat-value {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 600;
  color: #0f172a;
}

.stat-value.small {
  font-size: 18px;
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(300px, 0.9fr) minmax(420px, 1.4fr);
  gap: 16px;
}

.tree-card,
.editor-card,
.inner-card {
  min-height: 560px;
}

.filter-input {
  width: 220px;
}

.menu-tree {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  max-height: 620px;
  overflow: auto;
}

.menu-tree-node {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.menu-tree-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.menu-tree-name {
  font-weight: 600;
  color: #0f172a;
}

.menu-tree-code,
.menu-tree-path {
  font-size: 12px;
  color: #94a3b8;
}

.tag-group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.menu-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.menu-meta-item {
  border: 1px solid #dbe4f0;
  border-radius: 14px;
  background: #f8fafc;
  padding: 14px;
}

.menu-meta-label {
  font-size: 12px;
  color: #64748b;
}

.menu-meta-value {
  margin-top: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  word-break: break-all;
}

.summary-alert {
  margin-top: 16px;
}

.rule-form {
  margin-top: 18px;
}

.editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.impact-panel {
  margin-top: 16px;
  border: 1px solid #dbe4f0;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  padding: 16px;
}

.impact-title {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}

.impact-summary {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.7;
  color: #334155;
}

.impact-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.impact-item,
.impact-rule-item {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
  padding: 12px;
}

.impact-label {
  font-size: 12px;
  color: #64748b;
}

.impact-value,
.impact-rule-value {
  margin-top: 8px;
  color: #0f172a;
  line-height: 1.7;
  word-break: break-all;
}

.impact-rule-grid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.impact-tag-list {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.impact-diff-section {
  margin-top: 14px;
}

.impact-diff-title {
  font-size: 13px;
  font-weight: 600;
}

.impact-diff-title.success {
  color: #15803d;
}

.impact-diff-title.danger {
  color: #b91c1c;
}

.tenant-card {
  overflow: hidden;
}

.preview-layout {
  display: grid;
  grid-template-columns: minmax(360px, 1fr) minmax(320px, 1fr);
  gap: 18px;
}

.preview-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.preview-stat {
  border: 1px solid #dbe4f0;
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  padding: 14px 16px;
  min-width: 160px;
}

.preview-stat-label {
  font-size: 12px;
  color: #64748b;
}

.preview-stat-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 600;
  color: #0f172a;
}

.preview-hint {
  flex: 1;
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}

.preview-tree-panel {
  border: 1px solid #dbe4f0;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  min-height: 420px;
  padding: 14px;
}

.preview-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
}

@media (max-width: 1100px) {
  .workspace-grid,
  .preview-layout {
    grid-template-columns: 1fr;
  }

  .menu-meta {
    grid-template-columns: 1fr;
  }

  .preview-summary {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
