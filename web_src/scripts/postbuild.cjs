#!/usr/bin/env node
/**
 * 构建结束后将产物同步到Spring Boot静态资源目录。
 * @author 余炘洋
 * @date 2025-12-02 14:40:00
 */
const fs = require('fs');
const path = require('path');

const distDir = path.resolve(__dirname, '../dist');
const targetDir = path.resolve(__dirname, '../../src/main/resources/static/admin');

if (!fs.existsSync(distDir)) {
  console.error('未找到dist目录，先运行 vite build');
  process.exit(1);
}

fs.rmSync(targetDir, { recursive: true, force: true });
fs.mkdirSync(targetDir, { recursive: true });
fs.cpSync(distDir, targetDir, { recursive: true });
console.log('前端构建产物已复制到 src/main/resources/static/admin');
