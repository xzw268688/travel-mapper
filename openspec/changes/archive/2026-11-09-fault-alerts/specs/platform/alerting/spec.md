## Purpose

在接口出现 5xx 或数据库、Redis、对象存储不可用时，通过飞书群机器人主动通知管理员，并避免重复告警刷屏。

## ADDED Requirements

### Requirement: Webhook alert delivery

系统 MUST 支持通过飞书群机器人 Webhook 发送纯文本告警。Webhook 地址 MUST 只从配置读取，MUST NOT 写入代码或仓库。发送 MUST 异步进行且尽力而为：Webhook 未配置、发送失败或超时 MUST NOT 影响业务请求，MUST NOT 把 Webhook 地址写入日志。

#### Scenario: Configured webhook receives an alert
- **WHEN** 系统触发一次告警且 `ALERT_WEBHOOK_URL` 已配置
- **THEN** 飞书群收到包含标题、详情与时间的文本消息

#### Scenario: Missing webhook degrades to logging
- **WHEN** `ALERT_WEBHOOK_URL` 为空
- **THEN** 系统只写日志，不发送外部请求，也不影响业务

#### Scenario: Webhook failure does not break the request
- **WHEN** Webhook 请求超时或返回错误
- **THEN** 业务请求仍按原结果返回，系统只记录告警发送失败

### Requirement: 5xx error alerts

未捕获的 5xx 接口异常 MUST 触发告警；4xx 业务错误与参数校验失败 MUST NOT 触发告警。同类告警 MUST 在最小间隔内去重，默认 300 秒。

#### Scenario: Unexpected server error alerts
- **WHEN** 接口抛出未捕获异常并返回 500
- **THEN** 系统发送一条包含接口路径与 traceId 的告警

#### Scenario: Validation error does not alert
- **WHEN** 用户提交非法参数返回 400
- **THEN** 系统不发送告警

#### Scenario: Duplicate alerts are suppressed
- **WHEN** 同一类 5xx 错误在 300 秒内连续出现
- **THEN** 系统只发送第一条告警

### Requirement: Dependency health alerts

系统 MUST 按周期检查 MySQL、Redis 与对象存储的连通性，默认每 5 分钟一次。MUST 只在依赖从正常变为异常、或从异常恢复为正常时告警，MUST NOT 在持续异常期间重复告警。对象存储未配置时 MUST 跳过检查。

#### Scenario: Database outage alerts once
- **WHEN** MySQL 从可用变为不可用
- **THEN** 系统发送一次数据库异常告警

#### Scenario: Ongoing outage does not repeat
- **WHEN** MySQL 持续不可用且后续健康检查仍失败
- **THEN** 系统不重复发送数据库异常告警

#### Scenario: Recovery is reported
- **WHEN** Redis 从不可用恢复为可用
- **THEN** 系统发送一次恢复告警

#### Scenario: Unconfigured storage is skipped
- **WHEN** 对象存储未配置
- **THEN** 健康检查跳过对象存储，不产生告警

### Requirement: Alert configuration

告警开关、Webhook、最小间隔与健康检查 Cron MUST 可通过环境变量配置：`ALERT_ENABLED`、`ALERT_WEBHOOK_URL`、`ALERT_MIN_INTERVAL_SECONDS`、`ALERT_HEALTH_CRON`。默认值 MUST 保证未配置 Webhook 时不发送任何外部请求。

#### Scenario: Alerting can be disabled
- **WHEN** `ALERT_ENABLED=false`
- **THEN** 系统不发送任何 Webhook 请求

#### Scenario: Health cron can be overridden
- **WHEN** 运维设置 `ALERT_HEALTH_CRON`
- **THEN** 健康检查按新的 Cron 执行