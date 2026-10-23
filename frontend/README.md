# TravelMapper Frontend

Vue 3 + Vite + TypeScript + Pinia + Vue Router + Element Plus + ECharts.

## 启动

```bash
npm install
cp .env.example .env
npm run dev
```

开发服务器默认 `http://localhost:5173`，`/api` 代理到后端 `http://localhost:8080`。

## 登录

开发环境预置管理员 `admin / Admin@123456`。普通用户需先通过后端 `/api/auth/register` 注册并验证邮箱。

## 页面

- `/` 世界地图：三态着色、悬浮计数、点击国家打开抽屉。
- `/plan` AI 行程规划：多城市/天数/偏好生成；草稿支持天增删改排、景点增删改、天内排序与跨天移动，保存统一提交；行前准备卡片、七项清单、官方链接兜底。
- `/itineraries/:id` 已保存行程编辑：基本信息、景点/天 CRUD、跨天移动、AI 重新生成（保留手动景点并提示合并）、删除行程；地图抽屉中的行程可直接进入。
- `/polish` AI 流式润色：fetch + ReadableStream 打字机渲染，取消按钮先调用 `/api/ai/cancel` 再中断连接。
- `/notes` 游记：TipTap 图文编辑器（标题/加粗/列表/引用/链接/图片，粘贴或拖拽上传、压缩后直传），保存后跳转详情。
- `/notes/:id` 游记详情/编辑：封面、可见性、关联旅行、分享管理、下架状态；支持按 ID 打开和生成海报。
- `/poster/:id?` 游记海报：自动确保分享令牌、拉取地图统计、Canvas 足迹连线、二维码、html2canvas 下载 PNG。
- `/expenses` 记账：表单、列表、按国家/旅行汇总与未配置汇率提示。
- `/admin/rates` 管理员汇率维护；`/admin/notes` 管理员内容下架/恢复；`/admin/settings` LLM/搜索配置（均需 ADMIN）。LLM 未配置时生成行程返回 `2012`。
- `/share/:token` 公开只读分享页（无需登录，失效时展示“分享已失效”）；分享链接与海报二维码均指向该路由。

## 浏览器 E2E

```bash
npm run test:e2e        # Playwright + 本机 Chrome
npm run test:e2e:headed
```

需要后端已启动。覆盖地图/抽屉/行程编辑、游记创建与分享、海报下载、公开分享页、后台页面。详见 `e2e/README.md`。

## 地图底图

默认使用本地 `public/world.json`（ECharts 4 的世界地图 GeoJSON，约 1MB），不再依赖外部 CDN；可用 `VITE_WORLD_GEO_URL` 覆盖。上线前仍必须按 SRS 7.3 确认底图合规来源与审图号。底图加载失败时会自动切换为国家列表，列出全部国家（未点亮国家为灰色，同样可点击查看详情）。

## 移动端

ECharts `roam` 已开启双指缩放/单指拖拽，地图容器设置 `touch-action: none`。仍需要在 iOS Safari 15+、Android Chrome 100+ 与微信内置浏览器做一次真机验收。
