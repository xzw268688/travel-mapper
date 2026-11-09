# Admin Users Specification

## Purpose

为平台管理员提供用户检索、封禁/解封和额度调整能力，保证管理动作立即生效、可审计，并且不突破用户数据的行级隔离。

## Requirements

### Requirement: User listing
管理员 MUST 能按关键词（用户名/昵称/邮箱）和状态分页查询用户；列表 MUST 返回角色、状态、VIP 到期时间、当日免费额度用量、次数包余额和创建时间；已注销用户 MUST 仅显示账号壳（`deleted_{id}`、`已注销用户`、`DELETED`）。

#### Scenario: Search by keyword
- **WHEN** 管理员按用户名片段查询用户
- **THEN** 返回匹配用户的分页列表，字段符合约定

#### Scenario: Deleted users show only a shell
- **WHEN** 管理员查询包含已注销用户的列表
- **THEN** 该用户显示匿名化用户名、昵称与 `DELETED` 状态，不展示其内容

### Requirement: Ban and unban
管理员 MUST 能将用户状态改为 `BANNED` 或 `NORMAL`；封禁 MUST 使 `token_version` 自增并主动清除用户状态缓存，使旧 JWT 立即失效；解封 MUST 恢复登录；每次操作 MUST 写审计日志。

#### Scenario: Ban invalidates sessions immediately
- **WHEN** 管理员封禁用户并记录原因
- **THEN** 状态变为 `BANNED`，`tokenVersion` 增加，该用户下一次请求返回 `1003` 或 `1006`

#### Scenario: Unban restores access
- **WHEN** 管理员将封禁用户改回 `NORMAL`
- **THEN** 用户可以重新登录，旧 Token 仍需重新签发

#### Scenario: Ban action is audited
- **WHEN** 管理员执行封禁或解封
- **THEN** `audit_log` 记录操作人、目标用户、动作和原因

### Requirement: Admin quota and VIP adjustment endpoint
管理员 MUST 能通过后台接口调整用户 `AI_QUOTA` 或 `VIP`；`reason` MUST 必填；扣减后余额小于 0 MUST 返回 `2001`；调整 MUST 复用额度中心的入账规则并写 `quota_ledger`（`source=ADMIN`）与审计日志。

#### Scenario: Negative adjustment beyond balance is rejected
- **WHEN** 管理员扣减的次数超过用户当前可用余额
- **THEN** 系统返回 `2001`，余额不变

#### Scenario: Adjustment updates visible balance
- **WHEN** 管理员为用户增加 50 次 AI 额度
- **THEN** 用户额度查询中的 `packBalance` 增加 50，流水新增一条 `ADMIN` 记录

### Requirement: Admin-only access
所有后台用户管理接口 MUST 仅允许 `ADMIN` 角色访问；普通用户访问 MUST 返回 `4002`；管理员 MUST NOT 通过后台接口读取用户游记、行程或消费的正文内容。

#### Scenario: Normal user is rejected
- **WHEN** 普通用户调用用户列表、封禁或调额度接口
- **THEN** 系统返回 `4002`

#### Scenario: Admin cannot read personal content
- **WHEN** 管理员调用用户管理接口
- **THEN** 响应只包含账号与额度摘要，不包含任何个人内容正文

### Requirement: User detail
管理员 MUST 能查看指定用户详情：基础资料、角色与状态、当日免费额度用量、次数包余额、VIP 到期时间、游记/行程/旅行数量，以及最近的卡密兑换记录。已注销用户 MUST 只返回账号壳，不返回内容统计。详情接口 MUST 仅管理员可访问。

#### Scenario: Admin opens a user detail
- **WHEN** 管理员在用户列表点击某个用户
- **THEN** 展示额度、VIP、内容数量与兑换记录

#### Scenario: Deleted user has no content stats
- **WHEN** 管理员查看已注销用户
- **THEN** 只返回匿名化资料，不返回游记/行程统计

#### Scenario: Normal user cannot read admin detail
- **WHEN** 普通用户调用用户详情接口
- **THEN** 系统返回 `4002`

### Requirement: VIP expiry adjustment
管理员 MUST 能将用户 VIP 到期时间设置为指定时间；设置为过去时间 MUST 立即结束 VIP，设置为 `null` MUST 清除 VIP。按天数延长 MUST 继续使用现有的额度调整接口。两种操作 MUST 写审计日志，且 MUST 立即影响额度判断。

#### Scenario: Set a future expiry
- **WHEN** 管理员把用户 VIP 到期日设为下个月
- **THEN** 用户立即成为 VIP，额度按 VIP 上限计算

#### Scenario: Setting a past expiry revokes VIP
- **WHEN** 管理员把 VIP 到期日设为昨天
- **THEN** 用户立即失去 VIP 权限，审计记录该操作

#### Scenario: Extending days still works
- **WHEN** 管理员通过额度调整增加 VIP 天数
- **THEN** 到期日从「当前到期日」与「今天」中较晚者开始累加
