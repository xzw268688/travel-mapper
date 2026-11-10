# Identity Authentication Specification

## Purpose

建立账号身份、邮箱验证、会话签发与访问控制的统一规则，确保只有已验证且未被封禁的用户能访问本人数据，并为后续所有业务接口提供可复用的鉴权契约。

## Requirements

### Requirement: Account registration
系统 SHALL 支持用户名/邮箱 + 密码注册。用户名 MUST 为 4–20 位字母、数字或下划线且唯一；邮箱 MUST 合法且唯一；密码 MUST 为 8–32 位且同时包含字母与数字；`agreeTerms` MUST 为 `true`；昵称缺省取用户名。注册请求 MUST 携带 6 位邮箱验证码；系统 MUST 先校验用户名与邮箱唯一性，再校验验证码，只有验证码有效时才创建账号，且账号创建时 MUST 已是已验证状态。注册成功 MUST 返回 `userId`，MUST NOT 再次发送验证码，MUST NOT 返回验证码或令牌。

#### Scenario: Successful registration sends verification email
- **WHEN** 用户提交合法的用户名、邮箱、密码、未使用的有效验证码并勾选协议
- **THEN** 系统创建已激活账号并返回 `userId`，不再发送邮件

#### Scenario: Duplicate username or email is rejected
- **WHEN** 注册使用的用户名或邮箱已存在
- **THEN** 系统返回 `1008`，不创建账号，且不消耗验证码

#### Scenario: Terms not accepted
- **WHEN** `agreeTerms` 缺失或为 `false`
- **THEN** 系统返回 `2001`，不创建账号

#### Scenario: Invalid code blocks registration
- **WHEN** 用户提交错误、过期或已使用的验证码
- **THEN** 系统返回 `1004`，不创建账号

### Requirement: Email input normalization
所有接收邮箱的入口（发送验证码、注册、找回密码）SHALL 接受全角字符与空白；系统 MUST 在校验、发送验证码和提交注册/重置前按 NFKC 归一化为半角并去除空白字符。

#### Scenario: Full-width or padded email is accepted
- **WHEN** 用户以全角 `＠`、全角 `．` 或含空格的邮箱字符串发起注册或找回密码
- **THEN** 系统按半角、无空白形式校验并处理，邮箱合法时不返回「邮箱格式非法」

### Requirement: Email verification
邮箱验证 MUST 使用 6 位数字验证码，并在注册提交时校验；系统 MUST NOT 提供独立的邮箱验证接口或独立验证页面。验证码 MUST 在签发后 10 分钟内有效、只能使用一次，且同一邮箱同时 MUST 只有最新一个有效验证码；校验失败累计 5 次后该验证码 MUST 立即作废。发送验证码 MUST 允许邮箱尚未注册；对已注册且已验证的邮箱 MUST 返回成功但不实际发码。未验证邮箱的账号 MUST NOT 能登录。系统 MUST NOT 在响应中暴露验证码。

#### Scenario: Valid token verifies account
- **WHEN** 用户在注册页提交未使用的有效验证码与合法注册信息
- **THEN** 账号以已验证状态创建，该验证码立即删除

#### Scenario: Expired or reused token fails
- **WHEN** 用户提交已过期、已使用或被新验证码覆盖的验证码
- **THEN** 系统返回 `1004`，不创建账号

#### Scenario: Unverified account cannot log in
- **WHEN** 未验证邮箱的账号提交正确密码
- **THEN** 系统返回 `1002`，不签发 JWT

#### Scenario: Resend is rate limited
- **WHEN** 同一邮箱在 60 秒内再次请求发送验证码
- **THEN** 系统返回 `1005`，不重复发送

#### Scenario: Too many wrong attempts invalidate the code
- **WHEN** 同一邮箱对同一验证码连续输入错误 5 次
- **THEN** 该验证码立即作废，后续即使输入正确验证码也返回 `1004`

#### Scenario: Sending a new code replaces the old one
- **WHEN** 用户在已有有效验证码时再次请求发送
- **THEN** 旧验证码立即失效，只有新验证码可用于注册校验

#### Scenario: Code is never returned by the API
- **WHEN** 客户端调用发送验证码接口
- **THEN** 响应只包含是否已发送，不包含验证码本身

