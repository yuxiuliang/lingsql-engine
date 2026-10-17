<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {useAuthStore} from '@/store/auth';
import {fetchMenus, fetchProfile, logout as logoutApi} from '@/api/auth';
import {fetchTenants, TenantView} from '@/api/tenant';

/**
 * 菜单项定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
interface MenuNode {
  menuCode?: string;
  menuName?: string;
  path?: string;
  icon?: string;
  sort?: number;
  children?: MenuNode[];
}

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const isCollapsed = ref(false);
const isMobile = ref(false);
const mobileMenuVisible = ref(false);
const tenantOptions = ref<TenantView[]>([]);
const tenantLoading = ref(false);

const ICON_PATHS: Record<string, string> = {
  chat: 'M4 5h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H9l-5 4v-4H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z',
  tenant: 'M4 10l8-6 8 6v9a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-9z',
  metadata: 'M4 4h16v4H4V4zm0 6h16v10H4V10zm4 3h8v2H8v-2z',
  lexicon: 'M5 4h12a2 2 0 0 1 2 2v12l-4-2-4 2-4-2-4 2V6a2 2 0 0 1 2-2z',
  join: 'M7 7a4 4 0 0 1 4-4h2v2h-2a2 2 0 0 0-2 2v2H7V7zm8 0a2 2 0 0 0-2-2h-2V3h2a4 4 0 0 1 4 4v2h-2V7zM7 15h2v2a2 2 0 0 0 2 2h2v2h-2a4 4 0 0 1-4-4v-2zm10 0v2a4 4 0 0 1-4 4h-2v-2h2a2 2 0 0 0 2-2v-2h2z',
  model: 'M12 2a9 9 0 1 1-9 9 9 9 0 0 1 9-9zm0 5a4 4 0 1 0 4 4 4 4 0 0 0-4-4z',
  user: 'M12 12a4 4 0 1 0-4-4 4 4 0 0 0 4 4zm0 2c-4.4 0-8 2-8 4v2h16v-2c0-2-3.6-4-8-4z',
  role: 'M12 2l7 4v6c0 5-3.5 9-7 10-3.5-1-7-5-7-10V6l7-4z',
  permission: 'M7 14a5 5 0 1 1 4.6 3H9v3H7v-6zm4-7a3 3 0 1 0 3 3 3 3 0 0 0-3-3z',
  audit: 'M6 2h9l5 5v15H6V2zm8 1.5V7h3.5L14 3.5zM8 10h8v2H8v-2zm0 4h8v2H8v-2z',
  approval: 'M4 4h16v16H4V4zm3.2 8.8l2.6 2.6 5.6-5.6-1.4-1.4-4.2 4.2-1.2-1.2-1.4 1.4z',
  sql: 'M12 2c-4.4 0-8 1.3-8 3v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5c0-1.7-3.6-3-8-3zm0 2c3.9 0 6 .9 6 1s-2.1 1-6 1-6-.9-6-1 2.1-1 6-1zm0 4c3.9 0 6-.9 6-1v3c0 1.1-2.1 2-6 2s-6-.9-6-2V7c0 .1 2.1 1 6 1zm0 5c3.9 0 6-.9 6-1v3c0 1.1-2.1 2-6 2s-6-.9-6-2v-3c0 .1 2.1 1 6 1zm0 5c3.9 0 6-.9 6-1v2c0 1.1-2.1 2-6 2s-6-.9-6-2v-2c0 .1 2.1 1 6 1z',
  dashboard: 'M4 13h4v7H4v-7zm6-6h4v13h-4V7zm6 3h4v10h-4V10z',
  billing: 'M6 2h12a2 2 0 0 1 2 2v18l-4-2-4 2-4-2-4 2V4a2 2 0 0 1 2-2zm2 5h8v2H8V7zm0 4h8v2H8v-2z',
  plan: 'M4 4h16v4H4V4zm0 6h16v4H4v-4zm0 6h16v4H4v-4z',
  robot: 'M9 2h6v2h2a3 3 0 0 1 3 3v6a3 3 0 0 1-3 3h-1v2h2v2H6v-2h2v-2H7a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3h2V2zm-2 4a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1V7a1 1 0 0 0-1-1H7zm2 2h2v2H9V8zm4 0h2v2h-2V8zm-4 5h6v2H9v-2z',
  default: 'M12 2a9 9 0 1 1-9 9 9 9 0 0 1 9-9zm0 5a4 4 0 1 0 4 4 4 4 0 0 0-4-4z'
};

/**
 * 判断是否系统管理员。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const isSysAdmin = computed(() => authStore.isSysAdmin);

/**
 * 当前租户名称。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const currentTenantName = computed(() => {
  if (isSysAdmin.value) {
    return authStore.activeTenantName || '';
  }
  return authStore.tenantName || authStore.tenantId || '';
});

/**
 * 选中的租户ID。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const activeTenantId = computed({
  get: () => authStore.activeTenantId,
  set: (value: string) => {
    const selected = tenantOptions.value.find((item) => item.tenantId === value);
    authStore.setActiveTenant(value, selected?.tenantName || '');
  }
});

/**
 * 解析侧边栏头像显示文本。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const avatarText = computed(() => {
  const username = authStore.nickname?.trim() || authStore.username?.trim();
  if (username) {
    return Array.from(username).slice(0, 2).join('');
  }
  return '用户';
});

const sortMenus = (menus: MenuNode[]): MenuNode[] => {
  return [...menus]
    .sort((a, b) => (a?.sort || 0) - (b?.sort || 0))
    .map((menu) => ({
      ...menu,
      children: menu.children ? sortMenus(menu.children) : []
    }));
};

const menuTree = computed<MenuNode[]>(() => sortMenus(authStore.menus || []));

const ensureSession = async (): Promise<boolean> => {
  if (authStore.sessionReady) {
    return authStore.isAuthenticated;
  }
  try {
    const profile = await fetchProfile();
    authStore.applyProfile(profile);
    authStore.sessionReady = true;
    return authStore.isAuthenticated;
  } catch {
    authStore.logout();
    await router.push('/login');
    return false;
  }
};

/**
 * 加载菜单数据。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const loadMenus = async () => {
  const authenticated = await ensureSession();
  if (!authenticated) {
    return;
  }
  if (authStore.menus && authStore.menus.length > 0) {
    return;
  }
  try {
    const menus = await fetchMenus();
    authStore.setMenus(menus);
  } catch {
    authStore.logout();
    await router.push('/login');
  }
};

/**
 * 加载租户列表。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
const loadTenants = async () => {
  const authenticated = await ensureSession();
  if (!authenticated || !isSysAdmin.value) {
    return;
  }
  tenantLoading.value = true;
  try {
    tenantOptions.value = await fetchTenants();
    if (authStore.activeTenantId) {
      const selected = tenantOptions.value.find((item) => item.tenantId === authStore.activeTenantId);
      if (selected) {
        authStore.setActiveTenant(selected.tenantId, selected.tenantName || '');
      }
    }
  } catch {
    tenantOptions.value = [];
  } finally {
    tenantLoading.value = false;
  }
};

/**
 * 解析图标类型。
 *
 * @param icon 原始图标
 * @return 图标类型
 * @author 余炘洋
 * @date 2026-01-23
 */
