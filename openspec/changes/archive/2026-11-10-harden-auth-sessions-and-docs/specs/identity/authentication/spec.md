## MODIFIED Requirements

### Requirement: Login and JWT issuance

系统 MUST 允许使用用户名或邮箱登录，使用 BCrypt 比对密码；成功后签发有效期 7 天的 JWT，载荷包含 `sub`、`role`、`tv`、`jti`、`exp`。`jti` MUST 每次签发唯一，用于逐 Token 吊销。连续失败 5 次 MUST 锁定 15 分钟；封禁、已注销、邮箱未验证 MUST 分别返回 `1003`、`1007`、`1002`。

#### Scenario: Valid credentials return token and plan
- **WHEN** 用户使用正确账号密码登录
- **THEN** 系统返回 JWT、`expiresIn=604800` 和包含 `role` 与 `plan` 的用户信息，`plan` 由 VIP 到期时间实时派生

#### Scenario: Every issued token has a unique identifier
- **WHEN** 同一用户连续两次登录
- **THEN** 两个 JWT 的 `jti` 互不相同

#### Scenario: Wrong password does not reveal account existence
- **WHEN** 用户名不存在或密码错误
- **THEN** 系统统一返回 `1001`

#### Scenario: Repeated failures lock login
- **WHEN** 同一账号连续 5 次登录失败
- **THEN** 15 分钟内即使密码正确也返回锁定错误

### Requirement: Session invalidation

系统 MUST 在封禁、注销、改密、重置密码时使旧 JWT 失效；JWT 过滤器 MUST 校验用户状态与 `token_version`，不得仅依赖缓存过期。退出登录 MUST 服务端吊销当前 Token：该 Token 的 `jti` MUST 写入带剩余有效期 TTL 的黑名单，此后携带该 Token 的请求 MUST 返回 `1006`。系统 MUST 提供「退出所有设备」，使该用户全部已签发 JWT 立即失效。前端 MUST 在调用退出接口失败时仍然清除本地 Token。不含 `jti` 的旧 Token MUST 继续可用至过期，作为升级兼容窗口。黑名单存储不可用时，鉴权 MUST 降级为不校验黑名单并记录告警日志，MUST NOT 阻断全部已登录请求。

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