## MODIFIED Requirements

### Requirement: Complete business schema
系统 MUST 提供覆盖全部 V1.0 业务对象的 MySQL 8.0 表结构：`user`、`email_token`、`country`、`city`、`trip`、`itinerary`、`itinerary_day`、`itinerary_spot`、`country_official_link`、`trip_checklist`、`travel_note`、`note_share`、`expense`、`exchange_rate`、`card_key`、`quota_pack`、`quota_ledger`、`feedback`、`note_report`、`sys_config`、`audit_log`；可选 `exchange_rate_history` MUST NOT 阻塞建表。

#### Scenario: Fresh database bootstrap
- **WHEN** 在空数据库执行建表脚本
- **THEN** 上述 21 张表全部创建成功，字符集为 utf8mb4，且脚本可重复执行而不因表已存在而失败

#### Scenario: Schema covers documented fields and indexes
- **WHEN** 对照 `TravelMapper DB V1.0.md` 第 4 章核对表结构
- **THEN** 每张表的字段、唯一键和关键索引均存在，`amount_cny` 为 STORED 生成列

### Requirement: Deletion strategy per table
系统 MUST 按文档约定区分软删除与物理删除：带 `deleted` 的业务表默认查询排除已删除行；`itinerary_day`、`itinerary_spot`、`email_token`、`note_share`、`card_key`、`country_official_link`、`exchange_rate`、`quota_pack`、`quota_ledger`、`feedback`、`note_report`、`sys_config`、`audit_log` MUST NOT 依赖 `deleted` 标记。

#### Scenario: Deleted parent hides children
- **WHEN** 一条行程被软删除
- **THEN** 行程详情与地图统计均不再返回该行程，其天和景点也不再可达

#### Scenario: Regenerating physically replaces child rows
- **WHEN** 用户重新生成一条行程
- **THEN** 旧的 `itinerary_day` / `itinerary_spot` 行被物理删除并替换为新行，不产生 `(itinerary_id, day_index)` 唯一键冲突

#### Scenario: Feedback is never soft-deleted
- **WHEN** 用户或管理员查询反馈
- **THEN** 查询不应用 `deleted` 过滤，反馈记录始终存在且按状态与时间可见

#### Scenario: Reports are never soft-deleted
- **WHEN** 用户或管理员查询举报
- **THEN** 查询不应用 `deleted` 过滤，举报记录始终存在且按状态与时间可见