const resolveIconType = (icon?: string): string => {
  if (!icon) {
    return 'default';
  }
  const normalized = icon.trim().toLowerCase();
  if (['chat', 'tenant', 'metadata', 'lexicon', 'join', 'model', 'user', 'role', 'permission', 'audit', 'sql', 'dashboard', 'billing', 'plan', 'robot'].includes(normalized)) {
    return normalized;
  }
  if (['history'].includes(normalized)) {
    return 'chat';
  }
  if (['database', 'officebuilding'].includes(normalized)) {
    return 'tenant';
  }
  if (['member', 'account'].includes(normalized)) {
    return 'user';
  }
  if (['auth', 'rbac'].includes(normalized)) {
    return 'role';
  }
  if (['perm'].includes(normalized)) {
    return 'permission';
  }
  if (['log', 'logs'].includes(normalized)) {
    return 'audit';
  }
  if (['sql-log', 'sqls'].includes(normalized)) {
    return 'sql';
  }
  if (['overview', 'console'].includes(normalized)) {
    return 'dashboard';
  }
  if (['bill', 'pricing', 'charge'].includes(normalized)) {
    return 'billing';
  }
  if (['package', 'tier'].includes(normalized)) {
    return 'plan';
  }
  if (['robot', 'bot', 'employee'].includes(normalized)) {
    return 'robot';
  }
  if (['table', 'notebook'].includes(normalized)) {
    return 'metadata';
  }
  if (['dictionary', 'collection'].includes(normalized)) {
    return 'lexicon';
  }
  if (['link', 'share'].includes(normalized)) {
    return 'join';
  }
  if (['setting', 'cpu'].includes(normalized)) {
    return 'model';
  }
  return 'default';
};

