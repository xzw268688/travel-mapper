## MODIFIED Requirements

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

## ADDED Requirements

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