## Purpose

定义前端首屏加载与长期缓存的可验证约束，确保每个页面只下载自身需要的代码与资源，重依赖不再进入公共入口，并通过体积预算防止性能回退。

## ADDED Requirements

### Requirement: Route-level code splitting
系统 MUST 按路由拆分前端代码，首次进入某个路由时 MUST 只加载应用外壳与该路由及其依赖的代码。公开的登录、注册、重置密码页面 MUST NOT 加载地图、富文本编辑器、后台管理或海报页面的代码。

#### Scenario: Login page avoids application chunks
- **WHEN** 未登录用户在生产构建中打开 `/login`
- **THEN** 浏览器只请求入口外壳与登录页相关 chunk，不请求地图、ECharts、TipTap、后台或海报 chunk

#### Scenario: Map route loads map code on demand
- **WHEN** 已登录用户从登录页进入地图首页
- **THEN** 地图路由 chunk 在导航后加载，登录页首次渲染不等待该 chunk

### Requirement: On-demand heavy dependencies
Element Plus、ECharts 与 TipTap MUST 按实际使用引入。应用入口 chunk MUST NOT 包含 ECharts、TipTap 或其传递依赖。图表代码 MUST 仅在地图页与记账页加载，富文本编辑器代码 MUST 仅在需要编辑的页面加载。

#### Scenario: Entry manifest excludes chart and editor libraries
- **WHEN** 检查生产构建的入口静态依赖
- **THEN** 其中不包含 `echarts`、`zrender`、`@tiptap` 或 `prosemirror` 模块

#### Scenario: Charts load only on chart pages
- **WHEN** 用户访问地图页或记账页
- **THEN** 对应 ECharts chunk 被加载；访问不包含图表的页面时不加载该 chunk

#### Scenario: Editor loads only when authoring
- **WHEN** 用户打开游记编辑界面
- **THEN** TipTap/ProseMirror chunk 被加载；仅浏览列表或详情时不加载

### Requirement: Hashed map assets with concurrent loading
地图底图与极地数据 MUST 作为带内容哈希的静态资源输出，并可由现有 `/assets/` 长缓存策略返回。地图页 MUST 在请求页面数据的同时开始获取底图数据，不得等待页面 API 完成后再串行请求。`VITE_WORLD_GEO_URL` 配置 MUST 仍可覆盖默认底图地址。

#### Scenario: Map assets are immutable and hashed
- **WHEN** 生产构建部署后请求默认地图底图
- **THEN** URL 位于 `/assets/` 且包含内容哈希，响应带 `Cache-Control: public, immutable`

#### Scenario: Map data fetch runs in parallel with page APIs
- **WHEN** 地图页初始化
- **THEN** 底图请求与国家/统计 API 请求在同一阶段并发发起，而不是等 API 完成后再发起

#### Scenario: Environment override still works
- **WHEN** 配置了 `VITE_WORLD_GEO_URL`
- **THEN** 地图页从该地址加载底图，而不使用默认哈希资源

### Requirement: Bundle size budgets
生产构建 MUST 满足以下 gzip 预算：入口 JS ≤ 400KB、任意单个 JS chunk ≤ 600KB、首屏（入口 JS + 入口 CSS + 地图路由静态依赖 + 地图底图）≤ 800KB。CI MUST 在超限时失败并输出超限项明细。

#### Scenario: Budget check passes on normal build
- **WHEN** 执行生产构建与体积检查
- **THEN** 上述三项指标均在预算内，检查命令以 0 退出

#### Scenario: Budget regression blocks merge
- **WHEN** 某次改动使入口 JS gzip 超过 400KB 或首屏超过 800KB
- **THEN** CI 体积检查以非零退出并指出超限的 chunk 与数值

### Requirement: Lazy loading for offscreen images
游记列表、游记详情、公开分享页与海报页中位于首屏之外的图片 MUST 使用浏览器原生懒加载与异步解码，MUST NOT 在页面初次渲染时全部发起下载。

#### Scenario: Offscreen images load on approach
- **WHEN** 打开包含多张图片的游记列表并停留在首屏
- **THEN** 首屏之外的图片请求不会立即发出；滚动接近后才开始加载

#### Scenario: Visible images still render
- **WHEN** 用户滚动到图片位置
- **THEN** 图片正常解码并显示，不出现长期空白占位