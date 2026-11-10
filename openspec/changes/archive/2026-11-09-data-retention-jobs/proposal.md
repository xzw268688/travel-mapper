Triage: ready-for-agent

## Why

数据库文档 8.3 已经约定了审计日志 30 天、登录日志 90 天、过期次数包、失效分享与过期邮箱令牌的清理策略，但代码里只有对象存储清理一个定时任务。这些表会随时间无限增长，导致查询变慢、磁盘占用上升，且文档与实际行为不一致。

## What Changes

- 新增 `RetentionCleanupService`，按 DB 文档 8.3 实现 6 个定时任务：
  - 每小时：删除过期超过 30 天的 `email_token`。
  - 每日 03:00：把 `UNUSED` 且已过期的 `card_key` 标记为 `EXPIRED`。
  - 每日 03:05：删除过期超过 90 天的 `quota_pack` 批次。
  - 每日 03:10：删除创建超过 30 天的 `audit_log`。
  - 每日 03:15：删除创建超过 90 天的 `login_log`。
  - 每日 03:20：删除已撤销或过期超过 30 天的 `note_share`。
- 每个任务独立调度、互相隔离；单个任务失败只记录 `warn`，不影响其他任务。
- Cron 可通过 `app.retention.*` 覆盖，默认与 DB 文档一致。
- 补充集成测试，覆盖过期数据被清理、未过期数据保留、卡密状态更新。

非目标：历史归档、冷存储、按用户维度的保留策略、Redis key 清理（Redis 已由 TTL 管理）。

## Capabilities

### New Capabilities

- `platform/retention`: 按保留期自动清理过期数据与失效令牌的定时任务能力。

## Impact

- 后端：新增 `RetentionCleanupService` 与集成测试；`@EnableScheduling` 已存在。
- 数据库：无新表、无迁移；仅执行 DELETE/UPDATE。
- 文档：DB 文档 8.3 与实际实现对齐；补充可覆盖的 Cron 配置说明。
- 测试：新增 `RetentionCleanupServiceTest`，覆盖 6 类清理。