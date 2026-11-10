## Context

`@EnableScheduling` 已启用，现有 `StorageCleanupService` 使用 `@Scheduled(cron = "${app.storage.orphan-cleanup-cron:0 30 3 * * *}")` 清理未被引用的对象。DB 文档 8.3 列出了 6 类数据保留任务，但尚无实现。

## Goals / Non-Goals

**Goals**

- 6 类过期数据按文档保留期自动清理。
- 任务独立、失败隔离、Cron 可覆盖。
- 用测试证明「过期删除、未过期保留」。

**Non-Goals**

- 归档到冷存储、按用户自定义保留期、清理 Redis（TTL 已覆盖）。

## Decisions

- **单个 `RetentionCleanupService` + 多个 `@Scheduled` 方法**：每个任务一个方法，调度互相独立；无需引入 Quartz 等额外调度框架。
- **使用数据库 `NOW()` 判断过期**：避免应用与数据库时区不一致导致误删/漏删；保留期以数据库时间为准。
- **硬编码保留天数，Cron 可配置**：保留期来自产品文档，暂不需要运行期修改；Cron 通过 `app.retention.*` 覆盖，便于运维错峰。
- **失败只记录 `warn`**：定时任务不应让异常影响应用；每个方法内部 `try/catch`，并记录任务名与受影响行数。
- **物理删除而非软删除**：这些表本身不参与业务查询（日志/令牌/过期批次），物理删除符合文档；`card_key` 用状态更新而不是删除，保留兑换审计。

## Risks / Trade-offs

- [误删保留期内数据] → 所有条件基于 `NOW()` 与固定保留期，并用测试覆盖边界。
- [大表一次性 DELETE 造成锁/慢查询] → 当前数据量小；后续数据量大时可改为分批删除（如 `LIMIT` 循环），本次保持简单。
- [Cron 时区] → Spring `@Scheduled` 使用应用时区；DB 判断使用数据库时间。运维可用 `app.retention.*` 调整执行时间。
- [email_token 表可能为历史遗留] → 清理无副作用；若确认不再使用，可后续单独移除表。

## Migration Plan

1. 部署后端：定时任务开始按默认 Cron 运行。
2. 无数据库迁移；回滚只需移除该服务或关闭调度。
3. 首次上线可观察日志中的删除行数，确认保留期符合预期。

## Open Questions

无。