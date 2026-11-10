## Purpose

为多币种记账提供管理员维护的固定汇率表，区分“新增币种回填历史空快照”与“修改币种不回溯”两种行为，保证历史汇总稳定且补配置后能自动纳入。

## ADDED Requirements

### Requirement: Exchange rate listing
管理员 MUST 能查询当前生效的汇率列表，返回币种、对 CNY 汇率和更新时间；汇率 MUST 保留 6 位小数。

#### Scenario: Rates are listed
- **WHEN** 管理员打开汇率维护页
- **THEN** 返回所有已配置币种及其汇率

### Requirement: New currency backfill
当管理员为**此前不存在**的币种新增汇率时，系统 MUST 在同一事务内插入汇率，并把该币种所有 `exchange_rate_snapshot IS NULL` 且未删除的消费回填为本次汇率；MUST 返回 `affectedUnconfiguredRecords` 并写审计日志。

#### Scenario: New currency backfills history
- **WHEN** 管理员首次配置 KRW 汇率，且存在 7 条空快照的 KRW 消费
- **THEN** 这 7 条消费写入新快照，`amount_cny` 自动重算，返回 `affectedUnconfiguredRecords=7`

#### Scenario: Backfill affects totals
- **WHEN** 回填完成后用户查看汇总
- **THEN** 原先未计入的 KRW 消费被纳入 CNY 总额

#### Scenario: Backfill is audited
- **WHEN** 新增币种并回填
- **THEN** `audit_log` 记录币种、汇率与影响条数

### Requirement: Existing currency update
修改已有币种汇率时 MUST 只更新汇率表，MUST NOT 回填或修改任何历史消费快照；新汇率 MUST 仅影响此后录入或修改币种/金额的消费。

#### Scenario: Update does not rewrite history
- **WHEN** 管理员修改 JPY 汇率
- **THEN** 已有 JPY 消费的 `exchange_rate_snapshot` 与 `amount_cny` 保持不变，`affectedUnconfiguredRecords=0`

#### Scenario: New expense uses new rate
- **WHEN** 汇率修改后用户新增一笔 JPY 消费
- **THEN** 该笔消费使用新汇率写入快照

### Requirement: Exchange rate validation
币种 MUST 为 3 位大写字母；汇率 MUST 大于 0 且最多 6 位小数；非法输入 MUST 返回 `2001`；后台汇率接口 MUST 仅管理员可访问。

#### Scenario: Invalid currency is rejected
- **WHEN** 管理员提交两字母币种或负汇率
- **THEN** 系统返回 `2001`，不写入

#### Scenario: Normal user is forbidden
- **WHEN** 普通用户调用汇率后台接口
- **THEN** 系统返回 `4002`
