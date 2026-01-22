import {createRouter, createWebHistory, RouteRecordRaw} from 'vue-router';

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
        path: '/layout',
        name: 'Layout',
        component: () => import('@/layout/index.vue'), // 布局组件
        children: [
            {
                path: '/chat',
                name: 'Chat',
                component: () => import('@/views/chat/index.vue'),
                meta: {title: 'AI 语数对话', icon: 'ChatLineRound'}
            },
            {
                path: '/model',
                name: 'Model',
                component: () => import('@/views/model/index.vue'),
                meta: {title: '模型配置', icon: 'Cpu'}
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
router.beforeEach((to, from, next) => {
    // 设置页面标题
    if (to.meta.title) {
        document.title = `${to.meta.title} - 语数`;
    }

    // 如果没有登录，跳转到相应页面
    const token = localStorage.getItem('token');
    if (!token && to.path !== '/login') {
        next('/login');
    } else {
        next();
    }

    next();
});

export default router;