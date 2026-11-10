# 浏览器 E2E

基于 Playwright + 本机 Chrome（`channel: 'chrome'`，无需下载 Chromium）。

## 前置条件

1. 后端已启动（默认 `http://localhost:8080`），数据库可用。
2. 预置管理员可登录（默认 `admin / Admin@123456`）。
3. 前端依赖已安装。

## 运行

```bash
cd frontend
npm run test:e2e          # 无头
npm run test:e2e:headed   # 带界面
```

Playwright 会自动启动 Vite 开发服务器（`http://localhost:5173`，已存在则复用）。

## 测试内容

- 地图主页 → 日本图例 → 国家抽屉 → 行程编辑页。
- 行程编辑：景点新增/编辑/跨天移动/删除，天修改/新增/删除。
- 游记：TipTap 创建、保存、创建分享、海报渲染与 PNG 下载。
- 公开分享页免登录访问。
- 后台用户、卡密、城市、官方链接、汇率、内容管理、AI 配置页面可访问。
- 个人中心、卡密兑换入口、找回/重置密码页面。
- 记账新增/编辑/删除与汇总图表。
- 注册（邮箱下填验证码）→ 自动登录；找回密码；管理员入口与普通用户被拒；保持登录的存储策略；验证页与昵称已移除（`auth.spec.ts`）。

## 数据准备与清理

`global-setup.ts` 通过 API 登录管理员并创建：

- 一条 3 天东京行程（用于地图与编辑页测试）
- 一篇已分享的东京游记（用于分享页测试）

运行结束后 `global-teardown.ts` 通过 API 删除这两条数据。UI 测试中创建的游记会在用例内删除。

## 视觉验收截图

```bash
npx playwright test e2e/screens.spec.ts
```

在 `test-results/screens/` 输出登录、注册、地图、规划、游记、记账、个人中心、后台的桌面（1440）与手机（390）宽度截图，用于人工检查布局与间距。

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `E2E_API_BASE` | `http://localhost:8080` | 后端地址 |
| `E2E_FRONTEND_ORIGIN` | `http://localhost:5173` | 用于生成登录 storageState |
| `E2E_ADMIN_ACCOUNT` / `E2E_ADMIN_PASSWORD` | `admin` / `Admin@123456` | 管理员账号 |
| `E2E_BACKEND_LOG` | 无 | 后端日志路径；未设置时跳过需要验证码的用例 |
| `E2E_REDIS_CONTAINER` | `tm-redis` | 用例内清理发码限流键 |
| `E2E_MYSQL_CONTAINER` | `tm-mysql` | 备用：MySQL 容器 |
| `E2E_MYSQL_ROOT_PASSWORD` | `root` | MySQL root 密码 |
| `E2E_MYSQL_DATABASE` | `travelmapper` | 数据库名 |

`e2e/.auth.json`、`e2e/.state.json` 为运行期文件，已加入 `.gitignore`。
