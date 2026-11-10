## Context

见 `proposal.md`。当前构建约束：

- Vue 3 + Vite 6 + TypeScript；`src/main.ts` 全量注册 Element Plus 并引入全量 CSS。
- `src/router/index.ts:3-28` 静态导入 28 个页面组件，无任何路由级动态导入。
- `MapView.vue` 与 `ExpensesView.vue` 使用 `import * as echarts from 'echarts'`；`RichTextEditor.vue` 静态引入 TipTap。
- `world.json`（约 1MB）与 `antarctica.json` 位于 `public/`，通过 `/world.json` 运行时 fetch，且在页面 API 完成后才发起。
- 图片已有缩略图，但列表/详情/分享/海报的 `<img>` 与 `<el-image>` 未启用懒加载。
- 前置 change 已提供 Vite manifest 体积预算脚本与 CI 门禁，本 change 负责把预算收紧到目标值并扩展首屏解析。

## Goals / Non-Goals

**Goals:**

- 登录/公开页首屏只加载应用外壳与当前路由代码。
- ECharts、TipTap 与 Element Plus 组件不再进入公共入口。
- 地图底图可长期缓存，并与页面数据并发加载。
- 用预算脚本锁定入口 400KB、单 chunk 600KB、首屏 800KB（gzip）的目标。

**Non-Goals:**

- 不简化或替换 GeoJSON 几何数据。
- 不引入 SSR、PWA 或 CDN。
- 不改变 UI 布局、交互与业务逻辑。
- 不引入新的运行时日期/图表/编辑器依赖。
- 不追求 Lighthouse 分数门禁（体积预算已能稳定阻断回退）。

## Decisions

### 1. 全部路由改为动态导入

- `router/index.ts` 中所有 `component` 改为 `() => import('../views/Xxx.vue')`；`App.vue`、`FeedbackDialog`、全局样式保留在入口。
- 公开路由（登录、注册、重置、管理员登录、分享页）因此不再拉取地图、编辑器、后台与海报代码。
- 不额外实现预取策略：登录成功后导航到地图页时由路由自动加载地图 chunk，避免登录页提前下载。
- 替代方案：仅懒加载后台路由。实现更小，但登录页仍会加载 ECharts 与 TipTap，收益不足。

### 2. Element Plus 按需引入

- 增加开发依赖 `unplugin-auto-import`、`unplugin-vue-components`，在 `vite.config.ts` 注册 `ElementPlusResolver({ importStyle: 'css' })`；生成的类型声明放到 `src/types/`。`components.d.ts` 会生成供 IDE 参考，但因 Element Plus 表格插槽的严格 `DefaultRow` 类型与现有模板写法冲突，`vue-tsc` 通过 `tsconfig.exclude` 跳过该文件；运行时的组件解析不受影响。
- `main.ts` 移除 `app.use(ElementPlus)` 与 `element-plus/dist/index.css`；改为在 `App.vue` 最外层使用 `<el-config-provider :locale="zhCn">` 提供中文语言包。
- 现有 33 个文件中显式 `import { ElMessage, ElMessageBox } from 'element-plus'` 保持不动（便于按需摇树），在 `main.ts` 显式引入 `message` 与 `message-box` 的 CSS。
- 替代方案：逐文件手写组件与样式导入。可控但改动面大、易漏，因此不选。

### 3. ECharts 按需注册

- 新增 `src/utils/echarts.ts`：从 `echarts/core` 引入并 `use` `MapChart`、`BarChart`、`PieChart`、`TooltipComponent`、`LegendComponent`、`GridComponent`、`CanvasRenderer`，统一导出。
- `MapView.vue` 与 `ExpensesView.vue` 改为从该模块导入；两者都是懒加载路由，Rollup 会生成被二者共享的 ECharts 异步 chunk。
- 类型使用 `echarts/core` 导出的 `ECharts`，不再依赖全量包类型。
- 替代方案：继续全量 `echarts`。运行时功能不变但入口/地图 chunk 过大，无法满足预算。

### 4. 选择性 vendor 分包

- `vite.config.ts` 增加 `build.rollupOptions.output.manualChunks` 函数：将 `vue`/`vue-router`/`pinia` 归入 `vue`，`echarts`/`zrender` 归入 `echarts`，`axios` 单独。
- 不强制拆分 `element-plus`：按需引入后由 Rollup 依据实际使用自动形成共享 chunk，避免把全量组件提升为入口依赖。
- 实测把 `@tiptap`/`prosemirror` 也放进 `manualChunks` 会让 Vite 把 TipTap chunk 作为入口静态依赖预加载，违背首屏目标；因此 TipTap 交给 Rollup 自动生成异步 chunk，仅在被游记编辑相关路由引用时加载。
- 替代方案：按包全量分包。缓存粒度更粗，且可能让入口加载未使用组件，因此不选。

