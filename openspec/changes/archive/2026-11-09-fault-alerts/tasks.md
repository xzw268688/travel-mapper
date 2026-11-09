## 1. 告警发送

- [x] 1.1 新增 `AlertNotifier`：读取 `ALERT_ENABLED` / `ALERT_WEBHOOK_URL` / `ALERT_MIN_INTERVAL_SECONDS`，异步发送飞书文本消息
- [x] 1.2 实现同 key 最小间隔去重、发送失败只 `warn`、未配置时只写日志且不发请求
- [x] 1.3 `application.yml` 增加 `app.alert.*`，`.env.example` 增加 `ALERT_*` 说明

## 2. 触发点

- [x] 2.1 `GlobalExceptionHandler` 对 5xx 业务异常、数据库异常与未捕获异常发送告警；4xx 不发送
- [x] 2.2 新增 `HealthMonitorScheduler`：每 5 分钟检查 MySQL、Redis、对象存储
- [x] 2.3 依赖状态变化时告警，持续异常不重复；对象存储未配置时跳过；恢复时发送恢复告警

## 3. 测试与文档

- [x] 3.1 新增 `AlertNotifierTest`：本地 HTTP 服务验证发送、去重、未配置降级
- [x] 3.2 新增健康检查状态变化测试（可用 Mockito 模拟依赖）
- [x] 3.3 新增 `docs/ops/alerting.md`，说明飞书机器人配置、告警范围、防刷屏与关闭方式
- [x] 3.4 后端全量测试通过，验证方式：`mvn test` 退出码为 0