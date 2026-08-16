import {computed, readonly, ref, shallowRef, toRef, toValue, watch} from 'vue';
import type {MaybeRefOrGetter} from 'vue';
import {fetchModelHealth, type ModelHealthView} from '@/api/admin';
import {
  fetchTenantModelRoutes,
  replaceTenantModelRoutes,
  type TenantModelRouteCommand,
  type TenantModelRouteView
} from '@/api/model-route';
import {fetchTenants, type TenantView} from '@/api/tenant';
import {
  fetchTenantFirstQuestionReadiness,
  type TenantFirstQuestionReadinessView
} from '@/api/tenant-readiness';

export interface TenantModelRouteRow {
  modelConfigId: number;
  providerName: string;
  modelName: string;
  modelVersion: string;
  eligible: boolean;
  authorized: boolean;
  priority: number;
  weight: number;
  statusText: string;
}

export interface TenantModelRouteTenantOption {
  tenantId: string;
  tenantName: string;
  status?: number;
}

export interface UseTenantModelRoutesOptions {
  preferredTenantId?: MaybeRefOrGetter<string>;
}

/**
 * 管理租户模型授权路由的状态和副作用。
 *
 * @return 租户模型授权状态与操作
 * @author 余炘洋
 * @date 2026-08-16
 */
