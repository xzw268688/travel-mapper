## Purpose

按固定保留期自动清理过期日志、令牌、分享与次数包，控制数据库增长，并让数据库文档中的保留策略与实际运行行为一致。

## ADDED Requirements

### Requirement: Scheduled retention cleanup

系统 MUST 按以下保留策略周期执行清理，MUST 使用数据库当前时间判断过期：

- `email_token`：删除过期超过 30 天的记录，每小时执行。
- `card_key`：把 `status=UNUSED` 且 `expire_at < NOW()` 的记录更新为 `EXPIRED`，每日 03:00 执行。
- `quota_pack`：删除 `expire_at` 超过 90 天的批次，每日 03:05 执行。
- `audit_log`：删除创建超过 30 天的记录，每日 03:10 执行。
- `login_log`：删除创建超过 90 天的记录，每日 03:15 执行。
- `note_share`：删除 `revoked=1` 或 `expire_at` 超过 30 天的记录，每日 03:20 执行。

清理 MUST 只删除过期数据，MUST NOT 影响保留期内的数据。每个任务 MUST 独立调度；单个任务失败 MUST NOT 阻止其他任务执行。

#### Scenario: Old audit logs are deleted
- **WHEN** 审计日志创建时间超过 30 天
- **THEN** 定时任务删除该记录，30 天内的日志保留

#### Scenario: Login logs have a longer retention
- **WHEN** 登录日志创建时间超过 90 天
- **THEN** 定时任务删除该记录，90 天内的日志保留

#### Scenario: Expired card keys are marked
- **WHEN** 卡密状态为 `UNUSED` 且已过过期时间
- **THEN** 定时任务把状态更新为 `EXPIRED`，未过期的卡密保持不变

#### Scenario: Expired quota packs are removed
- **WHEN** 次数包批次过期超过 90 天
- **THEN** 定时任务物理删除该批次，未超期的批次保留

#### Scenario: Invalid shares are removed
- **WHEN** 分享令牌已撤销，或过期超过 30 天
- **THEN** 定时任务删除该分享令牌，仍然有效的分享保留

#### Scenario: Expired email tokens are removed
- **WHEN** 邮箱令牌过期超过 30 天
- **THEN** 定时任务删除该令牌

#### Scenario: One failing task does not block others
- **WHEN** 某一个清理任务执行异常
- **THEN** 其他清理任务仍按各自的调度继续执行，异常只记录告警日志

### Requirement: Cleanup schedule configuration

清理任务的 Cron MUST 可通过配置覆盖，默认值 MUST 与数据库文档 8.3 一致。

#### Scenario: Default schedule matches documentation
- **WHEN** 未提供 `app.retention.*` 配置
- **THEN** 使用文档约定的每小时与每日 03:00–03:20 调度

#### Scenario: Operator overrides a schedule
- **WHEN** 运维设置 `app.retention.audit-log-cron`
- **THEN** 审计日志清理按新的 Cron 执行，其他任务不受影响