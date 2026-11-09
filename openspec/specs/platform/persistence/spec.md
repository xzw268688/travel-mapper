# Platform Persistence Specification

## Purpose

定义 TravelMapper V1.0 的持久化基线：全部业务表、初始字典数据、软删除与子表物理删除规则，以及金额与汇率快照的存储口径，供后续所有业务能力共享。

## Requirements

### Requirement: Complete business schema
系统 MUST 提供覆盖全部 V1.0 业务对象的 MySQL 8.0 表结构：`user`、`email_token`、`country`、`city`、`trip`、`itinerary`、`itinerary_day`、`itinerary_spot`、`country_official_link`、`trip_checklist`、`travel_note`、`note_share`、`expense`、`exchange_rate`、`card_key`、`quota_pack`、`quota_ledger`、`feedback`、`note_report`、`sys_config`、`audit_log`；可选 `exchange_rate_history` MUST NOT 阻塞建表。

#### Scenario: Fresh database bootstrap
- **WHEN** 在空数据库执行建表脚本
- **THEN** 上述 21 张表全部创建成功，字符集为 utf8mb4，且脚本可重复执行而不因表已存在而失败

#### Scenario: Schema covers documented fields and indexes
- **WHEN** 对照 `TravelMapper DB V1.0.md` 第 4 章核对表结构
- **THEN** 每张表的字段、唯一键和关键索引均存在，`amount_cny` 为 STORED 生成列

### Requirement: Initial dictionary and configuration data
系统 SHALL 提供可重复执行的初始化数据，至少包含国家字典、城市字典、汇率、预置管理员和 `sys_config` 默认项；行前清单的 7 个固定项 MUST 作为代码常量维护，不单独建表。

#### Scenario: First install seeds dictionaries
- **WHEN** 在空数据库执行初始化脚本
- **THEN** 国家、城市、汇率、管理员和系统配置均可用，管理员可登录后台

#### Scenario: Re-running seeds does not duplicate
- **WHEN** 对已初始化数据库再次执行种子脚本
- **THEN** 不产生重复的国家代码、城市代码、币种或配置键，脚本正常结束

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

### Requirement: Money and exchange-rate snapshot integrity
金额 MUST 以 `DECIMAL(12,2)` 存储，禁止浮点；每笔消费 MUST 保存保存时刻的汇率值快照，不得保存汇率表主键；`amount_cny` MUST 由数据库生成列计算，汇总 MUST 直接使用 `SUM(amount_cny)` 并忽略 NULL。

#### Scenario: Rate change does not rewrite history
- **WHEN** 管理员修改某币种汇率
- **THEN** 已保存消费的 `exchange_rate_snapshot` 与 `amount_cny` 保持不变

#### Scenario: Missing rate stays out of totals
- **WHEN** 某笔消费的币种没有汇率，`exchange_rate_snapshot` 为 NULL
- **THEN** 该笔消费仍可保存，汇总时不计入 CNY 总额并提示未配置汇率

### Requirement: Uniqueness and concurrency constraints
数据库 MUST 通过唯一索引保证用户名、邮箱、城市 `(country_code, city_code)`、卡密 `code`、分享令牌 `token`、清单 `(user_id, itinerary_id, item_key)` 的唯一性；卡密兑换 MUST 由条件更新或行锁保证同一卡密只能成功一次。

#### Scenario: Duplicate business key rejected
- **WHEN** 写入重复的城市代码、卡密码或分享令牌
- **THEN** 数据库返回唯一键冲突，应用层转为对应的业务错误

#### Scenario: Concurrent redemption has one winner
- **WHEN** 多个请求同时兑换同一张未使用卡密
- **THEN** 只有一个请求成功，其余请求收到“卡密已使用”错误

### Requirement: Backup and recovery baseline
系统 SHALL 提供每日数据库备份并同步到对象存储的脚本，Redis MUST 开启 AOF；恢复步骤 MUST 记录在部署文档中，RPO 为数据库最多 1 天、Redis 最多几秒，RTO 为手工恢复 2 小时内。

#### Scenario: Daily backup is produced and uploaded
- **WHEN** 定时任务触发备份
- **THEN** 生成可导入的数据库转储文件并上传到配置的对象存储位置，失败时记录错误

#### Scenario: Restore drill succeeds
- **WHEN** 按部署文档在测试环境执行恢复演练
- **THEN** 数据库与 Redis 均可恢复到备份时点，且应用可启动完成健康检查
