## Context

当前登录流程是单因子：`AuthController` 只有注册/发码/登录/重置/登出，`UserController` 只有资料读写与注销；`User` 表没有 2FA 字段。`EmailCodeService` 已提供 6 位验证码的签发、HMAC 存储、10 分钟 TTL、5 次试错作废与三层发送限流，可直接复用。`AesCryptoService` 提供 AES-GCM 加解密，适合保存 TOTP 密钥。`UserStatusCache`/`RequestIdentityFilter` 已具备每请求校验用户状态与 `token_version` 的能力，是管理员 2FA 强制的天然落点。

现有规格见 `openspec/specs/identity/authentication`、`admin/users`、`platform/persistence`；上一个 change 已为 JWT 增加 `jti` 与登出吊销。

## Goals / Non-Goals

**Goals：**
- 登录态下可自助修改密码与邮箱，并使其他会话立即失效。
- 提供 TOTP 2FA 与一次性恢复码，管理员可通过配置强制开启。
- 管理员可重置用户 2FA，避免用户永久锁死。
- 所有敏感操作可审计、可通知，且不引入新的第三方依赖。

**Non-Goals：**
- 不做短信、Passkey/WebAuthn、多设备信任或设备会话列表。
- 不做旧邮箱确认链接；旧邮箱只收通知。
- 不改变 JWT 7 天有效期，不引入 refresh token。
- 不为普通用户强制 2FA；管理员强制默认关闭。

## Decisions

### 1. TOTP 自实现，不引入新依赖

在 `common/security` 新增 `TotpService`：RFC 6238 + HMAC-SHA1，30 秒步长，校验允许前后各一个时间窗；配套一个最小 Base32 编解码工具。用 RFC 6238 测试向量覆盖。

- 为什么不加第三方库：后端依赖已固定，TOTP 算法是稳定标准，自实现约百行且可完全测试；避免为一个小组件引入供应链依赖。
- 为什么 TOTP 而不是邮箱验证码：项目 SMTP 是同步等待（最坏 20 秒），邮箱被攻破时 2FA 也会失效；TOTP 离线、即时、标准。
- 为什么允许 ±1 窗口：吸收客户端时钟误差，代价是理论有效窗口扩大到 90 秒，配合登录限流可接受。

### 2. 密钥加密存储，恢复码哈希存储

`user.totp_secret` 保存 `AesCryptoService.encrypt(secret)` 的密文；`user.totp_enabled` 标记是否已确认；`user.totp_confirmed_at` 记录确认时间。恢复码使用 32 字符表生成 10 位随机码（去除易混淆字符），展示为 `XXXX-XXXX`；入库前统一去分隔符、转大写，再用现有 `PasswordEncoder`（BCrypt）存哈希。每个恢复码使用成功即删除该行，保证一次性。

- 为什么不存明文密钥：数据库泄露时攻击者可直接生成 TOTP；AES 密钥在环境变量中，与数据库分离。
- 为什么恢复码用 BCrypt：高熵码 + 现有编码器即可，避免新增 HMAC 密钥管理；10 条候选逐个比对，登录场景耗时可接受。
- 已知限制：`TRAVELMAPPER_AES_KEY` 轮换会导致所有 TOTP 绑定失效，需要用户重新绑定；文档中注明。

### 3. 登录两步：短期挑战 Token

`POST /api/auth/login` 密码通过后：

- 未开启 2FA：保持现状，直接返回 JWT。
- 已开启 2FA：返回 `{ requires2fa: true, challengeToken, expiresIn: 300 }`，不返回 JWT、不返回用户资料。

挑战 Token 复用 JWT 签名密钥，载荷含 `sub`、`tv`、`jti`、`purpose=2fa`、5 分钟 `exp`。`JwtPayload` 增加 `purpose` 字段；`RequestIdentityFilter` 拒绝带 `purpose` 的 Token 访问业务接口。`POST /api/auth/login/2fa` 校验 `purpose=2fa`、用户状态与 `tv`，用 Redis 记录 `auth:2fa:attempt:<jti>`（5 次/300 秒）与 `auth:2fa:used:<jti>`，成功后签发正式 JWT。

- 为什么不用临时数据库表：挑战是短期状态，Redis 天然带 TTL，和现有验证码/限流一致。
- 为什么不把密码校验结果直接放 Cookie/Session：项目坚持无状态 JWT，挑战 Token 只是 5 分钟的受限 JWT。

### 4. 改密/改邮箱：当前设备续签，其他设备下线

两个接口都要求当前密码；成功后 `token_version +1`、驱逐 `UserStatusCache`，并在同一响应中签发新 JWT（`{ token, expiresIn }`），前端替换本地 Token，避免用户刚改完就被踢。改邮箱额外要求新邮箱验证码，新增 `EmailCodeService.Scene.CHANGE_EMAIL`；发送接口要求登录态，先查重再发码，复用现有发送限流。旧邮箱只发通知，不阻塞。

