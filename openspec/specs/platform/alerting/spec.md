# platform/alerting Specification

## Purpose
在接口出现 5xx 或数据库、Redis、对象存储不可用时，通过飞书群机器人主动通知管理员，并避免重复告警刷屏。

## Requirements

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

告警开关、Webhook 与最小间隔 MUST 可在管理端「配置 → 故障提醒」中配置并持久化到 `sys_config`。系统 MUST 优先读取数据库配置，数据库缺失时回退到 `ALERT_ENABLED`、`ALERT_WEBHOOK_URL`、`ALERT_MIN_INTERVAL_SECONDS`；保存后 MUST 立即生效，无需重启后端。Webhook MUST 使用 AES 加密存储并在页面掩码显示，留空保存 MUST 保留原值。Webhook URL MUST 仅接受飞书 HTTPS 地址（`open.feishu.cn` 或 `open.larksuite.com` 的 `/open-apis/bot/v2/hook/...`）。健康检查 Cron MUST 继续通过 `ALERT_HEALTH_CRON` 配置。数据库不可用时，配置读取 MUST 回退到环境变量，MUST NOT 因配置读取失败而无法告警。

#### Scenario: Alerting can be disabled
- **WHEN** 管理员在配置页关闭故障提醒
- **THEN** 系统不发送任何 Webhook 请求

#### Scenario: Health cron can be overridden
- **WHEN** 运维设置 `ALERT_HEALTH_CRON`
- **THEN** 健康检查按新的 Cron 执行

#### Scenario: Admin saves webhook and it takes effect
- **WHEN** 管理员保存飞书机器人地址并启用提醒
- **THEN** 后续告警立即使用新地址，无需重启后端

#### Scenario: Blank webhook preserves current value
- **WHEN** 管理员只修改最小间隔并留空 Webhook
- **THEN** 已保存的 Webhook 保持不变

#### Scenario: Non-Feishu webhook is rejected
- **WHEN** 管理员填写非飞书域名或 HTTP 地址
- **THEN** 系统返回 `2001`，不保存配置

#### Scenario: Database outage falls back to environment
- **WHEN** 数据库不可用且环境变量中存在 Webhook
- **THEN** 告警仍通过环境变量中的 Webhook 发送

### Requirement: Alert test action

管理员 MUST 能使用已保存的告警配置发送一条测试消息。测试 MUST 绕过同类告警去重；当提醒未启用或未保存 Webhook 时 MUST 返回明确错误，MUST NOT 使用页面中未保存的地址。

#### Scenario: Test message is sent to the saved webhook
- **WHEN** 管理员在故障提醒页点击「发送测试消息」
- **THEN** 飞书群收到一条测试消息，页面提示发送成功

#### Scenario: Test requires saved configuration
- **WHEN** 管理员未保存 Webhook 或未启用提醒就点击测试
- **THEN** 系统返回 `2001` 并提示先保存/启用配置

#### Scenario: Test bypasses rate limiting
- **WHEN** 管理员连续点击两次测试
- **THEN** 两次测试消息都发送，不被 300 秒去重拦截
