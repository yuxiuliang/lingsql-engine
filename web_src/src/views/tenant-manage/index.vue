<script setup lang="ts">
import {computed, reactive, ref} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import {
  createTenant,
  deleteTenant,
  extendTenantLifecycle,
  fetchTenantNotificationChannelHealth,
  fetchTenantLifecycleWorkbench,
  fetchTenants,
  fetchTenantLifecycleSummary,
  reactivateTenant,
  TenantNotificationChannelHealthView,
  TenantNotificationChannelIssueView,
  TenantNotificationChannelTestView,
  TenantCreateRequest,
  TenantLifecycleExtendRequest,
  TenantLifecycleOpsTaskView,
  TenantLifecycleSummaryView,
  TenantLifecycleWorkbenchView,
  TenantUpdateRequest,
  TenantView,
  testTenantNotificationChannels,
  updateTenant
} from '@/api/tenant';
import {BillingPlanView, fetchPlans} from '@/api/plan';
import {fetchOrgTemplates, OrgTemplateView} from '@/api/organization';
import {useAuthStore} from '@/store/auth';
import {
  buildMysqlJdbcUrl,
  createEmptyMysqlConnectionForm,
  DEFAULT_MYSQL_DRIVER_CLASS,
  DEFAULT_MYSQL_PORT,
  parseMysqlJdbcUrl
} from '@/utils/mysql-jdbc';

const authStore = useAuthStore();
const tenants = ref<TenantView[]>([]);
const lifecycleSummary = ref<TenantLifecycleSummaryView | null>(null);
const lifecycleWorkbench = ref<TenantLifecycleWorkbenchView | null>(null);
const notificationHealth = ref<TenantNotificationChannelHealthView | null>(null);
const loading = ref(false);
const workbenchLoading = ref(false);
const notificationHealthLoading = ref(false);
const planList = ref<BillingPlanView[]>([]);
const orgTemplateList = ref<OrgTemplateView[]>([]);
const dialogVisible = ref(false);
const lifecycleDialogVisible = ref(false);
const notificationTestDialogVisible = ref(false);
const lifecycleTargetTenant = ref<TenantView | null>(null);
const editingId = ref<number | null>(null);
const notificationTestLoading = ref(false);
const notificationTestContext = ref('');
const notificationTestResult = ref<TenantNotificationChannelTestView | null>(null);
const initConfig = ref(false);
const notificationIssueMap = computed(() => {
  const map = new Map<string, TenantNotificationChannelIssueView>();
  (notificationHealth.value?.issues || []).forEach((item) => {
    if (item.tenantId) {
      map.set(item.tenantId, item);
    }
  });
  return map;
});
const planOptions = computed(() => {
  const options = new Set<string>();
  planList.value.forEach((plan) => {
    if (plan.planName && plan.planName.trim()) {
      options.add(plan.planName.trim());
    }
  });
  tenants.value.forEach((tenant) => {
    if (tenant.planName && tenant.planName.trim()) {
      options.add(tenant.planName.trim());
    }
  });
  return Array.from(options);
});

/**
 * 租户表单定义。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
interface TenantForm {
  tenantId: string;
  tenantName: string;
  status: number;
  remark: string;
  contactName: string;
  contactPhone: string;
  contactEmail: string;
  notificationChannels: string[];
  expireTime: string;
  planName: string;
  isolationMode: string;
  jdbcUrl: string;
  username: string;
  password: string;
  driverClass: string;
  logicColumn: string;
  orgTemplateCode: string;
  orgRootUnitName: string;
}

const form = reactive<TenantForm>({
  tenantId: '',
  tenantName: '',
  status: 1,
  remark: '',
  contactName: '',
  contactPhone: '',
  contactEmail: '',
  notificationChannels: [],
  expireTime: '',
  planName: '',
  isolationMode: 'PHYSICAL',
  jdbcUrl: '',
  username: '',
  password: '',
  driverClass: DEFAULT_MYSQL_DRIVER_CLASS,
  logicColumn: 'tenant_id',
  orgTemplateCode: '',
  orgRootUnitName: ''
});
const jdbcForm = reactive(createEmptyMysqlConnectionForm());
const jdbcManualMode = ref(false);
const jdbcPreview = computed(() => buildMysqlJdbcUrl(jdbcForm));
const selectedOrgTemplate = computed(() => {
  return orgTemplateList.value.find((item) => item.templateCode === form.orgTemplateCode) || null;
});

const notificationChannelOptions = [
  {label: '通用 Webhook', value: 'webhook'},
  {label: '钉钉机器人', value: 'dingtalk'},
  {label: '企业微信机器人', value: 'wecom'}
];

/**
 * 解析通知通道名称。
 *
 * @param channel 通道编码
 * @return 展示名称
 * @author 余炘洋
 * @date 2026-04-15
 */
const resolveNotificationChannelLabel = (channel?: string) => {
  return notificationChannelOptions.find((item) => item.value === channel)?.label || channel || '未知通道';
};

/**
 * 生命周期续期表单定义。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
interface TenantLifecycleForm {
  mode: 'days' | 'date';
  extendDays: number;
  expireTime: string;
  activate: boolean;
}

const lifecycleForm = reactive<TenantLifecycleForm>({
  mode: 'days',
  extendDays: 30,
  expireTime: '',
  activate: true
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
 * 初始化表单。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const resetForm = () => {
  form.tenantId = '';
  form.tenantName = '';
  form.status = 1;
  form.remark = '';
  form.contactName = '';
  form.contactPhone = '';
  form.contactEmail = '';
  form.notificationChannels = [];
  form.expireTime = '';
  form.planName = '';
  form.isolationMode = 'PHYSICAL';
  form.jdbcUrl = '';
  form.username = '';
  form.password = '';
  form.driverClass = DEFAULT_MYSQL_DRIVER_CLASS;
  form.logicColumn = 'tenant_id';
  form.orgTemplateCode = '';
  form.orgRootUnitName = '';
  syncJdbcForm(form.jdbcUrl);
};

/**
 * 回填 JDBC 简化表单。
 *
 * @param jdbcUrl JDBC 地址
 * @author 余炘洋
 * @date 2026-04-10
 */
