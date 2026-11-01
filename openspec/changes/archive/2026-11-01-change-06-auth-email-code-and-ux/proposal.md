## Why

当前认证体验与常见网站差距明显：注册验证与找回密码要求用户从邮件复制长令牌再手动粘贴；管理员与普通用户共用一个登录页；表单错误只以 toast 提示；登录页没有“保持登录”。这些细节会让首次使用者困惑，也让管理员与用户身份边界不清晰。

## What Changes

- **BREAKING** 注册与找回密码改为 6 位邮箱验证码（Redis 存哈希）；删除令牌版接口与 `resend-verify`/`forgot-password`，新增 `POST /api/auth/send-code`。
- **BREAKING** 注册改为“注册前校验验证码”：`POST /api/auth/register` 接收 `code`，校验通过才创建已激活账号；删除 `POST /api/auth/verify-email` 与独立验证页 `/verify-email`。
- 验证码规则：6 位数字、10 分钟有效、同邮箱同场景仅一个有效码、成功即删除、试错 5 次作废；重发间隔 60 秒，每邮箱每日 10 次，每 IP 每小时 20 次。
- 注册页在邮箱下方直接填写验证码（发送按钮 + 60 秒倒计时）；注册成功后前端自动登录并进入地图。
- 昵称输入框从注册页移除，后端 `nickname` 仍为可选，缺省取用户名。
- 找回密码合并为 `/reset-password` 单页三步；`/forgot-password` 重定向到该页。
- 新增独立管理员登录页 `/admin/login`，复用同一登录接口，登录后校验管理员角色；非管理员清除登录态并提示；登录成功跳用户管理页。
- 普通页面不展示管理员入口。
- 登录页新增“保持登录”：勾选存 `localStorage`，不勾存 `sessionStorage`；JWT 有效期仍为 7 天。
- 认证页统一视觉：浅灰背景、居中卡片、品牌标题；错误改为字段级内联 + 表单顶部关键错误。
- 开发环境（`MAIL_ENABLED=false`）验证码只打印后端日志，不写入接口响应、不写入数据库明文。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `identity/authentication`: 邮箱验证从“一次性令牌”改为“6 位验证码”；找回密码从“一次性令牌”改为“验证码 + 新密码”；补充验证码发送与试错限流、管理员登录入口、会话保持方式三项行为。

## Impact

- **后端**：`AuthController`、`AuthService`、`EmailTokenService`（替换为 Redis 验证码服务）、邮件正文、`ErrorCode` 中 `1004` 文案、限流组件；新增验证码相关单元与集成测试。
- **前端**：`LoginView`、`RegisterView`、`VerifyEmailView`、`ResetPasswordView`、`ForgotPasswordView`（改为重定向）、新增 `AdminLoginView`、`api/auth.ts`、`stores/auth.ts`、路由守卫、认证页样式、E2E。
- **接口契约**：`POST /api/auth/send-code`、`POST /api/auth/verify-email`、`POST /api/auth/reset-password` 请求体变化；注册响应移除 `verifyExpireAt`。
- **文档**：API 第 3 章与第 15/16 章、SRS 验收标准、Design 认证流程、`frontend/README.md`、`frontend/e2e/README.md`。
- **数据**：不改表结构；验证码只存 Redis。
- **不涉及**：手机号/短信、第三方登录、图形验证码、管理员独立登录接口或独立会话。