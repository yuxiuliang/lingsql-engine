import {createApp} from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import router from './router'
import {createPinia} from 'pinia'
import App from './App.vue';

/**
 * Vue应用入口，挂载Element Plus并渲染根组件。
 * @author 余炘洋
 * @date 2025-12-02 15:45:00
 */
createApp(App)
    .use(ElementPlus, {size: 'default'})
    .use(createPinia())
    .use(router)
    .mount('#app');