/**
 * 解析图标路径。
 *
 * @param icon 原始图标
 * @return SVG 路径
 * @author 余炘洋
 * @date 2026-02-28
 */
const resolveIconPath = (icon?: string): string => {
  const iconType = resolveIconType(icon);
  return ICON_PATHS[iconType] || ICON_PATHS.default;
};

/**
 * 执行退出登录操作。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleLogout = () => {
  logoutApi().catch(() => null).finally(() => {
    authStore.logout();
    router.push('/login');
  });
};

/**
 * 切换侧边栏折叠状态。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value;
};

/**
 * 同步移动端视口状态。
 *
 * @author 余炘洋
 * @date 2026-02-28
 */
const syncViewport = () => {
  isMobile.value = window.innerWidth <= 980;
  if (!isMobile.value) {
    mobileMenuVisible.value = false;
  }
};

/**
 * 切换移动端导航显隐。
 *
 * @author 余炘洋
 * @date 2026-02-28
 */
const toggleMobileMenu = () => {
  mobileMenuVisible.value = !mobileMenuVisible.value;
};

/**
 * 菜单选择后收起移动端导航。
 *
 * @author 余炘洋
 * @date 2026-02-28
 */
const handleMenuSelect = () => {
  if (isMobile.value) {
    mobileMenuVisible.value = false;
  }
};

watch(
  () => route.path,
  () => {
    if (isMobile.value) {
      mobileMenuVisible.value = false;
    }
  }
);

onMounted(loadMenus);
onMounted(() => {
  if (!isSysAdmin.value && authStore.tenantId && !authStore.activeTenantId) {
    authStore.setActiveTenant(authStore.tenantId, authStore.tenantName || authStore.tenantId);
  }
});
onMounted(loadTenants);
onMounted(() => {
  syncViewport();
  window.addEventListener('resize', syncViewport);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport);
});
</script>