- 为什么不要求旧邮箱确认：大量用户旧邮箱已失效，强制确认会让账号永久绑死；通知足以让原主人发现异常。
- 为什么改密/改邮箱要踢其他设备：这两个动作通常发生在「怀疑泄露」场景，保留旧会话会削弱止损效果。

### 5. 2FA 生命周期接口

- `POST /api/user/2fa/setup`：生成新密钥（覆盖未确认的旧密钥），返回 `otpauth://totp/...` 与密钥；密钥加密写入 `totp_secret`，`totp_enabled=false`。
- `POST /api/user/2fa/enable`：校验当前 TOTP；成功后 `totp_enabled=true`，删除旧恢复码并生成 10 个新恢复码，明文只在本次响应返回。
- `POST /api/user/2fa/disable`：要求当前密码 + TOTP；清空密钥、关闭标记、删除全部恢复码。
- `POST /api/user/2fa/recovery-codes`：要求当前密码 + TOTP；删除旧码并返回新码。
- 所有成功操作写审计（`USER_2FA_ENABLE`/`USER_2FA_DISABLE`/`USER_2FA_RECOVERY_REGENERATE`）并发送邮件通知。

### 6. 管理员强制与重置

配置 `app.security.require-admin-2fa`（环境变量 `REQUIRE_ADMIN_2FA`，默认 false）。`RequestIdentityFilter` 在解析 JWT 后，若请求为 `/api/admin/**`、开关开启、角色为 `ADMIN` 且快照 `totpEnabled=false`，返回新错误码 `1009`。`UserAuthSnapshot` 增加 `totpEnabled` 字段。管理员仍可访问 `/api/user/profile` 与 2FA 接口完成绑定。

新增 `POST /api/admin/users/{id}/2fa/reset`：清空密钥与恢复码、关闭 2FA、`token_version +1`、驱逐缓存、写审计 `USER_2FA_RESET`、邮件通知用户。后台用户详情增加 `twoFactorEnabled` 字段。

- 为什么在过滤器而不是 AdminInterceptor：过滤器已拿到用户快照，能覆盖所有 `/api/admin/**`，包括未来新增控制器；拦截器只做角色判断，不重复查库。
- 为什么默认关闭：项目会直接部署给学校查看，管理员强制会阻碍演示；生产可按需通过环境变量开启。

### 7. 邮件通知同步发送、失败不影响主流程

新增 `SecurityNoticeService`，复用 `EmailSender` 同步发送安全通知，所有异常只记日志，不影响密码/邮箱/2FA 操作结果。测试使用 `CapturingEmailSender` 断言通知内容。

- 代价：SMTP 慢时敏感操作可能等待至 SMTP 超时；这些操作低频，且配置了连接/读/写超时，权衡后接受。
- 备选异步发送需要额外的可观测性与测试等待，收益不足以覆盖复杂度。

### 8. 错误码

- `1009 TWO_FACTOR_REQUIRED`（403）：管理员未绑定 2FA。
- `1010 TWO_FACTOR_INVALID`（401）：挑战凭证或 TOTP/恢复码无效。
- `1011 TWO_FACTOR_ALREADY_ENABLED`（409）：重复开启。
- `1012 TWO_FACTOR_NOT_ENABLED`（409）：对未开启账号执行关闭/重生成。
- 当前密码错误复用 `1001`；邮箱占用复用 `1008`；验证码无效复用 `1004`；参数问题复用 `2001`。

## Risks / Trade-offs

- [TOTP 密钥随 AES 密钥轮换失效] → 文档注明轮换需通知用户重新绑定；管理端可重置 2FA 兜底。
- [恢复码丢失 + 手机丢失导致锁死] → 管理员重置接口 + 邮件通知；后台用户详情可见 2FA 状态。
- [管理员强制开关误开导致无法进后台] → 默认关闭；管理员仍可登录并访问个人中心绑定；无法绑定时可通过环境变量关闭开关重启。
- [挑战 Token 被重放] → 成功后写 `auth:2fa:used:<jti>`，5 分钟内拒绝复用；错误 5 次作废。
- [恢复码暴力尝试] → 10 位高熵码 + 挑战凭证 5 次上限 + 登录接口限流。
- [同步通知拖慢请求] → SMTP 已配置超时；通知失败不影响主流程，仅记录日志。
- [邮箱变更后旧邮箱被他人控制] → 旧邮箱只收通知；账号安全依赖当前密码 + 新邮箱验证码。

## Migration Plan

1. 先执行 `V24__account_security.sql`：`user` 增加 2FA 字段，创建 `user_recovery_code` 表。迁移只新增列/表，不改变既有数据。
2. 部署后端：默认关闭管理员强制；现有用户未绑定 2FA，登录行为不变。
3. 部署前端：登录页按 `requires2fa` 分支；个人中心出现账号安全区块。
4. 如需强制管理员 2FA：管理员先绑定，再设置 `REQUIRE_ADMIN_2FA=true` 并重启。
5. 回滚：回滚代码即可；新增列/表可保留，旧代码忽略它们。已生成的 TOTP 密钥与恢复码在回滚后失效，重新部署新版本仍可继续使用。