## Purpose

记录每一次登录尝试，并为管理员提供只读的登录日志查询能力，用于追查账号盗用、撞库与异常登录。

## ADDED Requirements

### Requirement: Login attempt recording

系统 MUST 在每次调用登录接口时记录一条登录日志，包含登录账号、用户 ID（能解析时）、角色（能解析时）、结果（成功/失败）、失败原因（仅失败时）、客户端 IP、User-Agent 与创建时间。日志 MUST 只追加，MUST NOT 保存用户密码或令牌。登录日志写入 MUST NOT 改变登录结果：写入失败时登录流程 MUST 继续。

#### Scenario: Successful login is recorded
- **WHEN** 用户使用正确账号与密码登录成功
- **THEN** 系统新增一条成功日志，记录该用户 ID、角色、IP 与 User-Agent

#### Scenario: Wrong password is recorded with reason
- **WHEN** 用户使用正确账号但错误密码登录
- **THEN** 系统新增一条失败日志，失败原因为 `BAD_CREDENTIALS`

#### Scenario: Unknown account is recorded
- **WHEN** 使用不存在的账号登录
- **THEN** 系统新增一条失败日志，失败原因为 `USER_NOT_FOUND` 且用户 ID 为空

#### Scenario: Blocked account failures are classified
- **WHEN** 登录因账号封禁、账号注销、邮箱未验证或失败次数过多被拒绝
- **THEN** 失败原因分别为 `ACCOUNT_BANNED`、`ACCOUNT_DELETED`、`EMAIL_NOT_VERIFIED`、`RATE_LIMITED`

#### Scenario: Log write failure does not block login
- **WHEN** 登录日志写入发生异常
- **THEN** 登录仍按原结果成功或失败，不向前端暴露日志错误

### Requirement: Login log query

管理员 MUST 能通过只读分页接口查询登录日志，结果 MUST 按创建时间倒序。MUST 支持按账号模糊匹配、用户 ID、角色、成功/失败与时间区间筛选。每条记录 MUST 返回时间、账号、用户 ID、角色、结果、失败原因、IP 与 User-Agent。接口 MUST 仅管理员可访问，且 MUST NOT 提供修改或删除能力。

#### Scenario: Admin lists login logs
- **WHEN** 管理员打开日志管理页的登录日志页签
- **THEN** 系统按时间倒序返回分页登录日志

#### Scenario: Filter by account and result
- **WHEN** 管理员输入账号关键字并选择「失败」
- **THEN** 只返回账号匹配且结果为失败的记录

#### Scenario: Filter by user and time range
- **WHEN** 管理员指定用户 ID 与日期区间
- **THEN** 只返回该用户在区间内的登录记录

#### Scenario: Normal user cannot read login logs
- **WHEN** 普通用户调用登录日志接口
- **THEN** 系统返回 `4002`

#### Scenario: Login logs are read-only
- **WHEN** 管理员尝试修改或删除登录日志
- **THEN** 系统不存在对应接口，返回 404 或 405