import {defineConfig} from 'vite';
import vue from '@vitejs/plugin-vue';
import {resolve} from 'path';
// 2025-12-19 余炘洋: 本地开发代理后端，避免跨域导致401无法捕获
const backendTarget = process.env.VITE_DEV_BACKEND || 'http://localhost:8000';

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 4173,
    proxy: {
      // 统一将内部接口代理到后端，解决CORS阻断401的问题
      '/internal': {
        target: backendTarget,
        changeOrigin: true
      },
      '/api': {
        target: backendTarget,
        changeOrigin: true
      }
    }
  },
  base: './',
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        /**
         * 手动拆分依赖包，降低单包体积告警。
         * @author 余炘洋
         * @date 2026-01-23
         */
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('element-plus')) {
              return 'element-plus';
            }
            if (id.includes('vue-router')) {
              return 'vue-router';
            }
            if (id.includes('pinia')) {
              return 'pinia';
            }
            if (id.includes('vue')) {
              return 'vue';
            }
            return 'vendor';
          }
        }
      }
    }
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  }
});
