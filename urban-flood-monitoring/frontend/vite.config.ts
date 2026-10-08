import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// Vite 构建配置
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      // 使用 @ 指向 src 目录，方便引入
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    // 开发环境代理：将前端请求转发到后端服务
    proxy: {
      // REST API 代理到后端 Spring Boot（默认 8080）
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // WebSocket 实时推送代理
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1500,
  },
})

