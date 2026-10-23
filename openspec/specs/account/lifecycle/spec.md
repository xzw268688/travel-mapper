# Account Lifecycle Specification

## Purpose

定义个人资料的可见与可改范围、账号封禁状态下的行为，以及注销时跨表软删除与匿名化的完整清单，确保注销后个人数据不可检索且审计仍可追溯。

## Requirements

### Requirement: Profile read and update
已登录用户 MUST 能读取本人资料；可修改字段 MUST 仅限昵称与预设头像。昵称 MUST 为 1–20 字；头像 MUST 属于 `preset_01`～`preset_12`；用户名与邮箱 MUST NOT 通过资料接口修改。

#### Scenario: Profile returns quota summary
- **WHEN** 用户读取本人资料
- **THEN** 返回 `id`、`username`、`email`、`nickname`、`avatarPreset`、`role`、`vipExpireAt`、`createdAt` 和额度摘要

#### Scenario: Invalid nickname or avatar is rejected
- **WHEN** 用户提交空昵称或不在预设集合内的头像
- **THEN** 系统返回 `2001`，资料保持不变

#### Scenario: Username and email are immutable
- **WHEN** 更新请求携带新的用户名或邮箱
- **THEN** 这些字段被忽略或拒绝，原值不变

### Requirement: Account deletion preconditions
注销 MUST 要求再次输入正确密码，且 `confirmText` 必须精确等于 `注销账号`；`ADMIN` 角色 MUST NOT 允许注销；注销 MUST 在单个事务内完成，任一失败整体回滚。

#### Scenario: Wrong password blocks deletion
- **WHEN** 用户提交错误密码或错误确认文案
- **THEN** 系统返回 `1001` 或 `2001`，账号与数据不变

#### Scenario: Admin cannot delete own account
- **WHEN** 管理员调用注销接口
- **THEN** 系统返回 `4002`，不执行任何删除

#### Scenario: Failure rolls back everything
- **WHEN** 注销事务中任一表处理失败
- **THEN** 所有已执行的软删除与匿名化全部回滚，账号仍可正常使用

### Requirement: Deletion anonymization checklist
注销成功后，系统 MUST 按以下规则处理：`user` 改为 `status=DELETED`、`deleted=1`、用户名 `deleted_{id}`、邮箱 `deleted_{id}@deleted.local`、昵称“已注销用户”、清空密码与 VIP；`email_token` 全部置已使用；`trip`、`itinerary`、`travel_note`、`expense`、`trip_checklist` 软删除并匿名化文本；`note_share` 全部撤销；`quota_pack` 物理删除；`quota_ledger` 与 `audit_log` 保留并脱敏。

#### Scenario: Personal content is no longer searchable
- **WHEN** 账号注销后管理员在后台检索该用户内容
- **THEN** 行程、游记、消费、旅行均不可检索；用户列表仅显示已注销账号壳

#### Scenario: Shares are immediately invalid
- **WHEN** 账号注销后访问该用户此前的分享链接
- **THEN** 分享页返回 `2007`“分享已失效”

#### Scenario: Quota assets are removed
- **WHEN** 账号注销完成
- **THEN** 该用户的 `quota_pack` 批次被物理删除，`quota_ledger` 仍保留用于审计

#### Scenario: Audit remains traceable
- **WHEN** 注销后查询审计日志
- **THEN** 相关记录仍存在，但详情已脱敏，不包含昵称、邮箱或正文

### Requirement: Post-deletion access
注销后登录 MUST 返回 `1007`；携带旧 JWT 的请求 MUST 返回 `1007`；地图统计 MUST 不再计入该用户数据；注销 MUST 不可恢复。

#### Scenario: Deleted account cannot log in
- **WHEN** 已注销账号使用原用户名和密码登录
- **THEN** 系统返回 `1007`

#### Scenario: Old token is rejected
- **WHEN** 注销前的 JWT 在注销后继续使用
- **THEN** 系统返回 `1007`，不执行业务逻辑

#### Scenario: Deletion is irreversible
- **WHEN** 用户或管理员尝试恢复已注销账号
- **THEN** 系统不提供恢复入口，账号保持注销状态

### Requirement: Banned account behavior
被封禁账号 MUST 无法登录或访问需鉴权接口，返回 `1003`；解封后 MUST 恢复登录，且封禁期间不得修改任何业务数据。

#### Scenario: Banned user is blocked everywhere
- **WHEN** 状态为 `BANNED` 的用户使用有效 JWT 访问任意需鉴权接口
- **THEN** 系统返回 `1003`

#### Scenario: Unban restores access
- **WHEN** 管理员将用户状态改回 `NORMAL`
- **THEN** 用户可重新登录并访问本人数据
