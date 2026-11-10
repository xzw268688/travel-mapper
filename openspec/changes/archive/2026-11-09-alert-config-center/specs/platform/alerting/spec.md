## MODIFIED Requirements

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

## ADDED Requirements

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