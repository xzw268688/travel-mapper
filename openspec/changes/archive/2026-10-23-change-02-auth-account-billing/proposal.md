## Why

TravelMapper 的所有个人数据、AI 调用和商业规则都以“谁在操作”和“还剩多少额度”为前提。没有可用的认证、行级隔离、账号注销、额度中心和卡密兑换，change-04 的 AI 行程与 change-05 的个人内容都无法安全落地，也无法验证首月卡密转化指标。

## What Changes

- 实现注册（用户名/邮箱 + 密码、协议勾选）、邮箱验证、重发验证、登录、登出、找回密码、重置密码；密码 BCrypt 存储，JWT 有效期 7 天，无 refresh token。
- 实现基于 `token_version` 的 Token 失效：封禁、改密、重置密码、注销后旧 JWT 立即失效；请求过滤器校验用户状态与 `tv`。
- 实现角色与数据隔离：平台角色仅 `USER`/`ADMIN`，VIP 由 `vip_expire_at` 派生；用户数据越权统一返回 404，后台越权返回 403。
- 实现个人资料（昵称、预设头像）与账号注销：单事务软删除 + 匿名化，撤销分享令牌，清空次数包，保留脱敏审计。
- 实现额度中心：普通用户每日 3 次免费额度（北京时间零点重置）、VIP 有效期内无限次且受 200 次/日兜底、`quota_pack` 批次按最早过期优先扣减、`quota_ledger` 只追加流水、失败退回幂等。
- 实现卡密：管理员批量生成（≤500）、列表、CSV 导出（UTF-8 BOM）、作废（仅未使用）；用户兑换使用 `Idempotency-Key` + 分布式锁 + 条件更新，同类型直接累加，次数包落新批次。
- 实现后台用户管理：用户列表、封禁/解封、手动调整额度/VIP，全部写审计日志。
- **不包含**AI 调用与行程生成（change-04 消费本 change 的额度扣减契约），也不包含城市/旅行/地图（change-03）。

## Capabilities

### New Capabilities

- `identity/authentication`: 注册、邮箱验证、登录、登出、找回/重置密码、JWT 签发与失效、角色与行级隔离。
- `account/lifecycle`: 个人资料、预设头像、账号注销与匿名化、封禁状态下的账号行为。
- `billing/quota`: 免费额度、VIP 无限次与日上限、次数包批次/FIFO/过期、流水、额度查询、扣减与退回契约。
- `billing/card-key`: 卡密批量生成、列表、导出、作废、兑换幂等与并发控制、VIP/次数包入账。
- `admin/users`: 后台用户列表、封禁/解封、手动调整额度/VIP、相关审计。

### Modified Capabilities

无。本 change 全部为新增能力。

## Impact

- **新增接口**：`/api/auth/*`、`/api/user/profile`、`/api/user/logout-account`、`/api/quota/me`、`/api/card/redeem`、`/api/admin/users/*`、`/api/admin/cards/*`（API 文档 3、11、12.5–12.11）。
- **数据表**：`user`、`email_token`、`card_key`、`quota_pack`、`quota_ledger`、`audit_log`（结构由 change-01 提供）。
- **Redis**：免费额度计数、VIP 日计数、次数包读缓存、取消/退回标记、幂等键、卡密锁、用户状态与 `token_version` 缓存。
- **外部依赖**：邮箱 SMTP（开发期 QQ 邮箱）、BCrypt、JWT 库、AES 配置（来自 change-01）。
- **后续消费方**：change-04 必须在每次 AI 生成/润色/重新生成前调用本 change 的额度扣减契约，并在失败时按 `requestId` 退回；change-05 的注销行为依赖本 change 的匿名化清单。
- **文档依据**：SRS 3.2/3.4、BR-002/004/008、REQ-SYS-001、REQ-ADM-001；Design 7.2/7.3/7.9/7.18/7.19；API 第 1、2、3、11、12.5–12.11、15 章；DB `user`、`email_token`、`card_key`、`quota_pack`、`quota_ledger`。
- **无破坏性变更**。
