## 1. 数据库与安全基础设施

- [x] 1.1 新增 Flyway `V24__account_security.sql`：`user` 增加 `totp_secret`、`totp_enabled`、`totp_confirmed_at`，创建 `user_recovery_code`（`id`、`user_id`、`code_hash`、`used_at`、`created_at`，索引 `user_id`）；运行集成测试启动 Flyway，确认迁移成功且表结构存在。
- [x] 1.2 新增 `TotpService` 与 Base32 工具：RFC 6238 + HMAC-SHA1、30 秒步长、±1 窗口；用 RFC 测试向量编写 `TotpServiceTest`，运行 `mvn -q -Dtest=TotpServiceTest test` 通过。
- [x] 1.3 扩展鉴权基础设施：`User` 实体增加 2FA 字段；`UserAuthSnapshot` 与 `UserStatusCache` 携带 `totpEnabled`；`JwtPayload` 增加 `purpose`；`JwtService` 支持签发/解析 5 分钟 `purpose=2fa` 挑战 Token；`RequestIdentityFilter` 拒绝挑战 Token 访问业务接口。补充单元测试覆盖挑战 Token 被拒。
- [x] 1.4 新增 `SecurityNoticeService`：基于现有 `EmailSender` 同步发送安全通知，异常只记日志不影响主流程；新增 `ErrorCode` 的 `1009`–`1012` 与 `AppProperties.Security.requireAdmin2fa`（环境变量 `REQUIRE_ADMIN_2FA`，默认 false）。
- [x] 1.5 在 `RequestIdentityFilter` 对 `/api/admin/**` 实施管理员 2FA 强制：开关开启、角色 `ADMIN` 且 `totpEnabled=false` 时返回 `1009`；集成测试覆盖开关开/关与已绑定/未绑定四种组合。

## 2. 改密码与改邮箱

- [x] 2.1 新增 `PUT /api/user/password`：DTO 校验当前密码与新密码规则；成功后 `token_version +1`、驱逐缓存、返回当前设备新 JWT、写审计 `USER_PASSWORD_CHANGE`、发通知。集成测试覆盖成功换签、当前密码错误、弱密码与其他设备失效。
- [x] 2.2 `EmailCodeService.Scene` 增加 `CHANGE_EMAIL`；新增登录态 `POST /api/user/email/send-code`：先校验新邮箱唯一性与格式，再复用发送限流与验证码规则；集成测试覆盖重复邮箱不消耗验证码与限流。
- [x] 2.3 新增 `PUT /api/user/email`：校验当前密码、唯一性、验证码；成功后更新邮箱、`token_version +1`、返回新 JWT、旧邮箱通知、写审计 `USER_EMAIL_CHANGE`。集成测试覆盖成功、重复邮箱、错误密码、无效验证码。

## 3. TOTP 二次验证

- [x] 3.1 新增 `POST /api/user/2fa/setup`、`POST /api/user/2fa/enable`、`POST /api/user/2fa/disable`、`POST /api/user/2fa/recovery-codes`：密钥用 `AesCryptoService` 加密存储；恢复码生成 10 个、仅返回明文一次、BCrypt 哈希入库、使用后物理删除；关闭与重生成要求密码 + TOTP。集成测试覆盖绑定、错误码不开启、恢复码一次性、关闭与重生成。
- [x] 3.2 改造登录：已开启 2FA 时返回 `requires2fa=true`、5 分钟挑战 Token、`expiresIn=300`；新增 `POST /api/auth/login/2fa`，校验挑战 Token、TOTP 或恢复码，限制 5 次尝试并防止挑战 Token 重放，成功后签发正式 JWT。集成测试覆盖挑战登录、恢复码登录、错误次数作废。
- [x] 3.3 新增 `POST /api/admin/users/{id}/2fa/reset`：清空 TOTP 与恢复码、关闭标记、`token_version +1`、驱逐缓存、写审计 `USER_2FA_RESET`、邮件通知；`AdminUserDetailView` 增加 `twoFactorEnabled`。集成测试覆盖管理员重置、普通用户 4002、未绑定用户幂等成功。

## 4. 前端

- [x] 4.1 `LoginView.vue` 支持两步登录：收到 `requires2fa` 后展示验证码输入（TOTP/恢复码），调用 `/auth/login/2fa` 并保存 JWT；`stores/auth.ts` 增加对应方法。运行 `npm run build` 通过。
- [x] 4.2 `ProfileView.vue` 账号安全区块扩展：修改密码、修改邮箱、开启/关闭 2FA、重新生成恢复码；2FA 绑定用现有 `qrcode` 库渲染二维码，恢复码仅展示一次。运行 `npm run build` 通过。
- [x] 4.3 `AdminUsersView.vue` 用户详情展示 2FA 状态，并提供「重置二次验证」按钮（二次确认）；`api/admin.ts` 增加接口。运行 `npm run build` 通过。
- [x] 4.4 前端统一处理 `1009`：管理员被要求绑定 2FA 时提示并跳转个人中心账号安全区块；运行 `npm run build` 通过。

## 5. 文档同步与整体验收

- [x] 5.1 更新 `TravelMapper API  V1.0.md`：新增改密、改邮箱、2FA、登录挑战、管理员重置接口与 `1009`–`1012` 错误码。
- [x] 5.2 更新 `TravelMapper SRS V1.0.md`：补充账号安全自助、TOTP、管理员强制开关与恢复码策略。
- [x] 5.3 更新 `TravelMapper DB  V1.0.md`：`user` 新增字段与 `user_recovery_code` 表。
- [x] 5.4 更新 `docs/ops/deployment.md` 与 `deploy/.env.prod.example`：新增 `REQUIRE_ADMIN_2FA` 说明与默认值。
- [x] 5.5 运行后端全量测试、前端 `npm run build` 与 `openspec validate account-security-self-service --strict`，全部通过。