<template>
  <el-container class="layout">
    <div
      v-if="isMobile && mobileMenuVisible"
      class="mobile-backdrop"
      role="button"
      tabindex="0"
      aria-label="关闭导航菜单"
      @click="mobileMenuVisible = false"
      @keydown.enter.prevent="mobileMenuVisible = false"
      @keydown.space.prevent="mobileMenuVisible = false"
    ></div>

    <el-aside
      :width="isCollapsed && !isMobile ? '76px' : '240px'"
      class="layout-aside"
      :class="{collapsed: isCollapsed && !isMobile, 'mobile-visible': mobileMenuVisible}"
    >
      <div class="logo" :class="{collapsed: isCollapsed && !isMobile}">
        <div class="logo-icon">语</div>
        <div v-if="!isCollapsed || isMobile" class="logo-text">
          <div class="logo-title">语数</div>
          <div class="logo-sub">lingsql-engine</div>
        </div>
        <button
          v-if="!isMobile"
          class="collapse-button"
          :title="isCollapsed ? '展开' : '收起'"
          :aria-label="isCollapsed ? '展开侧边栏' : '收起侧边栏'"
          @click="toggleCollapse"
        >
          <span class="collapse-line"></span>
          <span class="collapse-line"></span>
          <span class="collapse-line"></span>
        </button>
      </div>
      <el-menu
        router
        class="menu"
        :default-active="$route.path"
        background-color="#1e293b"
        text-color="#e2e8f0"
        active-text-color="#ffffff"
        :collapse="isCollapsed && !isMobile"
        :collapse-transition="false"
        @select="handleMenuSelect"
      >
        <template v-for="menu in menuTree" :key="menu.menuCode || menu.path">
          <el-sub-menu v-if="menu.children && menu.children.length" :index="menu.path || menu.menuCode || ''">
            <template #title>
              <el-icon class="menu-icon">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path :d="resolveIconPath(menu.icon)"/>
                </svg>
              </el-icon>
              <span class="menu-label">{{ menu.menuName }}</span>
            </template>
            <el-menu-item
              v-for="child in menu.children"
              :key="child.menuCode || child.path"
              :index="child.path || ''"
              :title="child.menuName"
            >
              <el-icon class="menu-icon">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path :d="resolveIconPath(child.icon)"/>
                </svg>
              </el-icon>
              <span class="menu-label">{{ child.menuName }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="menu.path || ''" :title="menu.menuName">
            <el-icon class="menu-icon">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path :d="resolveIconPath(menu.icon)"/>
              </svg>
            </el-icon>
            <span class="menu-label">{{ menu.menuName }}</span>
          </el-menu-item>
        </template>
      </el-menu>
      <div class="sidebar-footer" :class="{collapsed: isCollapsed && !isMobile}">
        <div class="avatar-circle">{{ avatarText }}</div>
        <div v-if="!isCollapsed || isMobile" class="user-info">
          <div class="user-name">{{ authStore.nickname || authStore.username || '未命名用户' }}</div>
          <div class="user-role">租户：{{ currentTenantName || '未设置' }}</div>
        </div>
      </div>
    </el-aside>

    <el-container class="layout-content">
      <el-header class="layout-header">
        <div class="header-left">
          <button
            v-if="isMobile"
            class="mobile-menu-button"
            aria-label="打开导航菜单"
            title="打开导航菜单"
            @click="toggleMobileMenu"
          >
            <span></span>
            <span></span>
            <span></span>
          </button>
          <span class="header-title">管理控制台</span>
        </div>
        <div class="header-right">
          <template v-if="isSysAdmin">
            <el-select
              v-model="activeTenantId"
              class="tenant-selector"
              filterable
              clearable
              placeholder="请选择租户"
              :loading="tenantLoading"
            >
              <el-option
                v-for="tenant in tenantOptions"
                :key="tenant.id"
                :label="`${tenant.tenantName} (${tenant.tenantId})`"
                :value="tenant.tenantId"
              />
            </el-select>
          </template>
          <template v-else>
            <el-tag class="tenant-tag" type="info" effect="light">
              当前租户：{{ currentTenantName || '未设置' }}
            </el-tag>
          </template>
          <span class="user-name">{{ authStore.nickname || authStore.username || '未命名用户' }}</span>
          <el-button size="small" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="layout-main">
        <router-view/>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  position: relative;
  min-height: 100vh;
  min-height: 100dvh;
  background: transparent;
}

.layout-content {
  min-width: 0;
}

