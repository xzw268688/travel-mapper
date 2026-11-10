Triage: ready-for-agent

## Why

系统目前只有监控页面和日志，没有主动告警：接口出现 500、数据库/Redis/对象存储不可用时，只能等用户反馈。需要一条轻量的通知链路，把故障推到管理员能及时看到的地方。

## What Changes

- 新增飞书群机器人 Webhook 告警：通过 `ALERT_WEBHOOK_URL` 配置，发送纯文本消息。
- 未捕获的 5xx 接口错误触发告警；4xx 业务错误不告警。
- 新增依赖健康检查：每 5 分钟检查 MySQL、Redis、对象存储连通性，只在「正常 → 异常」和「异常 → 正常」时告警，避免刷屏。
- 同类告警默认 300 秒内只发一次；可通过 `ALERT_MIN_INTERVAL_SECONDS` 调整。
- Webhook 未配置时自动降级为只写日志，不影响业务；Webhook 地址只从环境变量读取，不写入代码或仓库。
- 健康检查 Cron 可通过 `ALERT_HEALTH_CRON` 覆盖。

非目标：完整链路追踪（SkyWalking/Jaeger）、指标阈值告警（CPU/内存）、短信/电话通知、多实例去重。

## Capabilities

### New Capabilities

- `platform/alerting`: 通过飞书 Webhook 发送 5xx 接口错误与依赖健康状态告警的能力。

## Impact

- 后端：新增 `AlertNotifier`、`HealthMonitorScheduler`；`GlobalExceptionHandler` 增加 5xx 告警；`application.yml` 增加 `app.alert.*` 配置。
- 配置：`.env.example` 增加 `ALERT_*`；本地 `.env` 保存真实 Webhook（不提交）。
- 文档：新增 `docs/ops/alerting.md`，说明飞书机器人配置、告警范围与防刷屏。
- 测试：新增 `AlertNotifierTest` 与健康检查状态变化测试。