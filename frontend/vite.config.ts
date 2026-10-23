import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 注意使用 '/s/' 而不是 '/s'，否则会前缀匹配到 /src/...、/styles.css 等前端资源
      '/s/': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