export const useTenantModelRoutes = (options: UseTenantModelRoutesOptions = {}) => {
  const tenants = ref<TenantView[]>([]);
  const models = ref<ModelHealthView[]>([]);
  const routes = ref<TenantModelRouteView[]>([]);
  const readiness = shallowRef<TenantFirstQuestionReadinessView>();
  const selectedTenantId = shallowRef('');
  const loading = shallowRef(false);
  const saving = shallowRef(false);
  const syncError = shallowRef<unknown>();
  const initialized = shallowRef(false);
  const preferredTenantId = toRef(options.preferredTenantId ?? '');
  let tenantFactsRequestVersion = 0;

  const rows = computed<TenantModelRouteRow[]>(() => models.value.map((model) => {
    const route = routes.value.find((item) => item.modelConfigId === model.id);
    const eligible = model.isActive === 1 && model.probeStatus === 'HEALTHY' && !model.fused;
    return {
      modelConfigId: model.id,
      providerName: model.providerName,
      modelName: model.modelName,
      modelVersion: route?.modelVersion ?? model.modelVersion ?? '',
      eligible,
      authorized: route !== undefined,
      priority: route?.priority ?? model.priority ?? 1,
      weight: route?.weight ?? model.weight ?? 100,
      statusText: resolveStatusText(model)
    };
  }));

  const selectedCount = computed(() => routes.value.length);
  const eligibleCount = computed(() => rows.value.filter((row) => row.eligible).length);
  const tenantOptions = computed<TenantModelRouteTenantOption[]>(() => tenants.value.map((tenant) => ({
    tenantId: tenant.tenantId,
    tenantName: tenant.tenantName,
    status: tenant.status
  })));

  const initialize = async () => {
    loading.value = true;
    try {
      const [tenantItems, modelItems] = await Promise.all([fetchTenants(), fetchModelHealth()]);
      tenants.value = tenantItems;
      models.value = modelItems;
      initialized.value = true;
      await applyPreferredTenant(toValue(preferredTenantId), true);
    } finally {
      loading.value = false;
    }
  };

  const selectTenant = async (tenantId: string) => {
    await applyPreferredTenant(tenantId, true);
  };

  const refresh = async () => {
    loading.value = true;
    try {
      const [tenantItems, modelItems] = await Promise.all([fetchTenants(), fetchModelHealth()]);
      tenants.value = tenantItems;
      models.value = modelItems;
      await applyPreferredTenant(toValue(preferredTenantId), true);
    } finally {
      loading.value = false;
    }
  };

  const refreshRoutes = async () => {
    if (!selectedTenantId.value) {
      routes.value = [];
      return;
    }
    loading.value = true;
    try {
      await refreshTenantFacts();
    } finally {
      loading.value = false;
    }
  };

  const setAuthorized = (modelConfigId: number, authorized: boolean) => {
    if (!authorized) {
      routes.value = routes.value.filter((route) => route.modelConfigId !== modelConfigId);
      return;
    }
    const row = rows.value.find((item) => item.modelConfigId === modelConfigId);
    if (!row?.eligible || routes.value.some((route) => route.modelConfigId === modelConfigId)) {
      return;
    }
    routes.value = [...routes.value, toRouteView(selectedTenantId.value, row)];
  };

  const updatePriority = (modelConfigId: number, priority: number) => {
    updateRoute(modelConfigId, {priority});
  };

  const updateWeight = (modelConfigId: number, weight: number) => {
    updateRoute(modelConfigId, {weight});
  };

  const save = async () => {
    if (!selectedTenantId.value) {
      throw new Error('请先选择租户');
    }
    const commands: TenantModelRouteCommand[] = routes.value
      .map((route) => ({
        modelConfigId: route.modelConfigId,
        priority: route.priority,
        weight: route.weight
      }))
      .sort((left, right) => left.priority - right.priority || left.modelConfigId - right.modelConfigId);
    if (commands.length === 0) {
      throw new Error('每个租户至少授权一个已启用且验证健康的模型');
    }
    saving.value = true;
    try {
      routes.value = await replaceTenantModelRoutes(selectedTenantId.value, commands);
      readiness.value = await fetchTenantFirstQuestionReadiness(selectedTenantId.value);
      return routes.value;
    } finally {
      saving.value = false;
    }
  };

  const updateRoute = (
    modelConfigId: number,
    patch: Partial<Pick<TenantModelRouteView, 'priority' | 'weight'>>
  ) => {
    routes.value = routes.value.map((route) => route.modelConfigId === modelConfigId
      ? {...route, ...patch}
      : route);
  };

  const refreshTenantFacts = async () => {
    const tenantId = selectedTenantId.value;
    const requestVersion = ++tenantFactsRequestVersion;
    if (!tenantId) {
      routes.value = [];
      readiness.value = undefined;
      return;
    }
    const [routeItems, readinessView] = await Promise.all([
      fetchTenantModelRoutes(tenantId),
      fetchTenantFirstQuestionReadiness(tenantId)
    ]);
    if (requestVersion !== tenantFactsRequestVersion || selectedTenantId.value !== tenantId) {
      return;
    }
    routes.value = routeItems;
    readiness.value = readinessView;
  };

  const applyPreferredTenant = async (tenantId: string, force = false) => {
    const normalizedTenantId = tenantId?.trim() ?? '';
    const tenant = tenants.value.find((item) => item.tenantId === normalizedTenantId && item.status === 1);
    if (!tenant) {
      tenantFactsRequestVersion += 1;
      selectedTenantId.value = '';
      routes.value = [];
      readiness.value = undefined;
      return;
    }
    if (!force && selectedTenantId.value === tenant.tenantId) {
      return;
    }
    if (selectedTenantId.value !== tenant.tenantId) {
      routes.value = [];
      readiness.value = undefined;
    }
    selectedTenantId.value = tenant.tenantId;
    await refreshTenantFacts();
  };

  watch(preferredTenantId, async (tenantId) => {
    if (!initialized.value) {
      return;
    }
    try {
      syncError.value = undefined;
      await applyPreferredTenant(tenantId);
    } catch (error) {
      routes.value = [];
      readiness.value = undefined;
      syncError.value = error;
    }
  });

  return {
    tenantOptions,
    rows,
    selectedTenantId: readonly(selectedTenantId),
    loading: readonly(loading),
    saving: readonly(saving),
    selectedCount,
    eligibleCount,
    readiness: readonly(readiness),
    syncError: readonly(syncError),
    initialize,
    selectTenant,
    refresh,
    setAuthorized,
    updatePriority,
    updateWeight,
    save
  };
};

const resolveStatusText = (model: ModelHealthView) => {
  if (model.fused) {
    return '已熔断';
  }
  if (model.isActive !== 1) {
    return '未启用';
  }
  if (model.probeStatus !== 'HEALTHY') {
    return model.probeStatus === 'FAILED' ? '验证失败' : '未验证';
  }
  return '可授权';
};

const toRouteView = (tenantId: string, row: TenantModelRouteRow): TenantModelRouteView => ({
  id: 0,
  tenantId,
  modelConfigId: row.modelConfigId,
  providerName: row.providerName,
  modelName: row.modelName,
  modelVersion: row.modelVersion,
  priority: row.priority,
  weight: row.weight,
  status: 1
});
