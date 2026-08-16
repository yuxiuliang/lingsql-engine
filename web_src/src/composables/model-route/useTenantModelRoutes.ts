import {computed, readonly, ref, shallowRef} from 'vue';
import {fetchModelHealth, type ModelHealthView} from '@/api/admin';
import {
  fetchTenantModelRoutes,
  replaceTenantModelRoutes,
  type TenantModelRouteCommand,
  type TenantModelRouteView
} from '@/api/model-route';
import {fetchTenants, type TenantView} from '@/api/tenant';

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

/**
 * 管理租户模型授权路由的状态和副作用。
 *
 * @return 租户模型授权状态与操作
 * @author 余炘洋
 * @date 2026-08-16
 */
export const useTenantModelRoutes = () => {
  const tenants = ref<TenantView[]>([]);
  const models = ref<ModelHealthView[]>([]);
  const routes = ref<TenantModelRouteView[]>([]);
  const selectedTenantId = shallowRef('');
  const loading = shallowRef(false);
  const saving = shallowRef(false);

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
      const firstTenant = tenantItems.find((tenant) => tenant.status === 1) ?? tenantItems[0];
      selectedTenantId.value = firstTenant?.tenantId ?? '';
      routes.value = selectedTenantId.value
        ? await fetchTenantModelRoutes(selectedTenantId.value)
        : [];
    } finally {
      loading.value = false;
    }
  };

  const selectTenant = async (tenantId: string) => {
    selectedTenantId.value = tenantId;
    await refreshRoutes();
  };

  const refresh = async () => {
    loading.value = true;
    try {
      const [tenantItems, modelItems] = await Promise.all([fetchTenants(), fetchModelHealth()]);
      tenants.value = tenantItems;
      models.value = modelItems;
      if (selectedTenantId.value) {
        routes.value = await fetchTenantModelRoutes(selectedTenantId.value);
      }
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
      routes.value = await fetchTenantModelRoutes(selectedTenantId.value);
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

  return {
    tenantOptions,
    rows,
    selectedTenantId: readonly(selectedTenantId),
    loading: readonly(loading),
    saving: readonly(saving),
    selectedCount,
    eligibleCount,
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
