# Identity Authentication Specification

## Purpose

建立账号身份、邮箱验证、会话签发与访问控制的统一规则，确保只有已验证且未被封禁的用户能访问本人数据，并为后续所有业务接口提供可复用的鉴权契约。

## Requirements

### Requirement: Account registration
系统 SHALL 支持用户名/邮箱 + 密码注册。用户名 MUST 为 4–20 位字母、数字或下划线且唯一；邮箱 MUST 合法且唯一；密码 MUST 为 8–32 位且同时包含字母与数字；`agreeTerms` MUST 为 `true`；昵称缺省取用户名。

#### Scenario: Successful registration sends verification email
- **WHEN** 用户提交合法的用户名、邮箱、密码并勾选协议
- **THEN** 系统创建未验证账号、发送验证邮件，并返回 `userId`、`emailSent=true` 与验证令牌过期时间

#### Scenario: Duplicate username or email is rejected
- **WHEN** 注册使用的用户名或邮箱已存在
- **THEN** 系统返回 `1008`，不创建账号、不发送邮件

#### Scenario: Terms not accepted
- **WHEN** `agreeTerms` 缺失或为 `false`
- **THEN** 系统返回 `2001`，不创建账号

### Requirement: Email verification
验证令牌 MUST 在签发后 30 分钟内有效且只能使用一次；未验证邮箱的账号 MUST NOT 能登录；同一邮箱发送验证邮件的间隔 MUST 至少 60 秒。

#### Scenario: Valid token verifies account
- **WHEN** 用户提交未使用且未过期的验证令牌
- **THEN** 账号标记为已验证，令牌标记为已使用，后续可正常登录

#### Scenario: Expired or reused token fails
- **WHEN** 用户提交已过期或已使用的验证令牌
- **THEN** 系统返回 `1004`，账号验证状态不变

#### Scenario: Unverified account cannot log in
- **WHEN** 未验证邮箱的账号提交正确密码
- **THEN** 系统返回 `1002`，不签发 JWT

#### Scenario: Resend is rate limited
- **WHEN** 同一邮箱在 60 秒内再次请求发送验证邮件
- **THEN** 系统返回 `1005`，不重复发送

### Requirement: Login and JWT issuance
系统 MUST 允许使用用户名或邮箱登录，使用 BCrypt 比对密码；成功后签发有效期 7 天的 JWT，载荷包含 `sub`、`role`、`tv`、`exp`。连续失败 5 次 MUST 锁定 15 分钟；封禁、已注销、邮箱未验证 MUST 分别返回 `1003`、`1007`、`1002`。

#### Scenario: Valid credentials return token and plan
- **WHEN** 用户使用正确账号密码登录
- **THEN** 系统返回 JWT、`expiresIn=604800` 和包含 `role` 与 `plan` 的用户信息，`plan` 由 VIP 到期时间实时派生

#### Scenario: Wrong password does not reveal account existence
- **WHEN** 用户名不存在或密码错误
- **THEN** 系统统一返回 `1001`

#### Scenario: Repeated failures lock login
- **WHEN** 同一账号连续 5 次登录失败
- **THEN** 15 分钟内即使密码正确也返回锁定错误

### Requirement: Password reset
找回密码接口 MUST 无论邮箱是否存在都返回成功，防止账号枚举；重置令牌 MUST 为 30 分钟有效的一次性令牌；重置成功后 MUST 使该用户所有已签发 JWT 立即失效。

#### Scenario: Forgot password does not disclose registration
- **WHEN** 用户为未注册邮箱请求重置
- **THEN** 接口仍返回 `emailSent=true`

#### Scenario: Reset invalidates existing sessions
- **WHEN** 用户使用有效重置令牌设置新密码
- **THEN** 密码更新，`token_version` 自增，旧 JWT 再次使用时返回 `1006`

#### Scenario: Reused reset token fails
- **WHEN** 同一重置令牌被再次使用
- **THEN** 系统返回 `1004`

### Requirement: Session invalidation
系统 MUST 在封禁、注销、改密、重置密码时使旧 JWT 失效；JWT 过滤器 MUST 校验用户状态与 `token_version`，不得仅依赖缓存过期。退出登录 MUST 至少在客户端清除 Token。

#### Scenario: Banned user's token is rejected immediately
- **WHEN** 管理员封禁用户后该用户继续使用旧 JWT
- **THEN** 请求返回 `1003` 或 `1006`，不能访问业务接口

#### Scenario: Logout clears session
- **WHEN** 已登录用户调用退出登录
- **THEN** 接口成功，前端清除本地 Token；若启用黑名单，该 Token 在剩余有效期内不可再用

### Requirement: Role and row-level isolation
平台角色 MUST 仅有 `USER` 与 `ADMIN`；VIP MUST 不是角色。用户只能访问本人数据，越权访问资源类接口 MUST 返回 404（防枚举）；普通用户访问 `/api/admin/**` MUST 返回 403（`4002`）。

#### Scenario: Cross-user access is hidden
- **WHEN** 用户 A 使用自己的 JWT 访问用户 B 的行程、游记或消费
- **THEN** 系统返回 404，不暴露资源是否存在

#### Scenario: Normal user cannot enter admin area
- **WHEN** 普通用户携带有效 JWT 调用任意后台接口
- **THEN** 系统返回 `4002`

#### Scenario: Admin does not bypass row isolation
- **WHEN** 管理员以普通业务接口访问他人行程或游记
- **THEN** 系统仍按 404 处理，后台接口只用于管理用途

### Requirement: Authentication rate limiting
注册、重发验证、找回密码 MUST 受 60 秒发信限流；登录 MUST 受失败次数与接口级限流保护。

#### Scenario: Email sending is throttled
- **WHEN** 同一邮箱 60 秒内重复触发注册验证或找回密码
- **THEN** 系统返回 `1005` 或 `5004`，不发送邮件

#### Scenario: Login endpoint is rate limited
- **WHEN** 同一来源高频调用登录接口
- **THEN** 系统返回 `5004`，不再执行密码校验
