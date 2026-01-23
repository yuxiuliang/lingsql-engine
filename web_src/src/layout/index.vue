<script setup lang="ts">
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
}

const router = useRouter();
const authStore = useAuthStore();

const menus: MenuItem[] = [
  {path: '/chat', title: 'AI 语数对话'},
  {path: '/tenant', title: '租户配置'},
  {path: '/model', title: '模型资源池'},
  {path: '/lexicon', title: '语义字典'},
  {path: '/join-rule', title: 'Join 规则'}
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
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="layout-aside">
      <div class="logo">
        <div class="logo-title">语数</div>
        <div class="logo-sub">lingsql-engine</div>
      </div>
      <el-menu
        router
        class="menu"
        :default-active="$route.path"
        background-color="#1f2937"
        text-color="#e5e7eb"
        active-text-color="#ffffff"
      >
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
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
  background: #1f2937;
  color: #fff;
}

.logo {
  padding: 20px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
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

.menu {
  border-right: none;
  background: transparent;
  color: #e5e7eb;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
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

.user-name {
  font-size: 13px;
  color: #374151;
}

.tenant-tag {
  border: none;
  color: #475569;
  background: rgba(226, 232, 240, 0.7);
}

.layout-main {
  padding: 20px;
}
</style>