const syncJdbcForm = (jdbcUrl?: string) => {
  const parsed = parseMysqlJdbcUrl(jdbcUrl);
  jdbcForm.host = parsed.host;
  jdbcForm.port = parsed.port || DEFAULT_MYSQL_PORT;
  jdbcForm.databaseName = parsed.databaseName;
  jdbcForm.params = parsed.params;
  jdbcManualMode.value = parsed.manualMode;
};

/**
 * 切换 JDBC 手动模式。
 *
 * @param value 是否手动填写
 * @author 余炘洋
 * @date 2026-04-10
 */
const handleJdbcManualModeChange = (value: boolean) => {
  if (value) {
    form.jdbcUrl = form.jdbcUrl.trim() || jdbcPreview.value;
    return;
  }
  if (!jdbcForm.port.trim()) {
    jdbcForm.port = DEFAULT_MYSQL_PORT;
  }
};

/**
 * 解析初始化配置使用的 JDBC 地址。
 *
 * @return JDBC 地址
 * @author 余炘洋
 * @date 2026-04-10
 */
const resolveJdbcUrl = () => {
  return jdbcManualMode.value ? form.jdbcUrl.trim() : jdbcPreview.value;
};

/**
 * 校验初始化数据源配置。
 *
 * @return 合法 JDBC 地址
 * @author 余炘洋
 * @date 2026-04-10
 */
const validateInitJdbcUrl = (): string | null => {
  if (jdbcManualMode.value) {
    const jdbcUrl = resolveJdbcUrl();
    if (!jdbcUrl) {
      ElMessage.warning('请填写 JDBC 地址');
      return null;
    }
    return jdbcUrl;
  }
  if (!jdbcForm.host.trim()) {
    ElMessage.warning('请填写数据库 IP 或主机名');
    return null;
  }
  if (!jdbcForm.databaseName.trim()) {
    ElMessage.warning('请填写数据库名');
    return null;
  }
  const jdbcUrl = jdbcPreview.value;
  if (!jdbcUrl) {
    ElMessage.warning('请补全数据库连接信息');
    return null;
  }
  return jdbcUrl;
};

/**
 * 加载租户列表。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const loadTenants = async () => {
  loading.value = true;
  try {
    const list = await fetchTenants();
    tenants.value = [...list].sort((a, b) => (b.riskScore || 0) - (a.riskScore || 0));
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载租户失败');
  } finally {
    loading.value = false;
  }
};

/**
 * 加载生命周期汇总。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
const loadLifecycleSummary = async () => {
  try {
    lifecycleSummary.value = await fetchTenantLifecycleSummary();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载生命周期汇总失败');
  }
};

/**
 * 加载生命周期运营工作台。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
const loadLifecycleWorkbench = async () => {
  workbenchLoading.value = true;
  try {
    lifecycleWorkbench.value = await fetchTenantLifecycleWorkbench(50);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载运营任务失败');
  } finally {
    workbenchLoading.value = false;
  }
};

/**
 * 加载通知通道健康情况。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
const loadNotificationHealth = async () => {
  notificationHealthLoading.value = true;
  try {
    notificationHealth.value = await fetchTenantNotificationChannelHealth();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载通知通道健康情况失败');
  } finally {
    notificationHealthLoading.value = false;
  }
};

/**
 * 加载套餐列表。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
const loadPlans = async () => {
  try {
    planList.value = await fetchPlans();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载套餐失败');
  }
};

/**
 * 加载组织模板列表。
 *
 * @author 余炘洋
 * @date 2026-04-13
 */
const loadOrgTemplates = async () => {
  try {
    orgTemplateList.value = await fetchOrgTemplates();
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载组织模板失败';
    if (!message.includes('无权限')) {
      ElMessage.error(message);
    }
  }
};

/**
 * 刷新租户数据与生命周期看板。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
const refreshTenantData = async () => {
  await Promise.all([
    loadTenants(),
    loadLifecycleSummary(),
    loadLifecycleWorkbench(),
    loadNotificationHealth()
  ]);
};

/**
 * 打开新增弹窗。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleCreate = () => {
  editingId.value = null;
  initConfig.value = false;
  resetForm();
  dialogVisible.value = true;
};

/**
 * 打开编辑弹窗。
 *
 * @param row 租户信息
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleEdit = (row: TenantView) => {
  editingId.value = row.id;
  initConfig.value = false;
  form.tenantId = row.tenantId;
  form.tenantName = row.tenantName;
  form.status = row.status ?? 1;
  form.remark = row.remark || '';
  form.contactName = row.contactName || '';
  form.contactPhone = row.contactPhone || '';
  form.contactEmail = row.contactEmail || '';
  form.notificationChannels = row.notificationChannels || [];
  form.expireTime = row.expireTime || '';
  form.planName = row.planName || '';
  dialogVisible.value = true;
};

/**
 * 构建新增请求体。
 *
 * @return 创建请求
 * @author 余炘洋
 * @date 2026-01-26
 */
const buildCreatePayload = (jdbcUrl?: string): TenantCreateRequest => {
  const payload: TenantCreateRequest = {
    tenantName: form.tenantName.trim(),
    status: form.status,
    remark: form.remark || undefined,
    contactName: form.contactName || undefined,
    contactPhone: form.contactPhone || undefined,
    contactEmail: form.contactEmail || undefined,
    notificationChannels: form.notificationChannels,
    expireTime: form.expireTime || undefined,
    planName: form.planName || undefined
  };
  if (initConfig.value) {
    payload.isolationMode = form.isolationMode;
    payload.jdbcUrl = jdbcUrl || undefined;
    payload.username = form.username.trim() || undefined;
    payload.password = form.password || undefined;
    payload.driverClass = form.driverClass.trim() || DEFAULT_MYSQL_DRIVER_CLASS;
    payload.logicColumn = form.logicColumn.trim() || undefined;
  }
  payload.orgTemplateCode = form.orgTemplateCode || undefined;
  payload.orgRootUnitName = form.orgTemplateCode
    ? (form.orgRootUnitName.trim() || form.tenantName.trim() || undefined)
    : undefined;
  return payload;
};

