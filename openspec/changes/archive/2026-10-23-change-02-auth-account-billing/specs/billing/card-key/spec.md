## Purpose

把卡密作为 V1 唯一的付费通道，定义批量生成、导出、作废和兑换的完整规则，确保一码一用、并发安全、幂等可重放，并将兑换结果正确记入额度或 VIP。

## ADDED Requirements

### Requirement: Batch card generation
管理员 MUST 能按类型（`AI_QUOTA`/`VIP`）、面额、可选有效期、数量（1–500）和可选前缀（2–6 位大写字母，默认 `TM`）批量生成卡密。编码 MUST 为 `{prefix}-{16 位随机码}`，随机码使用大写字母与数字并剔除 `0/O/1/I/L`；同一批次 MUST 共享 `batch_id`，生成 MUST 在单事务内完成并写审计日志。

#### Scenario: Batch has unique codes
- **WHEN** 管理员生成 100 张 VIP 卡密
- **THEN** 返回 100 个唯一卡密，使用同一 `batchId`，全部状态为 `UNUSED`

#### Scenario: Too many cards are rejected
- **WHEN** 管理员请求生成超过 500 张
- **THEN** 系统返回 `2001`，不生成任何卡密

#### Scenario: Confusable characters are excluded
- **WHEN** 检查生成的随机码
- **THEN** 其中不含 `0`、`O`、`1`、`I`、`L`

### Requirement: Card listing and export
管理员 MUST 能按批次和状态分页查询卡密；导出 MUST 返回 CSV，编码为 UTF-8 with BOM，字段顺序固定为 `code,type,value,expireAt,status,createdAt`，时间格式为 `yyyy-MM-dd HH:mm:ss`，文件名包含批次与时间戳，并写审计日志。

#### Scenario: Export opens correctly in Excel
- **WHEN** 管理员导出某批次卡密
- **THEN** 文件带 UTF-8 BOM，中文与状态列无乱码，字段顺序与约定一致

#### Scenario: Filter by status
- **WHEN** 管理员按 `UNUSED` 查询卡密列表
- **THEN** 只返回未使用卡密，分页信息正确

### Requirement: Card voiding
只有 `UNUSED` 卡密 MUST 能被作废；作废后状态变为 `VOID`；已使用卡密 MUST NOT 回滚；不存在卡密返回 `2008`，已使用卡密作废返回 `2001`。

#### Scenario: Unused card can be voided
- **WHEN** 管理员作废一张未使用卡密
- **THEN** 状态变为 `VOID`，后续兑换返回 `2010`

#### Scenario: Used card cannot be voided
- **WHEN** 管理员尝试作废一张已使用卡密
- **THEN** 系统返回 `2001`，状态保持 `USED`

### Requirement: Card redemption and idempotency
兑换 MUST 要求 `Idempotency-Key`；同一用户同一 Key 在 24 小时内重复提交 MUST 返回首次结果并标记 `idempotentReplay=true`；缺少该头 MUST 返回 `2001`。兑换 MUST 使用分布式锁与 `status='UNUSED'` 条件更新，只有影响行数为 1 才成功。

#### Scenario: Replay returns first result
- **WHEN** 用户用同一 `Idempotency-Key` 重复提交同一卡密
- **THEN** 第二次返回首次兑换结果，不重复入账

#### Scenario: Missing idempotency key is rejected
- **WHEN** 兑换请求缺少 `Idempotency-Key`
- **THEN** 系统返回 `2001`，不执行兑换

#### Scenario: Concurrent redemption has one winner
- **WHEN** 多个用户的请求同时兑换同一张未使用卡密
- **THEN** 只有一个请求成功，其余返回 `2009` 或 `3002`

### Requirement: Card status validation
兑换 MUST 区分卡密不存在（`2008`）、已使用（`2009`）、已作废或过期（`2010`）；失败 MUST NOT 改变卡密状态或用户额度。

#### Scenario: Unknown code
- **WHEN** 用户提交不存在的卡密
- **THEN** 系统返回 `2008`

#### Scenario: Expired code
- **WHEN** 用户提交已过自身有效期的卡密
- **THEN** 系统返回 `2010`，不发放额度

### Requirement: Entitlement posting on redemption
`AI_QUOTA` 兑换 MUST 新建一个 `quota_pack` 批次（`granted_count=value`、`used_count=0`、`expire_at=now+365 天`、`card_key_id=卡密 id`）；`VIP` 兑换 MUST 按 `GREATEST(NOW(), vip_expire_at) + value 天` 延长；两类兑换 MUST 写 `quota_ledger` 与审计日志，并在成功后刷新额度缓存。

#### Scenario: AI quota creates pack batch
- **WHEN** 用户兑换 100 次 AI 次数包
- **THEN** 新增一个 100 次、365 天有效的批次，`packBalance` 立即增加 100

#### Scenario: VIP stacks remaining days
- **WHEN** 用户 VIP 还剩 10 天，兑换 30 天 VIP 卡密
- **THEN** VIP 到期时间变为剩余 40 天，次数包余额不变

#### Scenario: Successful redemption is audited
- **WHEN** 任一卡密兑换成功
- **THEN** `quota_ledger` 新增 `REDEEM` 流水，`audit_log` 记录操作人与卡密