#### Scenario: Already registered email gets no code
- **WHEN** 已注册且已验证的邮箱请求 `VERIFY` 验证码
- **THEN** 接口返回成功但不实际发码

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

### Requirement: Password reset
找回密码 MUST 使用 6 位数字验证码。发送接口 MUST 无论邮箱是否存在都返回成功，防止账号枚举，且 MUST 只为存在且未被注销的账号实际生成并发送验证码。验证码 MUST 在 10 分钟内有效、只能使用一次、校验失败 5 次后作废。重置成功后 MUST 使该用户所有已签发 JWT 立即失效。

#### Scenario: Forgot password does not disclose registration
- **WHEN** 用户为未注册邮箱请求重置验证码
- **THEN** 接口仍返回成功，且不发送任何邮件

#### Scenario: Reset invalidates existing sessions
- **WHEN** 密码重置成功后旧 JWT 再次使用
- **THEN** 系统返回 `1006`，密码已更新

#### Scenario: Reused reset token fails
- **WHEN** 同一验证码被再次使用或已超过有效时间
- **THEN** 系统返回 `1004`，密码不变

#### Scenario: Valid code resets password
- **WHEN** 用户在有效期内提交正确验证码与合法新密码
- **THEN** 密码更新，验证码删除，`token_version` 自增

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
注册、重发验证、找回密码 MUST 受发信限流：同一邮箱两次发送间隔 MUST 至少 60 秒，同一邮箱每日发送 MUST NOT 超过 10 次，同一来源 IP 每小时发送 MUST NOT 超过 20 次。登录 MUST 受失败次数与接口级限流保护。

#### Scenario: Email sending is throttled
- **WHEN** 同一邮箱 60 秒内重复请求发送验证码
- **THEN** 系统返回 `1005`，不发送邮件

#### Scenario: Login endpoint is rate limited
- **WHEN** 同一来源高频调用登录接口
- **THEN** 系统返回 `5004`，不再执行密码校验

#### Scenario: Daily email cap is enforced
- **WHEN** 同一邮箱当日发送验证码已达 10 次
- **THEN** 系统返回 `1005`，不再发送

#### Scenario: Per-IP email cap is enforced
- **WHEN** 同一来源 IP 一小时内发送验证码已达 20 次
- **THEN** 系统返回 `5004`，不再发送

### Requirement: Admin sign-in entry
系统 MUST 提供独立的管理员登录入口 `/admin/login`，复用同一登录接口。登录成功但角色不是 `ADMIN` 时，前端 MUST 立即清除本地登录态并提示无管理员权限，MUST NOT 进入任何后台页面。管理员从 `/admin/login` 登录成功后 MUST 进入后台用户管理页；从普通登录页登录成功后 MUST 进入地图页。面向普通用户的页面 MUST NOT 展示管理员入口链接。

#### Scenario: Admin signs in through the admin entry
- **WHEN** 管理员在 `/admin/login` 提交正确凭据
- **THEN** 登录成功并跳转到后台用户管理页

#### Scenario: Normal user is rejected at the admin entry
- **WHEN** 普通用户在 `/admin/login` 提交正确凭据
- **THEN** 前端清除登录态并提示无管理员权限，停留在登录页或返回普通登录页

#### Scenario: Admin entry is not advertised to normal users
- **WHEN** 普通用户浏览登录页与主导航
- **THEN** 页面不出现指向 `/admin/login` 的链接

### Requirement: Session persistence choice
登录页 MUST 提供“保持登录”选项。勾选时前端 MUST 将 JWT 保存在持久存储中并在浏览器重开后保持登录；未勾选时前端 MUST 将会话保存在当前标签会话中，关闭浏览器后 MUST 要求重新登录。该选择 MUST NOT 改变服务端 JWT 的有效期。

#### Scenario: Remember me keeps session after restart
- **WHEN** 用户勾选“保持登录”并成功登录后重新打开浏览器
- **THEN** 用户仍处于登录状态

#### Scenario: Unchecked session ends with the browser
- **WHEN** 用户未勾选“保持登录”并关闭浏览器
- **THEN** 再次打开时需重新登录

#### Scenario: Server token lifetime is unchanged
- **WHEN** 用户勾选或不勾选“保持登录”
- **THEN** 服务端签发的 JWT 过期时间均为 7 天

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