### 5. 地图数据哈希化与并发加载

- 将 `world.json`、`antarctica.json` 从 `public/` 移到 `src/assets/`，在 `MapView.vue` 以 `?url` 导入获取哈希 URL；默认底图地址为导入值，`VITE_WORLD_GEO_URL` 仍优先。
- 哈希资源输出到 `/assets/`，直接复用 Nginx 现有 1 年 immutable 规则，无需新增配置。
- `load()` 拆分为“解析底图”和“渲染”两步：进入页面即同时发起 `fetch(worldUrl)`、极地数据与 `getCountries()`/`getMapStats()`；全部就绪后再合并、`registerMap`、`init`。
- 删除 `public/world.json`、`public/antarctica.json`，避免部署时继续携带未哈希副本。
- 替代方案：只给 `world.json` 加 Nginx `expires`。无法获得内容哈希，发版后仍需重新验证，因此不选。

### 6. 图片懒加载

- 列表/网格与正文中的 `<img>` 增加 `loading="lazy"`、`decoding="async"`；Element Plus 的 `<el-image>` 增加 `lazy`。
- 详情页 `v-html` 渲染的正文图片无法在模板上声明，改为在内容渲染后统一查询容器内 `img` 并补属性（简单 composable 或指令），避免正文图片在首屏全部下载。
- 封面等首屏关键图片保持即时加载，避免 LCP 回退。
- 替代方案：引入图片懒加载库。原生属性已覆盖需求，且不增加包体积。

### 7. 预算收紧与首屏解析

- 更新前置 change 的 `check-bundle-budget.mjs`：入口取 manifest 的 `index.html` 静态依赖；首屏额外沿 `src/views/MapView.vue` 的 `imports` 收集地图路由静态依赖，并把 manifest 中的 `world.json` 资源计入。
- 预算改为：入口 JS ≤ 400KB gzip、任意单 JS chunk ≤ 600KB、首屏 ≤ 800KB gzip。
- 脚本同时断言入口静态依赖不包含 `echarts`、`zrender`、`@tiptap`、`prosemirror`，把“重依赖不得回到入口”变成构建门禁。
- 替代方案：只看入口大小。路由拆分后入口可能很小，但地图首屏仍可能过大，因此需要首屏口径。

## Risks / Trade-offs

- [按需引入漏掉模板中动态使用的组件] → 检查 `:is`/动态组件用法；CI 中跑生产构建 + E2E 覆盖地图、编辑器、后台与弹窗，缺失样式或组件会直接暴露。
- [ElMessage/ElMessageBox 样式未随按需引入加载] → 在 `main.ts` 显式引入两者 CSS，并用 E2E 触发一次消息与确认框验证。
- [路由拆分后首次进入页面出现短暂空白] → 地图页保留骨架/加载态；接受一次异步 chunk 加载，换取登录页首屏显著变快。
- [`manualChunks` 分组不当导致重复或过大 chunk] → 只分组稳定的框架/图表/编辑器依赖，Element Plus 交给 Rollup；预算脚本输出每个 chunk 明细。
- [首屏 800KB 预算可能被 Element Plus 地图页组件占满] → 先按实测定位；必要时把公告弹窗、攻略抽屉等非首屏 UI 改为异步组件，不简化底图。
- [移动 `world.json` 到 assets 影响外部引用] → 仅内部使用；`VITE_WORLD_GEO_URL` 保留覆盖能力，文档注明默认路径变化。
- [懒加载图片影响视觉验收截图] → 截图脚本滚动到底部或等待图片加载后再截；调整 `screens.spec.ts`。

## Migration Plan

1. 依赖前置的 CI 基座与预算脚本已合入，先记录当前基线。
2. 依次实现路由懒加载、Element Plus/ECharts 按需、底图哈希与并发、图片懒加载；每步跑生产构建与预算脚本，观察 chunk 变化。
3. 预算达标后把 CI 阈值收紧到 400/600/800KB，并合入入口依赖断言。
4. 部署后验证 `/assets/*.json` 的 `Cache-Control` 与地图首屏瀑布；旧 `public/world.json` 不再被请求。
5. 回滚只需回退前端构建产物；无数据或 API 变更。

## Open Questions

无。若首屏 800KB 在真实构建中无法达到，优先异步化非首屏 UI，而不是放宽预算或简化地图数据。