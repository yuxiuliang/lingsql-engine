<script setup lang="ts">
import {ref} from 'vue';
import {useRouter} from 'vue-router';
import {useAuthStore} from '@/store/auth';

/**
 * 菜单项定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
interface MenuItem {
  path: string;
  title: string;
  icon: 'chat' | 'tenant' | 'metadata' | 'lexicon' | 'join' | 'model';
}

/**
 * 菜单分组定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
interface MenuGroup {
  title: string;
  items: MenuItem[];
}

const router = useRouter();
const authStore = useAuthStore();
const isCollapsed = ref(false);

const menuGroups: MenuGroup[] = [
  {
    title: '核心应用',
    items: [
      {path: '/chat', title: 'AI 语数对话', icon: 'chat'}
    ]
  },
  {
    title: '数据治理',
    items: [
      {path: '/tenant', title: '租户配置', icon: 'tenant'},
      {path: '/metadata', title: '元数据字典', icon: 'metadata'},
      {path: '/lexicon', title: '语义字典', icon: 'lexicon'},
      {path: '/join-rule', title: 'Join 规则', icon: 'join'}
    ]
  },
  {
    title: '系统设置',
    items: [
      {path: '/model', title: '模型资源池', icon: 'model'}
    ]
  }
];

/**
 * 执行退出登录操作。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleLogout = () => {
  authStore.logout();
  router.push('/login');
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
        background-color="#1f2937"
        text-color="#e5e7eb"
        active-text-color="#ffffff"
        :collapse="isCollapsed"
        :collapse-transition="false"
      >
        <template v-for="group in menuGroups" :key="group.title">
          <div v-if="!isCollapsed" class="menu-group-title">{{ group.title }}</div>
          <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path" :title="item.title">
            <el-icon class="menu-icon">
              <svg v-if="item.icon === 'chat'" viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M4 5h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H9l-5 4v-4H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z"
                />
              </svg>
              <svg v-else-if="item.icon === 'tenant'" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M4 10l8-6 8 6v9a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-9z"/>
              </svg>
              <svg v-else-if="item.icon === 'metadata'" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M4 4h16v4H4V4zm0 6h16v10H4V10zm4 3h8v2H8v-2z"/>
              </svg>
              <svg v-else-if="item.icon === 'lexicon'" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M5 4h12a2 2 0 0 1 2 2v12l-4-2-4 2-4-2-4 2V6a2 2 0 0 1 2-2z"/>
              </svg>
              <svg v-else-if="item.icon === 'join'" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M7 7a4 4 0 0 1 4-4h2v2h-2a2 2 0 0 0-2 2v2H7V7zm8 0a2 2 0 0 0-2-2h-2V3h2a4 4 0 0 1 4 4v2h-2V7zM7 15h2v2a2 2 0 0 0 2 2h2v2h-2a4 4 0 0 1-4-4v-2zm10 0v2a4 4 0 0 1-4 4h-2v-2h2a2 2 0 0 0 2-2v-2h2z"/>
              </svg>
              <svg v-else viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 2a9 9 0 1 1-9 9 9 9 0 0 1 9-9zm0 5a4 4 0 1 0 4 4 4 4 0 0 0-4-4z"/>
              </svg>
            </el-icon>
            <span class="menu-label">{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
      <div class="sidebar-footer" :class="{collapsed: isCollapsed}">
        <div class="avatar-circle">AD</div>
        <div v-if="!isCollapsed" class="user-info">
          <div class="user-name">Admin</div>
          <div class="user-role">权限：01</div>
        </div>
      </div>
    </el-aside>
    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <span class="header-title">管理控制台</span>
        </div>
        <div class="header-right">
          <span class="user-name">{{ authStore.username || '未命名用户' }}</span>
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
  background: #f5f7fb;
}

.layout-aside {
  background: linear-gradient(180deg, #0f172a 0%, #1f2937 100%);
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
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #fff;
  box-shadow: 0 8px 20px rgba(59, 130, 246, 0.35);
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
  background: #e5e7eb;
  border-radius: 2px;
}

.menu {
  border-right: none;
  background: transparent;
  color: #e5e7eb;
  padding: 16px 0 8px;
  flex: 1;
  overflow-y: auto;
}

.menu-group-title {
  padding: 4px 20px 8px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
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
  background: linear-gradient(90deg, rgba(59, 130, 246, 0.25), rgba(59, 130, 246, 0.05));
  box-shadow: 0 6px 12px rgba(59, 130, 246, 0.2);
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
  background: rgba(255, 255, 255, 0.15);
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
  color: rgba(255, 255, 255, 0.6);
  margin-top: 4px;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  padding: 0 20px;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.05);
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
  background: rgba(226, 232, 240, 0.7);
}

.layout-main {
  padding: 20px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}
</style>
