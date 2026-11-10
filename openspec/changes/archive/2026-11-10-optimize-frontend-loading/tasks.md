## 1. 路由级代码分割

- [x] 1.1 将 `src/router/index.ts` 全部页面组件改为 `() => import(...)` 动态导入，保留应用外壳与全局组件在入口；验证 `npm run build` 后每个页面生成独立 chunk
- [x] 1.2 增加构建产物断言（或 Playwright 网络断言）：登录页不请求地图、ECharts、TipTap、后台与海报 chunk；验证 `/login` 首屏请求集合符合预期
- [x] 1.3 检查路由守卫与用户状态加载在异步组件下的行为；验证未登录重定向、管理员守卫与二次验证引导仍然正常

## 2. Element Plus 按需引入

- [x] 2.1 增加 `unplugin-auto-import`、`unplugin-vue-components` 开发依赖并在 `vite.config.ts` 配置 `ElementPlusResolver`，生成类型声明到 `src/types`（`components.d.ts` 因 Element Plus 插槽类型冲突从 `vue-tsc` 排除）；验证 `npm run typecheck` 通过
- [x] 2.2 移除 `main.ts` 的全量 `ElementPlus` 注册与全量 CSS，在 `App.vue` 外层使用 `<el-config-provider :locale="zhCn">`，并显式引入 `ElMessage`/`ElMessageBox` 样式；验证组件外观与中文语言包正常
- [x] 2.3 跑生产构建 + E2E，覆盖表格、表单、日期选择器、抽屉、弹窗、消息提示与确认框；验证无缺失组件或样式

## 3. ECharts 按需注册

- [x] 3.1 新增 `src/utils/echarts.ts`，按需注册 `MapChart`、`BarChart`、`PieChart`、`TooltipComponent`、`LegendComponent`、`GridComponent`、`CanvasRenderer`；验证类型检查通过
- [x] 3.2 `MapView.vue` 与 `ExpensesView.vue` 改为从该模块导入并修正类型；验证地图、记账柱状图与饼图正常渲染
- [x] 3.3 确认 ECharts 形成独立异步 chunk 且不进入入口；验证构建报告与运行时网络请求

## 4. Vendor 分包

- [x] 4.1 在 `vite.config.ts` 配置选择性 `manualChunks`（vue 系、echarts、axios），TipTap 交由 Rollup 自动异步分块，不强制拆分 element-plus；验证 chunk 列表与缓存粒度符合设计
- [x] 4.2 对比优化前后入口、地图首屏与单 chunk 的 raw/gzip 数值并记录；验证入口不包含 ECharts/TipTap/后台页面

## 5. 地图数据哈希与并发加载

- [x] 5.1 将 `public/world.json`、`public/antarctica.json` 移到 `src/assets/`，在 `MapView.vue` 以 `?url` 导入并保留 `VITE_WORLD_GEO_URL` 覆盖；验证默认与覆盖两种路径都能加载
- [x] 5.2 重构 `MapView` 初始化：底图/极地数据与 `getCountries()`/`getMapStats()` 并发发起，全部就绪后再合并渲染；验证瀑布图从串行变为并发
- [x] 5.3 验证生产构建中底图位于 `/assets/` 且带内容哈希，响应具备 1 年 immutable 缓存；删除 `public` 下旧文件后构建与部署正常

## 6. 图片懒加载

- [x] 6.1 为 `NotesView`、`ShareView`、`PosterView`、`NoteDetailView` 的列表/网格图片增加 `loading="lazy"`、`decoding="async"`，`<el-image>` 增加 `lazy`；验证首屏外图片不立即请求
- [x] 6.2 为详情页 `v-html` 正文图片增加渲染后属性补齐逻辑（composable 或指令）；验证正文图片在滚动接近时才加载
- [x] 6.3 调整视觉验收截图脚本，确保滚动/等待图片加载后再截图；验证截图无空白图位

## 7. 体积预算收紧

- [x] 7.1 更新 `check-bundle-budget.mjs`：首屏计入 `MapView` 路由静态依赖与 `world.json`，并断言入口不含 `echarts`、`zrender`、`@tiptap`、`prosemirror`；验证脚本输出正确明细
- [x] 7.2 将预算收紧为入口 JS ≤ 400KB gzip、单 JS chunk ≤ 600KB、首屏 ≤ 800KB，并更新 CI 阈值；验证正常构建通过
- [x] 7.3 临时引入一个重依赖或移除懒加载制造超限，确认 CI 体积 job 失败并指出超限项；验证后恢复

## 8. 文档与整体验证

- [x] 8.1 更新 `frontend/README.md` 与 `docs/ops/local-development.md`：路由懒加载、按需引入、底图资源路径、体积预算与检查命令；验证命令可执行
- [x] 8.2 运行 `openspec validate optimize-frontend-loading --strict` 通过
- [x] 8.3 运行 `npm run test:unit && npm run build && node scripts/check-bundle-budget.mjs` 全绿
- [x] 8.4 在生产构建模式下跑 E2E（地图、游记编辑、后台、海报、图片列表）；验证功能与视觉无回归，并记录优化前后体积对比
