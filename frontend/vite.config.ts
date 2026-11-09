import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import pkg from './package.json'

// 默认代理到本机 8080；可用 VITE_API_TARGET 指向另一个后端实例（例如 E2E 用 8090）。
const apiTarget = process.env.VITE_API_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version)
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true
      },
      // 注意使用 '/s/' 而不是 '/s'，否则会前缀匹配到 /src/...、/styles.css 等前端资源
      '/s/': {
        target: apiTarget,
        changeOrigin: true
      }
    }
  }
})