.layout-aside {
  background: linear-gradient(180deg, #1e293b 0%, #334155 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease;
  z-index: 1300;
}

.logo {
  padding: 20px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
}

.logo.collapsed {
  justify-content: center;
}

.logo-icon {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: linear-gradient(140deg, #3b82f6 0%, #0ea5e9 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #fff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.24);
}

.logo-text {
  display: flex;
  flex-direction: column;
}

.logo-title {
  font-size: 20px;
  font-weight: 600;
}

.logo-sub {
  font-size: 12px;
  opacity: 0.72;
  margin-top: 4px;
}

.collapse-button {
  position: absolute;
  right: 14px;
  top: 16px;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.collapse-button:hover {
  background: rgba(255, 255, 255, 0.16);
  transform: translateY(-1px);
}

.collapse-line {
  width: 16px;
  height: 2px;
  background: #e2e8f0;
  border-radius: 2px;
}

.menu {
  border-right: none;
  background: transparent;
  color: #e2e8f0;
  padding: 16px 0 8px;
  flex: 1;
  overflow-y: auto;
}

.menu-icon {
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-right: 10px;
}

.menu-icon svg {
  width: 18px;
  height: 18px;
  fill: currentColor;
}

.menu :deep(.el-menu-item),
.menu :deep(.el-sub-menu__title) {
  height: 44px;
  line-height: 44px;
  margin: 4px 12px;
  border-radius: 12px;
  transition: all 0.2s ease;
  background: transparent;
}

.menu :deep(.el-menu-item.is-active) {
  background: rgba(37, 99, 235, 0.22);
  box-shadow: 0 6px 14px rgba(15, 23, 42, 0.18);
}

.menu :deep(.el-menu-item:hover),
.menu :deep(.el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, 0.1);
}

.layout-aside.collapsed .menu :deep(.el-menu-item),
.layout-aside.collapsed .menu :deep(.el-sub-menu__title) {
  margin: 4px 10px;
  justify-content: center;
}

.layout-aside.collapsed .menu-icon {
  margin-right: 0;
  display: inline-flex !important;
}

.layout-aside.collapsed .menu-label {
  display: none;
}

.menu :deep(.el-menu--collapse .el-menu-item .menu-icon),
.menu :deep(.el-menu--collapse .el-sub-menu__title .menu-icon) {
  display: inline-flex !important;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: center;
  gap: 12px;
}

.sidebar-footer.collapsed {
  justify-content: center;
}

.avatar-circle {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 14px;
}

.user-info {
  display: flex;
  flex-direction: column;
}

.user-role {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.72);
  margin-top: 4px;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid #e2e8f0;
  padding: 0 20px;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.06);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

.mobile-menu-button {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  border: 1px solid #cbd5e1;
  background: #ffffff;
  display: inline-flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  cursor: pointer;
}

.mobile-menu-button span {
  width: 14px;
  height: 2px;
  background: #1e293b;
  border-radius: 2px;
  margin: 0 auto;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.layout-header .user-name {
  font-size: 13px;
  color: #334155;
}

.sidebar-footer .user-name {
  font-size: 14px;
  color: #f9fafb;
}

.tenant-tag {
  border: none;
  color: #334155;
  background: #e2e8f0;
}

.tenant-selector {
  width: 220px;
}

.layout-main {
  padding: 16px;
  min-height: 0;
  overflow: auto;
}

.mobile-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(2, 6, 23, 0.45);
  z-index: 1200;
}

@media (max-width: 1200px) {
  .layout-header .user-name {
    display: none;
  }
}

@media (max-width: 980px) {
  .layout {
    overflow: hidden;
  }

  .layout-aside {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    width: 240px !important;
    transform: translateX(-105%);
    transition: transform 0.2s ease;
    box-shadow: 0 14px 30px rgba(2, 6, 23, 0.4);
  }

  .layout-aside.mobile-visible {
    transform: translateX(0);
  }

  .layout-header {
    padding: 0 12px;
  }

  .header-right {
    gap: 6px;
  }

  .tenant-selector {
    width: 150px;
  }

  .layout-main {
    padding: 10px;
  }
}

@media (max-width: 640px) {
  .tenant-selector,
  .tenant-tag {
    display: none;
  }
}
</style>