/**
 * 构建更新请求体。
 *
 * @return 更新请求
 * @author 余炘洋
 * @date 2026-01-26
 */
const buildUpdatePayload = (): TenantUpdateRequest => {
  return {
    tenantName: form.tenantName.trim(),
    status: form.status,
    remark: form.remark || undefined,
    contactName: form.contactName || undefined,
    contactPhone: form.contactPhone || undefined,
    contactEmail: form.contactEmail || undefined,
    notificationChannels: form.notificationChannels,
    expireTime: form.expireTime || undefined,
    planName: form.planName || undefined
  };
};

/**
 * 保存租户。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleSave = async () => {
  if (!form.tenantName.trim()) {
    ElMessage.warning('请填写租户名称');
    return;
  }
  const jdbcUrl = !editingId.value && initConfig.value ? validateInitJdbcUrl() : null;
  if (!editingId.value && initConfig.value && !jdbcUrl) {
    return;
  }
  try {
    if (editingId.value) {
      await updateTenant(editingId.value, buildUpdatePayload());
      ElMessage.success('租户已更新');
    } else {
      await createTenant(buildCreatePayload(jdbcUrl || undefined));
      ElMessage.success('租户已创建');
    }
    dialogVisible.value = false;
    await refreshTenantData();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  }
};

/**
 * 测试当前表单中的默认通知通道。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
const handleTestFormNotificationChannels = async () => {
  if (!form.notificationChannels.length) {
    ElMessage.warning('请先选择至少一个默认通知通道');
    return;
  }
  notificationTestLoading.value = true;
  try {
    notificationTestResult.value = await testTenantNotificationChannels({
      tenantId: form.tenantId || undefined,
      tenantName: form.tenantName.trim() || undefined,
      channels: form.notificationChannels
    });
    notificationTestContext.value = editingId.value ? '当前租户配置' : '开户表单配置';
    notificationTestDialogVisible.value = true;
    ElMessage.success(notificationTestResult.value.success ? '通知通道测试通过' : '通知通道测试已完成，请查看诊断');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '测试通知通道失败');
  } finally {
    notificationTestLoading.value = false;
  }
};

/**
 * 测试租户当前默认通知通道。
 *
 * @param row 租户信息
 * @author 余炘洋
 * @date 2026-04-15
 */
const handleTestTenantNotificationChannels = async (row: TenantView) => {
  if (!row.notificationChannels?.length) {
    ElMessage.warning('该租户尚未配置默认通知通道');
    return;
  }
  notificationTestLoading.value = true;
  try {
    notificationTestResult.value = await testTenantNotificationChannels({
      tenantId: row.tenantId,
      tenantName: row.tenantName,
      channels: row.notificationChannels
    });
    notificationTestContext.value = `租户列表 / ${row.tenantName}`;
    notificationTestDialogVisible.value = true;
    ElMessage.success(notificationTestResult.value.success ? '通知通道测试通过' : '通知通道测试已完成，请查看诊断');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '测试通知通道失败');
  } finally {
    notificationTestLoading.value = false;
  }
};

/**
 * 删除租户。
 *
 * @param row 租户信息
 * @author 余炘洋
 * @date 2026-01-26
 */
const handleDelete = async (row: TenantView) => {
  try {
    await ElMessageBox.confirm('确认删除该租户？', '提示', {type: 'warning'});
    await deleteTenant(row.id);
    ElMessage.success('租户已删除');
    await refreshTenantData();
  } catch (error) {
    if (!isMessageBoxCancel(error)) {
      ElMessage.error(error instanceof Error ? error.message : '删除失败');
    }
  }
};

/**
 * 解析状态文本。
 *
 * @param status 状态值
 * @return 文案
 * @author 余炘洋
 * @date 2026-01-26
 */
const resolveStatusText = (status?: number) => {
  return status === 1 ? '启用' : '停用';
};

/**
 * 解析状态标签样式。
 *
 * @param status 状态值
 * @return 标签类型
 * @author 余炘洋
 * @date 2026-01-26
 */
const resolveStatusType = (status?: number) => {
  return status === 1 ? 'success' : 'info';
};

/**
 * 解析测试结果标签样式。
 *
 * @param success 是否成功
 * @return 标签类型
 * @author 余炘洋
 * @date 2026-04-15
 */
const resolveNotificationTestType = (success?: boolean) => {
  return success ? 'success' : 'danger';
};

/**
 * 解析通知健康状态标签类型。
 *
 * @param enabled 是否启用
 * @return 标签类型
 * @author 余炘洋
 * @date 2026-04-15
 */
const resolveNotificationHealthType = (enabled?: boolean) => {
  return enabled ? 'success' : 'danger';
};

/**
 * 获取租户通知通道风险项。
 *
 * @param row 租户信息
 * @return 风险项
 * @author 余炘洋
 * @date 2026-04-15
 */
const resolveTenantNotificationIssue = (row: TenantView) => {
  if (!row.tenantId) {
    return null;
  }
  return notificationIssueMap.value.get(row.tenantId) || null;
};

/**
 * 格式化时间文本。
 *
 * @param value 时间字符串
 * @return 展示文本
 * @author 余炘洋
 * @date 2026-04-15
 */
