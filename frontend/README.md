# 旅迹地图 Frontend

Vue 3 + Vite + TypeScript + Pinia + Vue Router + Element Plus + ECharts.

## 启动

```bash
npm install
cp .env.example .env
npm run dev
```

开发服务器默认 `http://localhost:5173`，`/api` 代理到后端 `http://localhost:8080`。

## 登录入口与内置账号

| 角色 | 登录地址 | 内置账号 | 说明 |
|---|---|---|---|
| 普通用户 | http://localhost:5173/login | 无内置账号 | 先到 `/register` 注册：填写邮箱 → 点“发送验证码” → 填 6 位验证码 → 设置密码；注册成功自动登录 |
| 管理员 | http://localhost:5173/admin/login | `admin` / `Admin@123456` | 仅 `ADMIN` 角色可登录；普通用户在此登录会被拒绝并清除登录态 |

- 管理员从 `/admin/login` 登录后进入用户管理页；普通用户从 `/login` 登录后进入地图首页。
- 普通用户页面不展示管理员入口，需要直接访问 `/admin/login`。
- 预置管理员来自数据库种子数据（`V2__seed.sql`），**生产首次部署必须修改密码**。
- 开发环境 `MAIL_ENABLED=false` 时不发邮件，验证码只打印在后端日志：`[auth-dev] scene=... email=... code=...`。
- 登录失败返回 `1001`；未验证邮箱 `1002`；封禁 `1003`；已注销 `1007`。
- 登录页“保持登录”勾选时 Token 存 `localStorage`（关闭浏览器仍登录），不勾选存 `sessionStorage`（关闭标签页失效）；服务端 JWT 有效期固定 7 天。
- 忘记密码：`/reset-password`（`/forgot-password` 会重定向到这里），用邮箱验证码 + 新密码重置，旧 JWT 立即失效。

## 页面

- `/login`、`/register`、`/reset-password`：登录、注册、找回密码（入口与账号见上一节）。
- `/admin/login`：管理员登录入口。
- `/profile` 个人中心：昵称/头像、额度明细、卡密兑换、注销账号。
- `/` 世界地图：三态着色、悬浮计数、点击国家打开抽屉（归档卡片 + 未归组摘要）。
- `/plan` AI 行程规划：多城市/天数/偏好生成；草稿支持天增删改排、景点增删改、天内排序与跨天移动，保存统一提交；行前准备卡片、七项清单、官方链接兜底。
- `/itineraries/:id` 已保存行程编辑：基本信息、景点/天 CRUD、跨天移动、AI 重新生成（保留手动景点并提示合并）、删除行程；地图抽屉中的行程可直接进入。
- `/trips/:id` 归档详情：概览与汇总、行程/游记全部、最近 10 条消费；可跳转各页继续录入。地图抽屉展示归档卡片（名称、日期、行程数、游记数、花费），点击进入归档详情；未归组为空时不显示该区块。
- 归档自动完成：同国家行程间隔 ≤30 天归为同一次；游记与消费按日期自动归类（前后各 30 天），匹配不到时自动新建归档。界面不再要求手动选择；记账页仍可按归档筛选查看。
- `/polish` AI 流式润色：fetch + ReadableStream 打字机渲染，取消按钮先调用 `/api/ai/cancel` 再中断连接。
- `/notes` 游记：TipTap 图文编辑器（标题/加粗/列表/引用/链接/图片，粘贴或拖拽上传、压缩后直传），保存后跳转详情。
- `/notes/:id` 游记详情/编辑：封面、可见性、关联旅行、分享管理、下架状态；支持按 ID 打开和生成海报。
- `/poster/:id?` 游记海报：自动确保分享令牌、拉取地图统计、Canvas 足迹连线、二维码、html2canvas 下载 PNG。
- `/expenses` 记账：新增/编辑/删除、国家/城市/旅行/分类/日期筛选、分页、按国家/城市/旅行/日期汇总与 ECharts 图表。
- `/admin/*`（需 ADMIN，导航“管理”菜单）：用户管理、卡密生成/导出/作废、城市维护、官方链接维护、汇率维护、内容下架/恢复、LLM/搜索配置。LLM 未配置时生成行程返回 `2012`。
- 行程草稿与已保存编辑页的景点支持同一天内拖拽排序；跨天请用“移到…”菜单。
- `/share/:token` 公开只读分享页（无需登录，失效时展示“分享已失效”）；分享链接与海报二维码均指向该路由。
- 城市字典：`V4__more_cities.sql` 预置 123 个中国常用地级市/旅游城市，以及日本、泰国、韩国、新加坡、法国、意大利、美国、澳大利亚的热门城市；其余城市可通过后台城市接口补充。

## 浏览器 E2E

```bash
npm run test:e2e        # Playwright + 本机 Chrome
npm run test:e2e:headed

# 视觉验收截图（输出到 test-results/screens/）
npx playwright test e2e/screens.spec.ts
```

需要后端已启动。覆盖地图/抽屉/行程编辑、游记创建与分享、海报下载、公开分享页、后台页面。详见 `e2e/README.md`。

## 地图底图

默认使用本地 `public/world.json`（ECharts 4 的世界地图 GeoJSON，约 1MB），不再依赖外部 CDN；可用 `VITE_WORLD_GEO_URL` 覆盖。

悬浮提示与悬停标签显示中文国家名，取值顺序：目录中文名 → `src/assets/world-names-zh.json` 内置对照表 → 底图原名。替换底图后运行 `node scripts/check-world-names.mjs` 检查是否有未翻译的要素。上线前仍必须按 SRS 7.3 确认底图合规来源与审图号。底图加载失败时会自动切换为国家列表，列出全部国家（未点亮国家为灰色，同样可点击查看详情）。

## 移动端

ECharts `roam` 已开启双指缩放/单指拖拽，地图容器设置 `touch-action: none`。仍需要在 iOS Safari 15+、Android Chrome 100+ 与微信内置浏览器做一次真机验收。
