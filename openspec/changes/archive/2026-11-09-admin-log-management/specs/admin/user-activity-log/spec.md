## Purpose

把用户侧关键业务写操作纳入操作日志，并记录操作发生时的角色快照，使管理员能够查看、筛选与追溯用户操作。

## ADDED Requirements

### Requirement: User operation recording

系统 MUST 将用户侧关键业务写操作写入 `audit_log`，动作 MUST 使用稳定的语义名称。写入 MUST 只在业务成功后发生；业务失败或被拒绝的请求 MUST NOT 记录。天、景点、清单项等子资源变更 MUST 聚合到父级用户动作，MUST NOT 为每次子资源变更单独记录。日志写入失败 MUST NOT 影响业务结果。

覆盖的动作 MUST 至少包括：
- 旅行：`TRIP_UPDATE`、`TRIP_STATUS`、`TRIP_DELETE`
- 行程：`ITINERARY_CREATE`、`ITINERARY_UPDATE`、`ITINERARY_DELETE`、`ITINERARY_GENERATE`
- 游记：`NOTE_CREATE`、`NOTE_UPDATE`、`NOTE_DELETE`、`NOTE_SHARE`、`NOTE_UNSHARE`、`NOTE_REPORT`
- 消费：`EXPENSE_CREATE`、`EXPENSE_UPDATE`、`EXPENSE_DELETE`
- 清单：`CHECKLIST_SAVE`
- 账号与反馈：`USER_PROFILE_UPDATE`、`FEEDBACK_CREATE`

`detail` MUST 只包含对象 ID、变更字段名、数量或枚举值；MUST NOT 包含标题、正文、金额、备注或联系方式原文。

#### Scenario: Saving an itinerary writes one aggregated entry
- **WHEN** 用户保存一份包含多天与多个景点的行程
- **THEN** 系统只新增一条 `ITINERARY_CREATE`，`detail` 包含天数与景点数，不为每天或每个景点单独记录

#### Scenario: Updating a note does not leak content
- **WHEN** 用户修改游记标题与可见性
- **THEN** 系统记录 `NOTE_UPDATE`，`detail` 只包含变更字段名，不包含标题或正文原文

#### Scenario: Anonymous report is recorded as anonymous
- **WHEN** 未登录用户在分享页提交游记举报
- **THEN** 系统记录 `NOTE_REPORT`，用户 ID 为空且角色为 `ANONYMOUS`

#### Scenario: Failed operation is not recorded
- **WHEN** 用户尝试修改他人的游记被拒绝
- **THEN** 系统不写入用户操作日志

#### Scenario: Log write failure does not block business
- **WHEN** 用户操作日志写入发生异常
- **THEN** 保存行程、游记、消费等业务操作仍按原结果成功，不向用户暴露日志错误

### Requirement: Actor role snapshot

每条操作日志 MUST 记录操作发生时的角色快照，取值为 `ADMIN`、`USER`、`SYSTEM` 或 `ANONYMOUS`。已登录用户的操作 MUST 记录其当时角色；无登录用户的请求 MUST 记录为 `ANONYMOUS`；无 HTTP 请求的系统写入 MUST 记录为 `SYSTEM`。角色快照 MUST NOT 因用户后续升/降级而改变。迁移 MUST 为历史记录回填角色：有用户 ID 的按用户角色回填，无用户 ID 的回填 `SYSTEM`。

#### Scenario: Admin action carries admin role
- **WHEN** 管理员封禁用户
- **THEN** 该条日志的角色快照为 `ADMIN`

#### Scenario: Role change does not rewrite history
- **WHEN** 某用户从 `USER` 升级为 `ADMIN` 后再次查看其历史操作
- **THEN** 升级前的日志仍显示 `USER`

#### Scenario: Historical rows are backfilled
- **WHEN** 数据库迁移完成
- **THEN** 既有日志均具有 `actor_role`，不再依赖用户表当前角色

### Requirement: Operation log role filtering

管理员 MUST 能按操作人角色筛选操作日志，MUST 支持「全部 / 管理员 / 用户 / 系统 / 匿名」。列表 MUST 返回并展示每条日志的角色。筛选 MUST 仅对管理员可用。

#### Scenario: Filter user operations
- **WHEN** 管理员在操作日志中选择角色「用户」
- **THEN** 只返回 `actor_role=USER` 的日志

#### Scenario: Default shows all roles
- **WHEN** 管理员首次打开操作日志页
- **THEN** 不施加角色筛选，展示全部角色的操作

#### Scenario: Normal user cannot filter operation logs
- **WHEN** 普通用户调用带 `actorRole` 的审计日志接口
- **THEN** 系统返回 `4002`