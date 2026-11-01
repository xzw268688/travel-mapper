## 1. 验证码服务（后端）

- [x] 1.1 实现 Redis 验证码服务：`auth:code:{scene}:{email}` 存 HMAC 摘要与尝试次数，TTL 600 秒，重发覆盖旧码；单元测试覆盖“重发使旧码失效”
- [x] 1.2 用 Lua 脚本实现原子校验：命中即删除；未命中累计次数，达到 5 次立即删除并返回失败；单元测试覆盖并发下的次数上限
- [x] 1.3 实现三层发信限流（60 秒间隔、每邮箱每日 10 次、每 IP 每小时 20 次），按 Asia/Shanghai 计算日期；测试覆盖三种超限返回 `1005`/`5004`
- [x] 1.4 实现验证码生成与发信：6 位数字；`MAIL_ENABLED=false` 时以 `[auth-dev] scene=... email=... code=...` 打印日志且不发送邮件；测试断言生产配置下不打印验证码
- [x] 1.5 更新 `ErrorCode` 中 `1004` 文案为“验证码无效或已过期”，并确认没有其他调用方依赖旧文案；编译与现有测试通过

## 2. 认证接口改造（后端）

- [x] 2.1 新增 `POST /api/auth/send-code {email, scene}`：无论邮箱是否存在都返回成功，仅对存在且未注销账号实际发码；集成测试覆盖“未注册邮箱不泄露”
- [x] 2.2 改造 `POST /api/auth/verify-email` 为 `{email, code}`：成功置为已验证并删除验证码；错误/过期返回 `1004`；集成测试覆盖成功、错误、过期、超次
- [x] 2.3 改造 `POST /api/auth/reset-password` 为 `{email, code, newPassword}`：成功更新密码、`token_version` 自增并删除验证码；集成测试覆盖旧 JWT 失效（`1006`）
- [x] 2.4 删除 `POST /api/auth/resend-verify` 与令牌版校验逻辑，移除对 `email_token` 的读写；回归测试确认无残留调用
- [x] 2.5 注册响应改为 `{userId, emailSent}`，移除 `verifyExpireAt`；集成测试同步更新
- [x] 2.6 更新后端认证测试：注册 → 发码 → 验证 → 登录、找回密码全链路通过，`mvn test` 全绿

## 3. 前端认证页改造

- [x] 3.1 抽出认证页统一布局组件（浅灰背景、居中卡片、品牌标题），四个认证页复用；构建通过
- [x] 3.2 登录页：字段级内联校验、表单顶部显示账号/密码错误、新增“保持登录”勾选；构建通过
- [x] 3.3 实现会话存储策略：勾选写 `localStorage`、不勾写 `sessionStorage`，登录/退出时清理另一处；E2E 覆盖“勾选后重开仍登录”
- [x] 3.4 注册页：保持字段不变，改为字段级校验；注册成功后跳转 `/verify-email?email=...` 并自动发送一次验证码；E2E 覆盖
- [x] 3.5 验证页改为单页：邮箱预填、发送按钮 60 秒倒计时、验证码输入、成功后跳登录；E2E 覆盖
- [x] 3.6 找回密码合并为 `/reset-password` 单页三步，`/forgot-password` 重定向；E2E 覆盖重置后旧密码失效、新密码可登录
- [x] 3.7 `api/auth.ts` 更新为 `sendCode`、`verifyEmail(email, code)`、`resetPassword(email, code, newPassword)`，删除旧函数与 `resendVerify`；构建通过

## 4. 管理员登录入口

- [x] 4.1 新增 `/admin/login` 页面，复用登录接口；登录后校验角色，非管理员清除登录态并显示内联错误；E2E 覆盖普通用户被拒
- [x] 4.2 管理员在该页登录成功跳转 `/admin/users`；普通登录页仍跳地图；E2E 覆盖两条跳转
- [x] 4.3 确认普通用户页面不出现指向 `/admin/login` 的链接；E2E 断言登录页与主导航无该链接

## 5. 测试与文档

- [x] 5.1 E2E 支持 `E2E_BACKEND_LOG` 读取验证码；未设置时跳过注册验证用例并打印原因；更新 `frontend/e2e/README.md`
- [x] 5.2 运行后端全量测试、`npm run build`、`npm run test:e2e`，全部通过并记录结果
- [x] 5.3 更新 API 文档第 3 章（接口、请求体、响应、错误码）与第 15/16 章；更新 SRS 验收标准与 Design 认证流程
- [x] 5.4 更新 `frontend/README.md`、`docs/ops/local-development.md`：验证码获取方式、管理员入口、旧令牌接口已移除
- [x] 5.5 运行 `openspec validate --strict` 并确认 change-06 可归档
## 6. 注册页内验证码调整

- [x] 6.1 注册接口增加 `code`：先查重、再校验验证码，通过后创建已激活账号并返回 `{userId}`；验证码错误返回 `1004`，重复返回 `1008` 且不消耗验证码
- [x] 6.2 删除 `POST /api/auth/verify-email` 与其 DTO；`send-code(VERIFY)` 允许未注册邮箱发码，已注册且已验证的邮箱返回成功但不发码
- [x] 6.3 注册页在邮箱下方提供验证码输入与“发送验证码”按钮（60 秒倒计时）；移除昵称输入框
- [x] 6.4 删除 `/verify-email` 页面与路由，移除登录页/找回密码页的验证链接；注册成功后自动登录并进入地图
- [x] 6.5 更新后端测试、E2E、API/SRS/Design 与 README，并跑通全量测试（后端 143、E2E 14）
