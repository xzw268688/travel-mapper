## MODIFIED Requirements

### Requirement: Login and JWT issuance

系统 MUST 允许使用用户名或邮箱登录，使用 BCrypt 比对密码；未开启二次验证时成功后签发有效期 7 天的 JWT，载荷包含 `sub`、`role`、`tv`、`jti`、`exp`。`jti` MUST 每次签发唯一，用于逐 Token 吊销。已开启二次验证的账号在密码校验通过后 MUST NOT 直接签发 JWT，而 MUST 返回有效期 5 分钟的临时凭证；客户端 MUST 再提交当前 TOTP 或一次性恢复码换取正式 JWT。临时凭证 MUST 只能用于二次验证，错误累计 5 次 MUST 立即作废。连续失败 5 次 MUST 锁定 15 分钟；封禁、已注销、邮箱未验证 MUST 分别返回 `1003`、`1007`、`1002`。

#### Scenario: Valid credentials return token and plan
- **WHEN** 用户使用正确账号密码登录且未开启二次验证
- **THEN** 系统返回 JWT、`expiresIn=604800` 和包含 `role` 与 `plan` 的用户信息，`plan` 由 VIP 到期时间实时派生

#### Scenario: Every issued token has a unique identifier
- **WHEN** 同一用户连续两次登录
- **THEN** 两个 JWT 的 `jti` 互不相同

#### Scenario: Two-factor login returns a challenge instead of a token
- **WHEN** 已开启二次验证的用户提交正确账号密码
- **THEN** 系统不返回 JWT，返回 `requires2fa=true`、5 分钟有效的临时凭证和 `expiresIn=300`

#### Scenario: Two-factor code completes login
- **WHEN** 客户端提交有效临时凭证与当前 TOTP
- **THEN** 系统签发正式 JWT，临时凭证立即作废

#### Scenario: Recovery code completes login exactly once
- **WHEN** 客户端提交有效临时凭证与未使用的恢复码
- **THEN** 登录成功，该恢复码立即标记为已使用，再次使用 MUST 失败

#### Scenario: Repeated invalid two-factor codes invalidate the challenge
- **WHEN** 同一临时凭证连续提交错误验证码 5 次
- **THEN** 临时凭证立即作废，后续即使提交正确验证码也返回 `1010`

#### Scenario: Wrong password does not reveal account existence
- **WHEN** 用户名不存在或密码错误
- **THEN** 系统统一返回 `1001`

#### Scenario: Repeated failures lock login
- **WHEN** 同一账号连续 5 次登录失败
- **THEN** 15 分钟内即使密码正确也返回锁定错误

### Requirement: Session invalidation

系统 MUST 在封禁、注销、改密、改邮箱、重置密码时使旧 JWT 失效；JWT 过滤器 MUST 校验用户状态与 `token_version`，不得仅依赖缓存过期。退出登录 MUST 服务端吊销当前 Token：该 Token 的 `jti` MUST 写入带剩余有效期 TTL 的黑名单，此后携带该 Token 的请求 MUST 返回 `1006`。系统 MUST 提供「退出所有设备」，使该用户全部已签发 JWT 立即失效。前端 MUST 在调用退出接口失败时仍然清除本地 Token。不含 `jti` 的旧 Token MUST 继续可用至过期，作为升级兼容窗口。黑名单存储不可用时，鉴权 MUST 降级为不校验黑名单并记录告警日志，MUST NOT 阻断全部已登录请求。

#### Scenario: Banned user's token is rejected immediately
- **WHEN** 管理员封禁用户后该用户继续使用旧 JWT
- **THEN** 请求返回 `1003` 或 `1006`，不能访问业务接口

#### Scenario: Logout clears session
- **WHEN** 已登录用户调用退出登录
- **THEN** 接口成功，前端清除本地 Token，该 Token 在剩余有效期内不可再用

#### Scenario: Logout revokes only the current token
- **WHEN** 用户在一台设备退出登录，随后用该设备退出前的 JWT 发起请求
- **THEN** 请求返回 `1006`；该用户在其他设备上未退出的 JWT 仍可正常使用

#### Scenario: Logout all devices revokes every token
- **WHEN** 用户调用「退出所有设备」
- **THEN** 该用户此前签发的全部 JWT（包括当前设备的）返回 `1006`，必须重新登录

#### Scenario: Frontend clears token even if logout fails
- **WHEN** 前端调用退出接口遇到网络错误或服务端错误
- **THEN** 本地 Token 仍被清除，用户回到登录页

#### Scenario: Legacy token without jti remains usable
- **WHEN** 升级前签发、未携带 `jti` 的 JWT 在有效期内访问业务接口
- **THEN** 请求按原有状态与 `token_version` 校验通过，直至该 Token 自然过期

#### Scenario: Blacklist store outage degrades safely
- **WHEN** Redis 不可用且用户携带已吊销的 JWT 发起请求
- **THEN** 系统记录告警日志，鉴权继续执行用户状态与 `token_version` 校验，不因黑名单不可用而拒绝全部请求

#### Scenario: Password change invalidates other sessions
- **WHEN** 用户修改密码成功
- **THEN** 其他设备上的旧 JWT 返回 `1006`，当前设备获得新 JWT

#### Scenario: Email change invalidates other sessions
- **WHEN** 用户修改邮箱成功
- **THEN** 其他设备上的旧 JWT 返回 `1006`，当前设备获得新 JWT

## ADDED Requirements

### Requirement: Password change

