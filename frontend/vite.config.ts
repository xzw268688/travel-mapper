import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import pkg from './package.json'

// 默认代理到本机 8080；可用 VITE_API_TARGET 指向另一个后端实例（例如 E2E 用 8090）。
const apiTarget = process.env.VITE_API_TARGET || 'http://localhost:8080'

const proxy = {
  '/api': {
    target: apiTarget,
    changeOrigin: true
  },
  // 注意使用 '/s/' 而不是 '/s'，否则会前缀匹配到 /src/...、/styles.css 等前端资源
  '/s/': {
    target: apiTarget,
    changeOrigin: true
  },
  // Swagger UI 与 OpenAPI 文档由后端提供；不代理会被 SPA fallback 到 index.html
  '/swagger-ui': {
    target: apiTarget,
    changeOrigin: true
  },
  '/v3/api-docs': {
    target: apiTarget,
    changeOrigin: true
  }
}

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      dts: 'src/types/auto-imports.d.ts'
    }),
    Components({
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
      dts: 'src/types/components.d.ts'
    })
  ],
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version)
  },
  build: {
    manifest: true,
    rollupOptions: {
      output: {
        // Element Plus 交给 Rollup 按实际使用自动分块；这里只固定框架与重依赖。
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined
          }
          if (id.includes('/echarts/') || id.includes('/zrender/')) {
            return 'echarts'
          }
          if (id.includes('/vue/') || id.includes('@vue/') || id.includes('vue-router')
              || id.includes('/pinia/')) {
            return 'vue'
          }
          if (id.includes('/axios/')) {
            return 'axios'
          }
          return undefined
        }
      }
    }
  },
  server: {
    port: 5173,
    proxy
  },
  preview: {
    port: 5173,
    proxy
  }
})