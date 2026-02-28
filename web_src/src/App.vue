<template>
  <el-config-provider :locale="zhCn">
    <router-view/>
  </el-config-provider>
</template>

<script setup lang="ts">
import {ElConfigProvider} from 'element-plus'
// 导入 Element Plus 的中文包
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<style>
/* 全局设计变量与排版体系 */
:root {
  --app-bg: #f4f7fb;
  --app-text: #0f172a;
  --app-muted-text: #475569;
  --app-border: #e2e8f0;
  --app-primary: #2563eb;
  --app-focus-ring: rgba(37, 99, 235, 0.28);
}

html,
body,
#app {
  margin: 0;
  padding: 0;
  width: 100%;
  min-height: 100%;
}

/* 全局基础样式重置 */
body {
  font-family: "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: var(--app-text);
  background: radial-gradient(circle at 2% 2%, #ffffff 0%, var(--app-bg) 48%, #eaf0f9 100%);
}

#app {
  min-height: 100vh;
  min-height: 100dvh;
}

/* 全局焦点可见性，保障键盘可达 */
*:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 2px;
}

button:focus-visible,
[role='button']:focus-visible,
a:focus-visible {
  box-shadow: 0 0 0 3px var(--app-focus-ring);
}
</style>
