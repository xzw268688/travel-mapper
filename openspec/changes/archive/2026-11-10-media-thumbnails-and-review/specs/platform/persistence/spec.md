## MODIFIED Requirements

### Requirement: Complete business schema

系统 MUST 提供覆盖全部 V1.0 业务对象的 MySQL 8.0 表结构：`user`、`user_recovery_code`、`email_token`、`country`、`city`、`trip`、`itinerary`、`itinerary_day`、`itinerary_spot`、`country_official_link`、`trip_checklist`、`travel_note`、`note_share`、`expense`、`exchange_rate`、`card_key`、`quota_pack`、`quota_ledger`、`feedback`、`note_report`、`sys_config`、`audit_log`；可选 `exchange_rate_history` MUST NOT 阻塞建表。`user` MUST 包含 `totp_secret`（密文）、`totp_enabled` 与 `totp_confirmed_at` 字段；`user_recovery_code` MUST 保存恢复码哈希、使用时间与所属用户；`travel_note` MUST 包含 `media_reviewed_at` 与 `media_reviewed_by` 字段用于人工媒体审核。

#### Scenario: Fresh database bootstrap
- **WHEN** 在空数据库执行建表脚本
- **THEN** 上述 22 张表全部创建成功，字符集为 utf8mb4，且脚本可重复执行而不因表已存在而失败

#### Scenario: Schema covers documented fields and indexes
- **WHEN** 对照 `TravelMapper DB V1.0.md` 第 4 章核对表结构
- **THEN** 每张表的字段、唯一键和关键索引均存在，`amount_cny` 为 STORED 生成列

#### Scenario: Account security fields exist
- **WHEN** 检查 `user` 与 `user_recovery_code` 表结构
- **THEN** 2FA 字段与恢复码哈希表可用，且不保存 TOTP 密钥或恢复码明文

#### Scenario: Media review fields exist
- **WHEN** 检查 `travel_note` 表结构
- **THEN** `media_reviewed_at` 与 `media_reviewed_by` 字段存在，可用于待审核筛选与审核留痕