## Why

前端入口 JS 约 2.86MB raw / 925KB gzip，CSS 约 54KB gzip，地图底图 `world.json` 另有 1MB raw / 285KB gzip，且与页面 API 串行加载。28 条路由全部静态导入，导致未登录用户也要先下载 ECharts、TipTap、后台页面与海报代码；Element Plus 与 ECharts 均全量引入，单一 chunk 使每次发版都整包失效重下。这直接拖慢首次加载，也让移动网络下的首屏地图体验不可接受。

## What Changes

- 28 条路由全部改为路由级懒加载，登录/注册等公开页不再加载地图、编辑器、后台与海报代码；地图页作为首页保持最短路径加载。
- Element Plus 改为按需引入（`unplugin-vue-components` + `unplugin-auto-import`），移除全量组件注册与全量 CSS；语言包通过 `ElConfigProvider` 提供，`ElMessage`/`ElMessageBox` 所需样式显式引入。
- ECharts 改为 `echarts/core` 按需注册（地图、柱状、饼图及实际使用的组件），形成仅被地图页与记账页加载的独立 chunk。
- 对 `vue`、`echarts`、`tiptap` 做选择性 vendor 分包，提升长期缓存命中；Element Plus 交由 Rollup 按实际使用自动拆分。
- `world.json` 与 `antarctica.json` 移入 `src/assets` 并以 `?url` 导入，获得带哈希的 `/assets/` 路径与 1 年 immutable 缓存；保留 `VITE_WORLD_GEO_URL` 覆盖能力。底图请求与页面 API 并行，缩短首屏瀑布。
- 游记列表、分享页、海报页、详情页的图片增加原生懒加载与异步解码。
- 在 CI 中把体积预算收紧到目标值：入口 JS gzip ≤ 400KB、任意单 JS chunk gzip ≤ 600KB、首屏（入口 JS+CSS+地图 chunk+`world.json`）gzip ≤ 800KB；超限阻断 PR。
- 不简化 GeoJSON 几何，地图边界与中文地名保持现状。

## Capabilities

### New Capabilities

- `ui/performance`: 定义前端首屏加载、路由级代码分割、静态资源缓存与包体积预算的可验证约束，防止后续改动重新把重依赖带回入口。

### Modified Capabilities

无。

## Impact

- 前端：`src/router/index.ts`、`src/main.ts`、`src/App.vue`、`MapView`/`ExpensesView` 的 ECharts 引入、`RichTextEditor` 相关按需加载、多个页面的 `<img>`、`vite.config.ts`、`package.json`/lockfile。
- 构建产物：新增/拆分多个路由与 vendor chunk；`world.json`/`antarctica.json` 从 `public/` 移到 `src/assets/` 并以哈希资源输出。
- CI：更新体积预算阈值与首屏解析逻辑（依赖测试与 CI 基座 change）。
- 部署：Nginx 现有 `/assets/` 长缓存规则即可覆盖底图，无需新增规则；旧 `public` 路径不再使用。
- 无 API、数据库或用户可见业务行为变化；仅加载方式与性能变化。