Triage: ready-for-agent

## Why

当前账号只有注册、登录和「忘记密码」三条路径：登录后无法修改密码，也无法更换邮箱；除了密码没有任何第二因子。密码一旦泄露，攻击者可以长期占用账号；用户怀疑泄露时也无法自助止损。需要补齐登录态下的账号安全自助能力，并为管理员提供强制二次验证的开关与兜底重置手段。

## What Changes

- **修改密码**：登录用户提交当前密码 + 新密码（8–32 位、字母与数字）。成功后 `token_version` 自增，其他设备全部下线，当前设备换发新 JWT；写入审计并给邮箱发「密码已修改」通知。
- **修改邮箱**：登录用户提交当前密码 + 新邮箱 + 新邮箱验证码（新增 `CHANGE_EMAIL` 场景）。新邮箱 MUST 唯一；成功后更新邮箱、使其他设备下线、当前设备换发新 JWT；旧邮箱只收通知，不要求确认；写入审计。
- **TOTP 二次验证（2FA）**：用户可自愿开启。绑定流程返回 `otpauth://` URI 与密钥，前端扫码后输入 6 位码确认；开启时一次性生成 10 个恢复码（仅展示一次，加密/哈希入库，用后作废）。开启后登录改为两步：密码通过后返回 5 分钟有效的临时凭证，再提交 TOTP 或恢复码换取正式 JWT。关闭 2FA、重新生成恢复码 MUST 同时校验当前密码与 TOTP。
- **管理员强制开关**：新增配置 `app.security.require-admin-2fa`，默认关闭。开启时未绑定 2FA 的管理员登录后仍可进入个人中心绑定，但访问 `/api/admin/**` MUST 返回专用错误码；该开关通过环境变量控制，演示/生产可分别配置。
- **管理员重置用户 2FA**：后台用户详情展示 2FA 状态，并提供「重置二次验证」按钮；重置后清除 TOTP 与恢复码、使该用户会话失效、写审计并邮件通知用户。
- **数据库迁移 `V24`**：`user` 表新增 `totp_secret`（AES 加密）、`totp_enabled`、`totp_confirmed_at`；新增 `user_recovery_code` 表。
- 更新 OpenSpec 规格、根目录 API/SRS/DB 文档与部署说明；补充后端集成测试与前端账号安全界面。

不做：短信验证、Passkey/WebAuthn、多设备信任、设备会话列表、邮箱变更的旧邮箱确认链接、2FA 强制覆盖普通用户。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `identity/authentication`: 登录增加 2FA 分支；新增修改密码、修改邮箱、TOTP 开启/关闭/恢复码要求；改密与改邮箱纳入会话失效范围；管理员 2FA 强制。
- `admin/users`: 用户详情暴露 2FA 状态；新增管理员重置用户 2FA 的接口与审计要求。
- `platform/persistence`: 业务表清单新增 `user_recovery_code` 与 `user` 的 2FA 字段；删除策略声明新表不依赖软删除。

## Impact

- 后端：`AuthController`/`AuthService`（登录 2FA 分支、改密、改邮箱）、新增 `TotpService` 与 Base32 工具、`UserController`/`UserAccountSecurityService`、`AdminUserController`/`AdminUserService`（重置 2FA）、`EmailCodeService.Scene` 增加 `CHANGE_EMAIL`、`AppProperties.Security` 增加 `requireAdmin2fa`、`ErrorCode` 增加 2FA 专用错误码、Flyway `V24__account_security.sql`。
- API：`POST /api/auth/login/2fa`、`PUT /api/user/password`、`POST /api/user/email/send-code`、`PUT /api/user/email`、`POST /api/user/2fa/setup`、`POST /api/user/2fa/enable`、`POST /api/user/2fa/disable`、`POST /api/user/2fa/recovery-codes`、`POST /api/admin/users/{id}/2fa/reset`。
- 前端：`api/auth.ts`、`api/user.ts`、`api/admin.ts`、`stores/auth.ts`、`views/LoginView.vue`、`views/ProfileView.vue`（账号安全区块）、`views/AdminUsersView.vue`（2FA 状态与重置）、`api/types.ts`。
- 文档：`TravelMapper API  V1.0.md`、`TravelMapper SRS V1.0.md`、`TravelMapper DB  V1.0.md`、`docs/ops/deployment.md`、`deploy/.env.prod.example`。
- 测试：新增 `AccountSecurityIntegrationTest`、`TotpServiceTest`；扩展 `AuthIntegrationTest` 与 `AdminUserIntegrationTest`/`MonitoringIntegrationTest` 相邻的管理端用例。