已登录用户 MUST 能修改密码：提交当前密码与新密码，新密码 MUST 为 8–32 位且同时包含字母与数字。当前密码错误 MUST 返回 `1001`；新密码不合规 MUST 返回 `2001`。修改成功后 `token_version` MUST 自增，其他设备全部下线，响应 MUST 返回当前设备的新 JWT；系统 MUST 写审计日志并向用户邮箱发送「密码已修改」通知。

#### Scenario: Successful password change rotates sessions
- **WHEN** 用户提交正确的当前密码与合规的新密码
- **THEN** 密码更新，其他设备旧 JWT 返回 `1006`，响应携带当前设备的新 JWT

#### Scenario: Wrong current password is rejected
- **WHEN** 用户提交错误的当前密码
- **THEN** 系统返回 `1001`，密码不变，其他会话保持有效

#### Scenario: Weak new password is rejected
- **WHEN** 新密码少于 8 位或不含字母/数字
- **THEN** 系统返回 `2001`，密码不变

#### Scenario: Password change notice is sent
- **WHEN** 密码修改成功
- **THEN** 用户邮箱收到密码已修改通知，审计日志记录该操作

### Requirement: Email change

已登录用户 MUST 能修改邮箱：提交当前密码、新邮箱与发送到新邮箱的 6 位验证码。系统 MUST 先校验当前密码、新邮箱唯一性，再校验验证码；新邮箱不合法 MUST 返回 `2001`，已被占用 MUST 返回 `1008`，验证码无效 MUST 返回 `1004`，当前密码错误 MUST 返回 `1001`。验证码 MUST 复用现有 10 分钟有效、一次性、5 次试错作废与发送限流规则。修改成功后 `token_version` MUST 自增，其他设备下线，响应 MUST 返回当前设备的新 JWT；系统 MUST 向旧邮箱发送变更通知，但 MUST NOT 要求旧邮箱确认；系统 MUST 写审计日志。

#### Scenario: Successful email change
- **WHEN** 用户提交正确的当前密码、未被占用的新邮箱与有效验证码
- **THEN** 邮箱更新，其他设备旧 JWT 失效，当前设备获得新 JWT

#### Scenario: Duplicate new email is rejected
- **WHEN** 新邮箱已被其他账号使用
- **THEN** 系统返回 `1008`，邮箱不变，验证码不被消耗

#### Scenario: Wrong current password blocks email change
- **WHEN** 用户提交错误的当前密码
- **THEN** 系统返回 `1001`，邮箱不变

#### Scenario: Invalid or expired code blocks email change
- **WHEN** 用户提交错误、过期或已使用的验证码
- **THEN** 系统返回 `1004`，邮箱不变

#### Scenario: Old email only receives a notice
- **WHEN** 邮箱修改成功
- **THEN** 旧邮箱收到变更通知，无需任何确认操作

### Requirement: Two-factor authentication

已登录用户 MUST 能自愿开启 TOTP 二次验证。开启流程 MUST 返回 `otpauth://` URI 与密钥供认证器绑定；密钥 MUST 加密存储，MUST NOT 以明文落库或返回给其他接口。确认绑定 MUST 校验当前 6 位 TOTP；确认成功后 MUST 标记已开启并一次性生成 10 个恢复码，恢复码 MUST 只在生成响应中明文返回一次，服务端 MUST 只存哈希，每个恢复码 MUST 只能使用一次。TOTP MUST 按 30 秒步长校验，并允许前后各一个时间窗。关闭二次验证与重新生成恢复码 MUST 同时校验当前密码与当前 TOTP；重新生成 MUST 使旧恢复码全部作废。开启、关闭、重新生成与管理员重置 MUST 向用户邮箱发送通知并写审计日志。

#### Scenario: Setup returns a bindable secret
- **WHEN** 已登录用户发起 2FA 绑定
- **THEN** 系统返回可用于生成二维码的 `otpauth://` URI 与密钥

#### Scenario: Enabling requires a valid TOTP
- **WHEN** 用户提交绑定密钥对应的当前 6 位码
- **THEN** 二次验证标记为已开启，响应返回 10 个恢复码

#### Scenario: Invalid TOTP does not enable 2FA
- **WHEN** 用户在确认绑定时提交错误验证码
- **THEN** 系统返回 `1010`，二次验证保持未开启

#### Scenario: Disabling requires password and TOTP
- **WHEN** 用户关闭二次验证
- **THEN** 必须同时提供正确的当前密码与当前 TOTP，否则返回 `1001` 或 `1010`

#### Scenario: Regenerating recovery codes invalidates old ones
- **WHEN** 用户通过密码 + TOTP 重新生成恢复码
- **THEN** 返回新的 10 个恢复码，此前所有未使用的恢复码全部失效

#### Scenario: Recovery codes are stored hashed
- **WHEN** 服务端保存恢复码
- **THEN** 数据库中只存在哈希值，不存在可还原的明文

### Requirement: Administrator two-factor enforcement

系统 MUST 提供配置开关 `app.security.require-admin-2fa`，默认 MUST 为关闭。开启时，未绑定二次验证的 `ADMIN` MUST 仍能登录并访问个人资料与 2FA 绑定接口，但访问任意 `/api/admin/**` 接口 MUST 返回 `1009`；绑定成功后 MUST 恢复访问。关闭时 MUST 不限制管理员访问。

#### Scenario: Unbound admin is blocked when enforcement is on
- **WHEN** 开关开启且未绑定 2FA 的管理员访问后台接口
- **THEN** 系统返回 `1009`，不执行业务逻辑

#### Scenario: Bound admin passes when enforcement is on
- **WHEN** 开关开启且管理员已绑定 2FA
- **THEN** 后台接口正常返回

#### Scenario: Enforcement off does not block unbound admins
- **WHEN** 开关关闭且管理员未绑定 2FA
- **THEN** 后台接口正常返回