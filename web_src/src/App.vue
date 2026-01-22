<template>
  <el-config-provider :locale="zhCn">
    <router-view/>
  </el-config-provider>
</template>

<script setup lang="ts">
import {ElConfigProvider} from 'element-plus'
// 导入 Element Plus 的中文包
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
</script>

<style>
/* 全局基础样式重置 */
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #2c3e50;
  margin: 0;
  padding: 0;
  height: 100vh;
}

/* 消除 body 默认间距 */
body {
  margin: 0;
  padding: 0;
}
</style>