const formatDateTime = (value?: string) => {
  if (!value) {
    return '--';
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleString('zh-CN', {hour12: false});
};

/**
 * 解析生命周期状态文本。
 *
 * @param state 生命周期状态
 * @return 文案
 * @author 余炘洋
 * @date 2026-02-27
 */
const resolveLifecycleText = (state?: string) => {
  if (!state) {
    return '未知';
  }
  const map: Record<string, string> = {
    ACTIVE: '正常',
    EXPIRING_SOON: '即将到期',
    GRACE_PERIOD: '宽限期',
    SUSPENDED: '已冻结',
    DISABLED: '已停用'
  };
  return map[state] || state;
};

/**
 * 解析生命周期状态标签样式。
 *
 * @param state 生命周期状态
 * @return 标签类型
 * @author 余炘洋
 * @date 2026-02-27
 */
const resolveLifecycleType = (state?: string) => {
  if (state === 'ACTIVE') {
    return 'success';
  }
  if (state === 'EXPIRING_SOON' || state === 'GRACE_PERIOD') {
    return 'warning';
  }
  if (state === 'SUSPENDED' || state === 'DISABLED') {
    return 'danger';
  }
  return 'info';
};

/**
 * 解析剩余到期天数显示。
 *
 * @param days 天数
 * @return 文案
 * @author 余炘洋
 * @date 2026-02-27
 */
const resolveDaysToExpireText = (days?: number | null) => {
  if (days === null || days === undefined) {
    return '--';
  }
  if (days >= 0) {
    return `${days} 天`;
  }
  return `已过期 ${Math.abs(days)} 天`;
};

/**
 * 解析风险等级文本。
 *
 * @param level 风险等级
 * @return 文案
 * @author 余炘洋
 * @date 2026-02-27
 */
const resolveRiskText = (level?: string) => {
  if (!level) {
    return '低风险';
  }
  const map: Record<string, string> = {
    LOW: '低风险',
    MEDIUM: '中风险',
    HIGH: '高风险',
    CRITICAL: '严重风险'
  };
  return map[level] || level;
};

/**
 * 解析风险等级标签样式。
 *
 * @param level 风险等级
 * @return 标签类型
 * @author 余炘洋
 * @date 2026-02-27
 */
const resolveRiskType = (level?: string) => {
  if (level === 'CRITICAL') {
    return 'danger';
  }
  if (level === 'HIGH') {
    return 'warning';
  }
  if (level === 'MEDIUM') {
    return 'info';
  }
  return 'success';
};

/**
 * 解析风险原因提示。
 *
 * @param reasons 风险原因
 * @return 提示文案
 * @author 余炘洋
 * @date 2026-02-27
 */
const resolveRiskReasonText = (reasons?: string[]) => {
  if (!reasons || reasons.length === 0) {
    return '经营状态稳定';
  }
  return reasons.join('；');
};

/**
 * 解析任务优先级文案。
 *
 * @param priority 优先级
 * @return 文案
 * @author 余炘洋
 * @date 2026-02-27
 */
const resolvePriorityText = (priority?: string) => {
  if (priority === 'P0') {
    return 'P0 紧急';
  }
  if (priority === 'P1') {
    return 'P1 高';
  }
  return 'P2 中';
};

/**
 * 解析任务优先级标签样式。
 *
 * @param priority 优先级
 * @return 标签类型
 * @author 余炘洋
 * @date 2026-02-27
 */
const resolvePriorityType = (priority?: string) => {
  if (priority === 'P0') {
    return 'danger';
  }
  if (priority === 'P1') {
    return 'warning';
  }
  return 'info';
};

/**
 * 执行运营任务。
 *
 * @param task 运营任务
 * @author 余炘洋
 * @date 2026-02-27
 */
const handleProcessTask = async (task: TenantLifecycleOpsTaskView) => {
  const tenant = tenants.value.find((item) => item.tenantId === task.tenantId);
  if (!tenant) {
    ElMessage.warning('租户不在当前列表，请先刷新');
    return;
  }
  if (tenant.lifecycleState === 'SUSPENDED' || tenant.lifecycleState === 'DISABLED' || tenant.status !== 1) {
    await handleReactivate(tenant);
    return;
  }
  openLifecycleDialog(tenant);
  if (task.priority === 'P0') {
    lifecycleForm.extendDays = 90;
  } else if (task.priority === 'P1') {
    lifecycleForm.extendDays = 60;
  } else {
    lifecycleForm.extendDays = 30;
  }
  lifecycleForm.activate = true;
};

/**
 * 重置生命周期续期表单。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
const resetLifecycleForm = () => {
  lifecycleForm.mode = 'days';
  lifecycleForm.extendDays = 30;
  lifecycleForm.expireTime = '';
  lifecycleForm.activate = true;
};

/**
 * 打开自定义续期弹窗。
 *
 * @param row 租户信息
 * @author 余炘洋
 * @date 2026-02-27
 */
const openLifecycleDialog = (row: TenantView) => {
  lifecycleTargetTenant.value = row;
  resetLifecycleForm();
  lifecycleDialogVisible.value = true;
};

/**
 * 执行自定义续期。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
const handleCustomExtend = async () => {
  if (!lifecycleTargetTenant.value) {
    return;
  }
  const payload: TenantLifecycleExtendRequest = {
    activate: lifecycleForm.activate
  };
  if (lifecycleForm.mode === 'days') {
    const days = Math.floor(Number(lifecycleForm.extendDays));
    if (!Number.isFinite(days) || days < 1) {
      ElMessage.warning('续期天数必须大于 0');
      return;
    }
    payload.extendDays = days;
  } else {
    if (!lifecycleForm.expireTime) {
      ElMessage.warning('请选择目标到期时间');
      return;
    }
    payload.expireTime = lifecycleForm.expireTime;
  }
  try {
    await extendTenantLifecycle(lifecycleTargetTenant.value.id, payload);
    ElMessage.success('租户续期成功');
    closeLifecycleDialog();
    await refreshTenantData();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '续期失败');
  }
};

/**
 * 关闭续期弹窗。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
const closeLifecycleDialog = () => {
  lifecycleDialogVisible.value = false;
  lifecycleTargetTenant.value = null;
};

/**
 * 快捷续期 30 天并恢复服务。
 *
 * @param row 租户信息
 * @author 余炘洋
 * @date 2026-02-27
 */
const handleExtend30Days = async (row: TenantView) => {
  try {
    await extendTenantLifecycle(row.id, {
      extendDays: 30,
      activate: true
    });
    ElMessage.success('已续期 30 天并恢复服务');
    await refreshTenantData();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '续期失败');
  }
};

