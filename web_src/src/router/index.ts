import {createRouter, createWebHistory, RouteRecordRaw} from 'vue-router';
import {useAuthStore} from '@/store/auth';
import {pinia} from '@/store';
import {fetchProfile} from '@/api/auth';
// 1. 定义路由记录
const routes: Array<RouteRecordRaw> = [
    {
        path: '/',
        redirect: '/chat',
    },
    {
        path: '/login',
        name: 'Login',
        component: () => import('@/views/login/index.vue'),
        meta: {title: '登录'}
    },
    {
        path: '/password-change',
        name: 'PasswordChange',
        component: () => import('@/views/password-change/index.vue'),
        meta: {title: '修改密码'}
    },
    {
        path: '/layout',
        name: 'Layout',
        component: () => import('@/layout/index.vue'), // 布局组件
        redirect: '/chat',
        children: [
            {
                path: '/chat',
                name: 'Chat',
                component: () => import('@/views/chat/index.vue'),
                meta: {title: 'AI 语数对话', icon: 'ChatLineRound'}
            },
            {
                path: '/history',
                name: 'ChatHistoryCompat',
                redirect: '/chat',
                meta: {title: '问答历史', icon: 'ChatLineRound'}
            },
            {
                path: '/chat-history',
                name: 'ChatHistoryLegacyCompat',
                redirect: '/chat',
                meta: {title: '问答历史', icon: 'ChatLineRound'}
            },
            {
                path: '/chat/history',
                name: 'ChatHistoryNestedCompat',
                redirect: '/chat',
                meta: {title: '问答历史', icon: 'ChatLineRound'}
            },
            {
                path: '/dashboard',
                name: 'Dashboard',
                component: () => import('@/views/dashboard/index.vue'),
                meta: {title: '仪表盘', icon: 'Dashboard'}
            },
            {
                path: '/model',
                name: 'Model',
                component: () => import('@/views/model/index.vue'),
                meta: {title: '模型资源池', icon: 'Cpu'}
            },
            {
                path: '/tenant',
                name: 'Tenant',
                component: () => import('@/views/tenant-manage/index.vue'),
                meta: {title: '租户管理', icon: 'OfficeBuilding'}
            },
            {
                path: '/plan',
                name: 'PlanManage',
                component: () => import('@/views/plan/index.vue'),
                meta: {title: '套餐管理', icon: 'Tickets'}
            },
            // 数字员工管理页，修复菜单点击与刷新后 404。
            {
                path: '/digital-employee',
                name: 'DigitalEmployee',
                component: () => import('@/views/digital-employee/index.vue'),
                meta: {title: '数字员工', icon: 'Cpu'}
            },
            // 人工审批工作台：裁决通过后可发起续跑，续跑仍完整经过 SQL 安全执行链。
            {
                path: '/approval',
                name: 'AgentApproval',
                component: () => import('@/views/approval/index.vue'),
                meta: {title: '人工审批', icon: 'Stamp'}
            },
            {
                path: '/billing',
                name: 'BillingManage',
                component: () => import('@/views/billing/index.vue'),
                meta: {title: '计费规则', icon: 'CreditCard'}
            },
            {
                path: '/datasource',
                name: 'DatasourceManage',
                component: () => import('@/views/tenant/index.vue'),
                meta: {title: '数据源管理', icon: 'OfficeBuilding'}
            },
            {
                path: '/lexicon',
                name: 'Lexicon',
                component: () => import('@/views/lexicon/index.vue'),
                meta: {title: '语义字典', icon: 'Collection'}
            },
            {
                path: '/join-rule',
                name: 'JoinRule',
                component: () => import('@/views/join-rule/index.vue'),
                meta: {title: 'Join 规则', icon: 'Share'}
            },
            {
                path: '/metadata',
                name: 'Metadata',
                component: () => import('@/views/metadata/index.vue'),
                meta: {title: '元数据字典', icon: 'Notebook'}
            },
            {
                path: '/experience',
                name: 'Experience',
                component: () => import('@/views/experience/index.vue'),
                meta: {title: '体验账号', icon: 'User'}
            },
            {
                path: '/organization',
                name: 'OrganizationManage',
                component: () => import('@/views/organization/index.vue'),
                meta: {title: '组织架构', icon: 'OfficeBuilding'}
            },
            {
                path: '/user',
                name: 'UserManage',
                component: () => import('@/views/user/index.vue'),
                meta: {title: '用户管理', icon: 'User'}
            },
            {
                path: '/role',
                name: 'RoleManage',
                component: () => import('@/views/role/index.vue'),
                meta: {title: '角色管理', icon: 'Role'}
            },
            {
                path: '/permission',
                name: 'PermissionManage',
                component: () => import('@/views/permission/index.vue'),
                meta: {title: '权限管理', icon: 'Permission'}
            },
            {
                path: '/menu',
                name: 'MenuManage',
                component: () => import('@/views/menu/index.vue'),
                meta: {title: '菜单治理', icon: 'Permission'}
            },
            {
                path: '/plugin',
                name: 'PluginManage',
                component: () => import('@/views/plugin/index.vue'),
                meta: {title: '插件治理', icon: 'Cpu'}
            },
            {
                path: '/audit',
                name: 'AuditLog',
                component: () => import('@/views/audit/index.vue'),
                meta: {title: '审计日志', icon: 'Audit'}
            },
            {
                path: '/sql-log',
                name: 'SqlLog',
                component: () => import('@/views/sql-log/index.vue'),
                meta: {title: 'SQL 日志', icon: 'Sql'}
            }
        ]
    },
    {
        path: '/:pathMatch(.*)*',
        name: 'NotFound',
        component: () => import('@/views/error/404.vue')
    }
];

const router = createRouter({
    history: createWebHistory(),
    routes
});

// 2. 路由守卫：处理租户 ID 和标题
/**
 * 全局路由守卫，负责登录拦截与标题设置。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
router.beforeEach(async (to) => {
    // 设置页面标题
    if (to.meta.title) {
        document.title = `${to.meta.title} - 语数`;
    }

    const authStore = useAuthStore(pinia);
    if (to.path === '/login' && !authStore.token) {
        return true;
    }
    if (!authStore.token) {
        return '/login';
    }
    if (authStore.isRestrictedSession) {
        authStore.restoreRestrictedSession();
        return to.path === '/password-change' ? true : '/password-change';
    }
    if (!authStore.sessionReady) {
        try {
            const profile = await fetchProfile();
            authStore.applyProfile(profile);
            authStore.sessionReady = true;
        } catch {
            if (authStore.isRestrictedSession) {
                return to.path === '/password-change' ? true : '/password-change';
            }
            authStore.logout();
            return '/login';
        }
    }
    if (!authStore.isAuthenticated) {
        return '/login';
    }
    if (to.path === '/login' || to.path === '/password-change') {
        return '/chat';
    }
    return true;
});

export default router;
