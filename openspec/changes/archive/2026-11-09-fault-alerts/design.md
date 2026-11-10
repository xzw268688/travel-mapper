## Context

`@EnableScheduling` 已启用；`GlobalExceptionHandler` 统一处理异常；`StorageProviderHolder` 提供 `probe()`；`StringRedisTemplate` 与 `JdbcTemplate` 可用于健康检查。SMTP 当前未启用，因此选择飞书群机器人 Webhook。

## Goals / Non-Goals

**Goals**

- 5xx 与依赖状态变化主动通知，且不刷屏。
- Webhook 未配置/失败时完全降级，不影响业务。
- Webhook 地址不进入代码或 Git。

**Non-Goals**

- 完整链路追踪、CPU/内存阈值告警、短信/电话、多实例去重。

## Decisions

- **直接使用飞书自定义机器人 Webhook**：请求体 `{"msg_type":"text","content":{"text":"..."}}`，不引入飞书 SDK；URL 通过 `ALERT_WEBHOOK_URL` 注入。
- **Java `HttpClient` + 单线程守护线程池异步发送**：连接超时 3 秒、请求超时 5 秒；发送失败只 `warn`，不阻塞请求线程。
- **内存防刷屏**：`ConcurrentHashMap<key, lastSentMillis>`，同类告警在 `ALERT_MIN_INTERVAL_SECONDS`（默认 300 秒）内只发一次。单实例足够；多实例会各自发送，属于已知限制。
- **健康检查只在状态变化时告警**：`ConcurrentHashMap<依赖名, 是否健康>`，首次异常会告警，持续异常不重复；恢复时发一次恢复消息。对象存储未配置则跳过。
- **5xx 才告警**：在 `GlobalExceptionHandler` 中对 `BusinessException` 的 5xx、数据库异常与未捕获异常调用 notifier；4xx 不调用。告警内容包含接口路径与 `RequestContext.traceId()`。
- **配置默认值**：`ALERT_ENABLED=true`，但 `ALERT_WEBHOOK_URL` 为空时自动 no-op；只有显式配置 URL 才发送外部请求。

## Risks / Trade-offs

- [Webhook 地址泄露] → 只从环境变量读取，不写入 `application.yml`/代码/日志；`.env` 已在 `.gitignore` 中。
- [飞书机器人频率限制] → 内存去重；同 key 300 秒一次，远低于飞书限制。
- [应用重启后健康状态丢失] → 首次检查若仍异常会重新告警一次，可接受。
- [多实例重复告警] → 当前单实例部署；后续可换成 Redis 去重。
- [异步线程池队列堆积] → 单线程 + 小队列，发送超时短；失败即丢弃，避免影响主流程。

## Migration Plan

1. 在 `.env` 增加 `ALERT_WEBHOOK_URL`，重启后端。
2. 无需数据库迁移。
3. 上线后可通过触发一次测试消息或等待健康检查验证。

## Open Questions

无。