<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import {useRouter} from 'vue-router';
import {useAuthStore} from '@/store/auth';
import {fetchMenus, logout as logoutApi} from '@/api/auth';

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
const authStore = useAuthStore();
const isCollapsed = ref(false);

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

/**
 * 加载菜单数据。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const loadMenus = async () => {
  if (!authStore.token) {
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
  if (['chat', 'tenant', 'metadata', 'lexicon', 'join', 'model'].includes(normalized)) {
    return normalized;
  }
  return 'default';
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

onMounted(loadMenus);
</script>

<template>
  <el-container class="layout">
    <el-aside :width="isCollapsed ? '76px' : '240px'" class="layout-aside" :class="{collapsed: isCollapsed}">
      <div class="logo" :class="{collapsed: isCollapsed}">
        <div class="logo-icon">语</div>
        <div v-if="!isCollapsed" class="logo-text">
          <div class="logo-title">语数</div>
          <div class="logo-sub">lingsql-engine</div>
        </div>
        <button class="collapse-button" @click="toggleCollapse" :title="isCollapsed ? '展开' : '收起'">
          <span class="collapse-line"></span>
          <span class="collapse-line"></span>
          <span class="collapse-line"></span>
        </button>
      </div>
      <el-menu
        router
        class="menu"
        :default-active="$route.path"
        background-color="#334155"
        text-color="#e2e8f0"
        active-text-color="#ffffff"
        :collapse="isCollapsed"
        :collapse-transition="false"
      >
        <template v-for="menu in menuTree" :key="menu.menuCode || menu.path">
          <el-sub-menu v-if="menu.children && menu.children.length" :index="menu.path || menu.menuCode || ''">
            <template #title>
              <el-icon class="menu-icon">
                <svg v-if="resolveIconType(menu.icon) === 'chat'" viewBox="0 0 24 24" aria-hidden="true">
                  <path
                    d="M4 5h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H9l-5 4v-4H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z"
                  />
                </svg>
                <svg v-else-if="resolveIconType(menu.icon) === 'tenant'" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M4 10l8-6 8 6v9a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-9z"/>
                </svg>
                <svg v-else-if="resolveIconType(menu.icon) === 'metadata'" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M4 4h16v4H4V4zm0 6h16v10H4V10zm4 3h8v2H8v-2z"/>
                </svg>
                <svg v-else-if="resolveIconType(menu.icon) === 'lexicon'" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M5 4h12a2 2 0 0 1 2 2v12l-4-2-4 2-4-2-4 2V6a2 2 0 0 1 2-2z"/>
                </svg>
                <svg v-else-if="resolveIconType(menu.icon) === 'join'" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M7 7a4 4 0 0 1 4-4h2v2h-2a2 2 0 0 0-2 2v2H7V7zm8 0a2 2 0 0 0-2-2h-2V3h2a4 4 0 0 1 4 4v2h-2V7zM7 15h2v2a2 2 0 0 0 2 2h2v2h-2a4 4 0 0 1-4-4v-2zm10 0v2a4 4 0 0 1-4 4h-2v-2h2a2 2 0 0 0 2-2v-2h2z"/>
                </svg>
                <svg v-else-if="resolveIconType(menu.icon) === 'model'" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M12 2a9 9 0 1 1-9 9 9 9 0 0 1 9-9zm0 5a4 4 0 1 0 4 4 4 4 0 0 0-4-4z"/>
                </svg>
                <svg v-else viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M12 2a9 9 0 1 1-9 9 9 9 0 0 1 9-9zm0 5a4 4 0 1 0 4 4 4 4 0 0 0-4-4z"/>
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
              <span class="menu-label">{{ child.menuName }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="menu.path || ''" :title="menu.menuName">
            <el-icon class="menu-icon">
              <svg v-if="resolveIconType(menu.icon) === 'chat'" viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M4 5h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H9l-5 4v-4H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z"
                />
              </svg>
              <svg v-else-if="resolveIconType(menu.icon) === 'tenant'" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M4 10l8-6 8 6v9a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-9z"/>
              </svg>
              <svg v-else-if="resolveIconType(menu.icon) === 'metadata'" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M4 4h16v4H4V4zm0 6h16v10H4V10zm4 3h8v2H8v-2z"/>
              </svg>
              <svg v-else-if="resolveIconType(menu.icon) === 'lexicon'" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M5 4h12a2 2 0 0 1 2 2v12l-4-2-4 2-4-2-4 2V6a2 2 0 0 1 2-2z"/>
              </svg>
              <svg v-else-if="resolveIconType(menu.icon) === 'join'" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M7 7a4 4 0 0 1 4-4h2v2h-2a2 2 0 0 0-2 2v2H7V7zm8 0a2 2 0 0 0-2-2h-2V3h2a4 4 0 0 1 4 4v2h-2V7zM7 15h2v2a2 2 0 0 0 2 2h2v2h-2a4 4 0 0 1-4-4v-2zm10 0v2a4 4 0 0 1-4 4h-2v-2h2a2 2 0 0 0 2-2v-2h2z"/>
              </svg>
              <svg v-else-if="resolveIconType(menu.icon) === 'model'" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 2a9 9 0 1 1-9 9 9 9 0 0 1 9-9zm0 5a4 4 0 1 0 4 4 4 4 0 0 0-4-4z"/>
              </svg>
              <svg v-else viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 2a9 9 0 1 1-9 9 9 9 0 0 1 9-9zm0 5a4 4 0 1 0 4 4 4 4 0 0 0-4-4z"/>
              </svg>
            </el-icon>
            <span class="menu-label">{{ menu.menuName }}</span>
          </el-menu-item>
        </template>
      </el-menu>
      <div class="sidebar-footer" :class="{collapsed: isCollapsed}">
        <div class="avatar-circle">{{ avatarText }}</div>
        <div v-if="!isCollapsed" class="user-info">
          <div class="user-name">{{ authStore.nickname || authStore.username || '未命名用户' }}</div>
          <div class="user-role">租户：{{ authStore.tenantId || '未设置' }}</div>
        </div>
      </div>
    </el-aside>
    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <span class="header-title">管理控制台</span>
        </div>
        <div class="header-right">
          <span class="user-name">{{ authStore.nickname || authStore.username || '未命名用户' }}</span>
          <el-tag class="tenant-tag" type="info" effect="light">当前租户：{{ authStore.tenantId || '未设置' }}</el-tag>
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
  height: 100vh;
  background: #f8fafc;
}

.layout-aside {
  background: linear-gradient(180deg, #334155 0%, #475569 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease;
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
  background: linear-gradient(135deg, #409EFF 0%, #66B1FF 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #fff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
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
  opacity: 0.7;
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

.menu-group-title {
  padding: 4px 20px 8px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
  letter-spacing: 1px;
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

.menu :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  margin: 4px 12px;
  border-radius: 12px;
  transition: all 0.2s ease;
  background: transparent;
}

.menu :deep(.el-menu-item.is-active) {
  background: rgba(64, 158, 255, 0.18);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08);
}

.layout-aside.collapsed .menu :deep(.el-menu-item) {
  margin: 4px 10px;
  justify-content: center;
}

.layout-aside.collapsed .menu-icon {
  margin-right: 0;
  display: inline-flex !important;
}

.layout-aside.collapsed .menu :deep(.el-menu-item .menu-icon) {
  display: inline-flex !important;
}

.layout-aside.collapsed .menu-label {
  display: none;
}

.menu :deep(.el-menu--collapse .el-menu-item .menu-icon) {
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
  color: rgba(255, 255, 255, 0.7);
  margin-top: 4px;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  padding: 0 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.layout-header .user-name {
  font-size: 13px;
  color: #374151;
}

.sidebar-footer .user-name {
  font-size: 14px;
  color: #f9fafb;
}

.tenant-tag {
  border: none;
  color: #475569;
  background: #f1f5f9;
}

.layout-main {
  padding: 20px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}
</style>
