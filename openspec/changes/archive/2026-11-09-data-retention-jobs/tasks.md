## 1. 清理服务

- [x] 1.1 新增 `RetentionCleanupService`，实现 email_token 每小时清理（过期 >30 天）
- [x] 1.2 实现 card_key 过期标记（每日 03:00）与 quota_pack 清理（>90 天，03:05）
- [x] 1.3 实现 audit_log（>30 天，03:10）、login_log（>90 天，03:15）、note_share（撤销或过期 >30 天，03:20）清理
- [x] 1.4 每个任务独立 `try/catch` 并记录删除/更新行数，Cron 支持 `app.retention.*` 覆盖

## 2. 测试

- [x] 2.1 新增集成测试：过期数据被删除、保留期内数据保留
- [x] 2.2 覆盖 card_key 状态更新、note_share 撤销/过期、email_token 过期清理
- [x] 2.3 覆盖单个任务异常不影响其他任务（或至少验证任务方法独立捕获异常）

## 3. 文档与回归

- [x] 3.1 更新 `TravelMapper DB  V1.0.md` 8.3，注明 Cron 可覆盖项与实现位置
- [x] 3.2 后端全量测试通过，验证方式：`mvn test` 退出码为 0