/**
 * 快捷恢复服务。
 *
 * @param row 租户信息
 * @author 余炘洋
 * @date 2026-02-27
 */
const handleReactivate = async (row: TenantView) => {
  try {
    await reactivateTenant(row.id);
    ElMessage.success('租户服务已恢复');
    await refreshTenantData();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '恢复失败');
  }
};

/**
 * 是否展示“恢复服务”按钮。
 *
 * @param row 租户信息
 * @return 是否显示
 * @author 余炘洋
 * @date 2026-02-27
 */
const shouldShowReactivate = (row: TenantView) => {
  if (row.status !== 1) {
    return true;
  }
  return row.lifecycleState === 'SUSPENDED' || row.lifecycleState === 'DISABLED';
};

/**
 * 判断是否为弹窗取消。
 *
 * @param error 异常对象
 * @return 是否取消
 * @author 余炘洋
 * @date 2026-01-26
 */
const isMessageBoxCancel = (error: unknown) => {
  return error === 'cancel' || error === 'close';
};

loadPlans();
loadOrgTemplates();
refreshTenantData();
</script>

<template>
  <div class="page">
    <div class="page-title">租户管理</div>
    <div class="page-desc">维护租户信息并执行初始化配置。</div>
    <div class="summary-grid">
      <el-card shadow="never" class="summary-card">
        <div class="summary-label">总租户</div>
        <div class="summary-value">{{ lifecycleSummary?.total ?? 0 }}</div>
      </el-card>
      <el-card shadow="never" class="summary-card">
        <div class="summary-label">正常</div>
        <div class="summary-value success">{{ lifecycleSummary?.active ?? 0 }}</div>
      </el-card>
      <el-card shadow="never" class="summary-card">
        <div class="summary-label">即将到期</div>
        <div class="summary-value warning">{{ lifecycleSummary?.expiringSoon ?? 0 }}</div>
      </el-card>
      <el-card shadow="never" class="summary-card">
        <div class="summary-label">宽限期</div>
        <div class="summary-value warning">{{ lifecycleSummary?.gracePeriod ?? 0 }}</div>
      </el-card>
      <el-card shadow="never" class="summary-card">
        <div class="summary-label">已冻结</div>
        <div class="summary-value danger">{{ lifecycleSummary?.suspended ?? 0 }}</div>
      </el-card>
      <el-card shadow="never" class="summary-card">
        <div class="summary-label">已停用</div>
        <div class="summary-value">{{ lifecycleSummary?.disabled ?? 0 }}</div>
      </el-card>
      <el-card shadow="never" class="summary-card">
        <div class="summary-label">中高风险</div>
        <div class="summary-value warning">{{ lifecycleSummary?.atRisk ?? 0 }}</div>
      </el-card>
      <el-card shadow="never" class="summary-card">
        <div class="summary-label">高风险</div>
        <div class="summary-value warning">{{ lifecycleSummary?.highRisk ?? 0 }}</div>
      </el-card>
      <el-card shadow="never" class="summary-card">
        <div class="summary-label">严重风险</div>
        <div class="summary-value danger">{{ lifecycleSummary?.criticalRisk ?? 0 }}</div>
      </el-card>
    </div>

    <el-card class="card workbench-card" shadow="never">
      <template #header>
        <div class="workbench-header">
          <span>运营任务清单</span>
          <span class="workbench-time">更新时间：{{ lifecycleWorkbench?.generatedAt || '--' }}</span>
        </div>
      </template>
      <div class="workbench-metrics">
        <el-tag type="danger">P0 {{ lifecycleWorkbench?.p0Count ?? 0 }}</el-tag>
        <el-tag type="warning">P1 {{ lifecycleWorkbench?.p1Count ?? 0 }}</el-tag>
        <el-tag type="info">P2 {{ lifecycleWorkbench?.p2Count ?? 0 }}</el-tag>
        <span class="workbench-total">共 {{ lifecycleWorkbench?.totalTasks ?? 0 }} 条</span>
      </div>
      <el-empty v-if="(lifecycleWorkbench?.tasks || []).length === 0" description="暂无待处理运营任务"/>
      <el-table
        v-else
        :data="lifecycleWorkbench?.tasks || []"
        border
        size="small"
        v-loading="workbenchLoading"
        max-height="360"
      >
        <el-table-column prop="priority" label="优先级" width="90">
          <template #default="scope">
            <el-tag :type="resolvePriorityType(scope.row.priority)">
              {{ resolvePriorityText(scope.row.priority) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tenantName" label="租户" min-width="140"/>
        <el-table-column prop="lifecycleState" label="生命周期" width="120">
          <template #default="scope">
            <el-tag :type="resolveLifecycleType(scope.row.lifecycleState)">
              {{ resolveLifecycleText(scope.row.lifecycleState) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="风险" width="140">
          <template #default="scope">
            <el-tag :type="resolveRiskType(scope.row.riskLevel)">
              {{ resolveRiskText(scope.row.riskLevel) }}({{ scope.row.riskScore }})
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="suggestedAction" label="建议动作" min-width="220"/>
        <el-table-column prop="triggerReason" label="触发原因" min-width="220"/>
        <el-table-column prop="slaHours" label="SLA(h)" width="80"/>
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button type="primary" link @click="handleProcessTask(scope.row)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="card channel-health-card" shadow="never">
      <template #header>
        <div class="workbench-header">
          <span>通知通道健康预检</span>
          <el-button link type="primary" :disabled="notificationHealthLoading" @click="loadNotificationHealth">刷新预检</el-button>
        </div>
      </template>
      <div v-loading="notificationHealthLoading">
        <div class="channel-health-overview">
          <div class="channel-health-item">
            <div class="summary-label">系统通知开关</div>
            <div class="channel-health-value">
              <el-tag :type="resolveNotificationHealthType(notificationHealth?.alertEnabled)">
                {{ notificationHealth?.alertEnabled ? '已启用' : '已关闭' }}
              </el-tag>
            </div>
          </div>
          <div class="channel-health-item">
            <div class="summary-label">已配置通道</div>
            <div class="summary-value">{{ notificationHealth?.configuredChannels?.length ?? 0 }}</div>
          </div>
          <div class="channel-health-item">
            <div class="summary-label">使用默认通道租户</div>
            <div class="summary-value">{{ notificationHealth?.tenantUsingNotificationChannels ?? 0 }}</div>
          </div>
          <div class="channel-health-item">
            <div class="summary-label">风险租户</div>
            <div class="summary-value danger">{{ notificationHealth?.tenantIssueCount ?? 0 }}</div>
          </div>
        </div>
        <div class="channel-health-tags">
          <div class="channel-health-tag-group">
            <span class="channel-health-tag-label">系统已配置</span>
            <template v-if="notificationHealth?.configuredChannels?.length">
              <el-tag
                v-for="channel in notificationHealth.configuredChannels"
                :key="`configured-${channel}`"
                type="success"
              >
                {{ resolveNotificationChannelLabel(channel) }}
              </el-tag>
            </template>
            <span v-else class="toolbar-tip">暂无</span>
          </div>
          <div class="channel-health-tag-group">
            <span class="channel-health-tag-label">系统未配置</span>
            <template v-if="notificationHealth?.unconfiguredChannels?.length">
              <el-tag
                v-for="channel in notificationHealth.unconfiguredChannels"
                :key="`unconfigured-${channel}`"
                type="danger"
              >
                {{ resolveNotificationChannelLabel(channel) }}
              </el-tag>
            </template>
            <span v-else class="toolbar-tip">无缺失</span>
          </div>
        </div>
        <div class="channel-health-summary">
          {{ notificationHealth?.summary || '暂未获取到通知通道健康信息。' }}
        </div>
        <el-empty
          v-if="!notificationHealth?.issues?.length"
          description="当前没有默认通知通道风险租户"
        />
        <el-table
          v-else
          :data="notificationHealth.issues || []"
          border
          size="small"
          max-height="320"
        >
          <el-table-column prop="tenantName" label="租户" min-width="160">
            <template #default="scope">
              <div>{{ scope.row.tenantName || '未命名租户' }}</div>
              <div class="toolbar-tip">{{ scope.row.tenantId || '--' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="已选通道" min-width="180">
            <template #default="scope">
              <div class="channel-list">
                <el-tag
                  v-for="channel in scope.row.selectedChannels || []"
                  :key="`${scope.row.tenantId}-${channel}`"
                  size="small"
                  effect="plain"
                >
                  {{ resolveNotificationChannelLabel(channel) }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="不可用通道" min-width="180">
            <template #default="scope">
              <div class="channel-list">
                <el-tag
                  v-for="channel in scope.row.unavailableChannels || []"
                  :key="`${scope.row.tenantId}-missing-${channel}`"
                  size="small"
                  type="danger"
                >
                  {{ resolveNotificationChannelLabel(channel) }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="风险说明" min-width="240" show-overflow-tooltip/>
          <el-table-column prop="suggestion" label="建议动作" min-width="260" show-overflow-tooltip/>
        </el-table>
      </div>
    </el-card>

    <el-card class="card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-actions">
          <el-button type="primary" :disabled="!isSysAdmin" @click="handleCreate">新增租户</el-button>
          <el-button @click="refreshTenantData">刷新</el-button>
        </div>
        <div class="toolbar-tip" v-if="!isSysAdmin">仅系统管理员可管理租户。</div>
      </div>

      <el-table :data="tenants" border v-loading="loading">
        <el-table-column prop="tenantId" label="租户ID" min-width="140"/>
        <el-table-column prop="tenantName" label="租户名称" min-width="160"/>
        <el-table-column prop="planName" label="套餐" min-width="120"/>
        <el-table-column prop="expireTime" label="到期时间" min-width="160"/>
        <el-table-column prop="daysToExpire" label="剩余天数" width="120">
          <template #default="scope">
            <span>{{ resolveDaysToExpireText(scope.row.daysToExpire) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="lifecycleState" label="生命周期" min-width="120">
          <template #default="scope">
            <el-tag :type="resolveLifecycleType(scope.row.lifecycleState)">
              {{ resolveLifecycleText(scope.row.lifecycleState) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskScore" label="风险分" width="90">
          <template #default="scope">
            <span class="risk-score">{{ scope.row.riskScore ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="流失风险" min-width="120">
          <template #default="scope">
            <el-tooltip :content="resolveRiskReasonText(scope.row.riskReasons)" placement="top">
              <el-tag :type="resolveRiskType(scope.row.riskLevel)">
                {{ resolveRiskText(scope.row.riskLevel) }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="contactName" label="联系人" min-width="120"/>
        <el-table-column label="默认通知通道" min-width="180" show-overflow-tooltip>
          <template #default="scope">
            <template v-if="scope.row.notificationChannels?.length">
              <div class="channel-list">
                <el-tag
                  v-for="channel in scope.row.notificationChannels"
                  :key="`${scope.row.tenantId}-${channel}`"
                  size="small"
                  effect="plain"
                >
                  {{ resolveNotificationChannelLabel(channel) }}
                </el-tag>
              </div>
              <div v-if="resolveTenantNotificationIssue(scope.row)" class="channel-risk-tip">
                缺少 {{ resolveTenantNotificationIssue(scope.row)?.unavailableChannels?.map(resolveNotificationChannelLabel).join(' / ') }}
              </div>
            </template>
            <span v-else>未配置</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="scope">
            <el-tag :type="resolveStatusType(scope.row.status)">
              {{ resolveStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180"/>
        <el-table-column label="操作" width="500">
          <template #default="scope">
            <div class="row-actions">
              <el-button size="small" :disabled="!isSysAdmin" @click="handleEdit(scope.row)">编辑</el-button>
              <el-button
                size="small"
                type="info"
                :disabled="!isSysAdmin || !scope.row.notificationChannels?.length || notificationTestLoading"
                @click="handleTestTenantNotificationChannels(scope.row)"
              >
                测试通知
              </el-button>
              <el-button size="small" type="warning" :disabled="!isSysAdmin" @click="handleExtend30Days(scope.row)">续期30天</el-button>
              <el-button size="small" type="primary" :disabled="!isSysAdmin" @click="openLifecycleDialog(scope.row)">自定义续期</el-button>
              <el-button
                v-if="shouldShowReactivate(scope.row)"
                size="small"
                type="success"
                :disabled="!isSysAdmin"
                @click="handleReactivate(scope.row)"
              >
                恢复服务
              </el-button>
              <el-button size="small" type="danger" :disabled="!isSysAdmin" @click="handleDelete(scope.row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑租户' : '新增租户'" width="640px" :close-on-click-modal="false">
      <el-form label-width="120px">
        <el-form-item label="租户ID">
          <el-tag v-if="editingId" type="info">{{ form.tenantId || '未生成' }}</el-tag>
          <el-tag v-else type="info">系统自动生成</el-tag>
        </el-form-item>
        <el-form-item label="租户名称" required>
          <el-input v-model="form.tenantName" placeholder="请输入租户名称"/>
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactName" placeholder="联系人姓名"/>
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" placeholder="联系电话"/>
        </el-form-item>
        <el-form-item label="联系邮箱">
          <el-input v-model="form.contactEmail" placeholder="邮箱"/>
        </el-form-item>
        <el-form-item label="默认通知通道">
          <el-select
            v-model="form.notificationChannels"
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="留空则不设置租户默认通道"
            style="width: 100%"
          >
            <el-option
              v-for="item in notificationChannelOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <div class="form-inline-actions">
            <div class="form-tip">数字员工未单独选择推送通道时，会优先继承这里的默认通道。</div>
            <el-button
              link
              type="primary"
              :disabled="notificationTestLoading || !form.notificationChannels.length"
              @click="handleTestFormNotificationChannels"
            >
              测试当前通道
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="到期时间">
          <el-date-picker
            v-model="form.expireTime"
            type="datetime"
            placeholder="请选择到期时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="套餐">
          <el-select v-model="form.planName" placeholder="请选择套餐" filterable clearable style="width: 100%">
            <el-option v-for="plan in planOptions" :key="plan" :label="plan" :value="plan"/>
          </el-select>
          <div class="form-tip">套餐来源于“套餐管理”页现有租户配置。</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option :value="1" label="启用"/>
            <el-option :value="0" label="停用"/>
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="备注信息"/>
        </el-form-item>
        <template v-if="!editingId">
          <el-divider content-position="left">初始化配置</el-divider>
          <el-form-item label="组织模板">
            <el-select v-model="form.orgTemplateCode" clearable filterable style="width: 100%" placeholder="可选，创建租户后自动初始化组织树">
              <el-option
                v-for="template in orgTemplateList"
                :key="template.templateCode"
                :label="`${template.templateName}（${template.industry || '通用'}）`"
                :value="template.templateCode"
              />
            </el-select>
            <div class="form-tip">建议在开户时直接选择模板，减少后续再进入“组织架构”页二次初始化。</div>
          </el-form-item>
          <el-alert
            v-if="selectedOrgTemplate"
            type="info"
            :closable="false"
            class="template-alert"
          >
            <template #title>
              {{ selectedOrgTemplate.templateName }}：{{ selectedOrgTemplate.description || '用于初始化组织类型、节点树和关系语义。' }}
            </template>
            <div class="template-alert-meta">
              组织类型 {{ selectedOrgTemplate.typeCount || 0 }} 个，组织节点 {{ selectedOrgTemplate.unitCount || 0 }} 个，组织关系 {{ selectedOrgTemplate.relationCount || 0 }} 条
            </div>
          </el-alert>
          <el-form-item v-if="selectedOrgTemplate" label="根组织名称">
            <el-input
              v-model="form.orgRootUnitName"
              :placeholder="`留空则默认使用租户名称；模板默认值为 ${selectedOrgTemplate.defaultRootUnitName || '根组织'}`"
            />
            <div class="form-tip">用于覆盖模板中的默认主体名称，避免直接落出“集团总部 / 第一学校 / 政务中心”等固定名称。</div>
          </el-form-item>
          <el-form-item label="初始化数据源">
            <el-switch v-model="initConfig"/>
          </el-form-item>
          <template v-if="initConfig">
            <el-form-item label="隔离模式">
              <el-select v-model="form.isolationMode" style="width: 100%">
                <el-option label="物理隔离" value="PHYSICAL"/>
                <el-option label="逻辑隔离" value="LOGICAL"/>
              </el-select>
            </el-form-item>
            <el-form-item label="手动JDBC">
              <el-switch v-model="jdbcManualMode" @change="handleJdbcManualModeChange"/>
              <div class="form-tip">默认按 MySQL 自动拼接地址，不会写 JDBC 时只填主机、端口和数据库名即可。</div>
            </el-form-item>
            <template v-if="jdbcManualMode">
              <el-form-item label="JDBC地址">
                <el-input v-model="form.jdbcUrl" placeholder="jdbc:mysql://127.0.0.1:3306/demo"/>
              </el-form-item>
            </template>
            <template v-else>
              <el-form-item label="数据库主机">
                <el-input v-model="jdbcForm.host" placeholder="127.0.0.1 或 db.example.com"/>
              </el-form-item>
              <el-form-item label="端口">
                <el-input v-model="jdbcForm.port" placeholder="3306"/>
              </el-form-item>
              <el-form-item label="数据库名">
                <el-input v-model="jdbcForm.databaseName" placeholder="demo"/>
              </el-form-item>
              <el-form-item label="连接参数">
                <el-input v-model="jdbcForm.params" placeholder="useSSL=false&allowPublicKeyRetrieval=true">
                  <template #prepend>?</template>
                </el-input>
                <div class="form-tip">可选项，常见场景保持默认即可。</div>
              </el-form-item>
              <el-form-item label="JDBC预览">
                <el-input :model-value="jdbcPreview" readonly placeholder="根据上面的字段自动生成"/>
              </el-form-item>
            </template>
            <el-form-item label="账号">
              <el-input v-model="form.username" placeholder="数据库账号"/>
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="form.password" type="password" placeholder="数据库密码" show-password autocomplete="new-password"/>
            </el-form-item>
            <el-form-item label="驱动类">
              <el-input v-model="form.driverClass" placeholder="com.mysql.cj.jdbc.Driver"/>
            </el-form-item>
            <el-form-item label="逻辑字段">
              <el-input v-model="form.logicColumn" placeholder="tenant_id"/>
            </el-form-item>
          </template>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="lifecycleDialogVisible" title="租户续期" width="520px" :close-on-click-modal="false">
      <el-form label-width="120px">
        <el-form-item label="租户">
          <span>{{ lifecycleTargetTenant?.tenantName || '--' }}（{{ lifecycleTargetTenant?.tenantId || '--' }}）</span>
        </el-form-item>
        <el-form-item label="续期方式">
          <el-radio-group v-model="lifecycleForm.mode">
            <el-radio-button label="days">按天数续期</el-radio-button>
            <el-radio-button label="date">指定到期时间</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="lifecycleForm.mode === 'days'" label="续期天数">
          <el-input-number v-model="lifecycleForm.extendDays" :min="1" :max="3650" :step="1"/>
          <div class="form-tip">默认 30 天，可按合同手动调整。</div>
        </el-form-item>
        <el-form-item v-else label="目标到期时间">
          <el-date-picker
            v-model="lifecycleForm.expireTime"
            type="datetime"
            placeholder="请选择目标到期时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="恢复服务">
          <el-switch v-model="lifecycleForm.activate"/>
          <div class="form-tip">关闭后仅调整到期时间，不变更当前启停状态。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeLifecycleDialog">取消</el-button>
        <el-button type="primary" @click="handleCustomExtend">确认续期</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="notificationTestDialogVisible" title="通知通道测试结果" width="720px" :close-on-click-modal="false">
      <template v-if="notificationTestResult">
        <div class="notification-test-summary">
          <div class="notification-test-summary-item">
            <span class="summary-item-label">测试来源</span>
            <span class="summary-item-value">{{ notificationTestContext || '--' }}</span>
          </div>
          <div class="notification-test-summary-item">
            <span class="summary-item-label">租户</span>
            <span class="summary-item-value">
              {{ notificationTestResult.tenantName || '未命名租户' }}（{{ notificationTestResult.tenantId || '未指定租户ID' }}）
            </span>
          </div>
          <div class="notification-test-summary-item">
            <span class="summary-item-label">测试时间</span>
            <span class="summary-item-value">{{ formatDateTime(notificationTestResult.testTime) }}</span>
          </div>
        </div>
        <div class="notification-test-result-bar">
          <el-tag :type="resolveNotificationTestType(notificationTestResult.success)">
            {{ notificationTestResult.success ? '整体成功' : '存在失败' }}
          </el-tag>
          <span class="notification-test-result-text">{{ notificationTestResult.summary || '-' }}</span>
        </div>
        <el-table :data="notificationTestResult.channelResults || []" border size="small">
          <el-table-column label="通道" min-width="140">
            <template #default="scope">
              {{ resolveNotificationChannelLabel(scope.row.channel) }}
            </template>
          </el-table-column>
          <el-table-column label="结果" width="110">
            <template #default="scope">
              <el-tag :type="resolveNotificationTestType(scope.row.success)">
                {{ scope.row.success ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="HTTP 状态" width="120">
            <template #default="scope">{{ scope.row.statusCode ?? '--' }}</template>
          </el-table-column>
          <el-table-column label="诊断明细" min-width="260" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.detail || '-' }}</template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button type="primary" @click="notificationTestDialogVisible = false">关闭</el-button>
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

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-card {
  border-radius: 10px;
}

.summary-label {
  color: #64748b;
  font-size: 13px;
}

.summary-value {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
}

.summary-value.success {
  color: #059669;
}

.summary-value.warning {
  color: #d97706;
}

.summary-value.danger {
  color: #dc2626;
}

.card {
  border-radius: 12px;
}

.workbench-card {
  margin-bottom: 16px;
}

.channel-health-card {
  margin-bottom: 16px;
}

.workbench-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.workbench-time {
  font-size: 12px;
  color: #64748b;
  font-weight: 400;
}

.workbench-metrics {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.workbench-total {
  color: #475569;
  font-size: 13px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.channel-health-overview {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.channel-health-item {
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.channel-health-value {
  margin-top: 8px;
}

.channel-health-tags {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 12px;
}

.channel-health-tag-group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.channel-health-tag-label {
  min-width: 88px;
  color: #475569;
  font-size: 13px;
}

.channel-health-summary {
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  color: #334155;
  line-height: 1.7;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.toolbar-tip {
  color: #94a3b8;
  font-size: 13px;
}

.form-tip {
  font-size: 12px;
  color: #64748b;
  margin-top: 6px;
}

.form-inline-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.template-alert {
  margin-bottom: 16px;
}

.template-alert-meta {
  margin-top: 6px;
  font-size: 12px;
  color: #475569;
}

.risk-score {
  font-weight: 600;
  color: #b45309;
}

.channel-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.channel-risk-tip {
  margin-top: 6px;
  color: #dc2626;
  font-size: 12px;
}

.notification-test-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.notification-test-summary-item {
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
}

.summary-item-label {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.summary-item-value {
  color: #0f172a;
  font-weight: 600;
  word-break: break-word;
}

.notification-test-result-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  margin-bottom: 16px;
}

.notification-test-result-text {
  color: #334155;
  line-height: 1.6;
}

@media (max-width: 1200px) {
  .summary-grid {
    grid-template-columns: repeat(3, minmax(120px, 1fr));
  }
}

@media (max-width: 768px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }

  .channel-health-tag-group,
  .form-inline-actions,
  .notification-test-result-bar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
