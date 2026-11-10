## Purpose

定义免费额度、VIP 特权与次数包的统一账本和扣减/退回契约，为 AI 生成与润色提供唯一可信的额度判定来源，并支撑额度查询与后台调整。

## ADDED Requirements

### Requirement: Daily free quota
普通用户 MUST 每个北京时间自然日拥有 3 次免费 AI 调用额度，行程生成与游记润色共享；额度 MUST 在次日零点重置；额度耗尽时 MUST 在调用 AI 前返回 `2011`。

#### Scenario: Fourth call is rejected
- **WHEN** 普通用户当日已使用 3 次免费额度后再次请求 AI 生成或润色
- **THEN** 系统返回 `2011`，不执行 AI 调用

#### Scenario: Quota resets at Beijing midnight
- **WHEN** 北京时间跨过零点
- **THEN** 当日已用次数归零，用户重新获得 3 次免费额度

#### Scenario: Server enforces the limit
- **WHEN** 前端绕过提示直接调用 AI 接口且额度已耗尽
- **THEN** 后端仍返回 `2011`，不产生 AI 请求

### Requirement: VIP entitlement
VIP MUST 由 `vip_expire_at > NOW()` 实时派生，不得作为角色；VIP 有效期内 MUST 不消耗次数包和免费额度，但仍 MUST 受默认 200 次/日的服务端上限保护；过期后 MUST 自动回到普通用户规则。

#### Scenario: VIP calls are unlimited within daily cap
- **WHEN** VIP 用户在有效期内连续调用 AI 且未达到日上限
- **THEN** 免费计数与次数包余额均不变化

#### Scenario: VIP daily cap protects cost
- **WHEN** VIP 用户当日调用达到 200 次
- **THEN** 后续调用返回 `2011`，直到次日重置

### Requirement: Pack batch accounting
次数包 MUST 以 `quota_pack` 批次为余额真实来源，每次授予生成一个批次；批次有效期 MUST 为授予后 365 天；扣减 MUST 按最早过期优先（`expire_at ASC, id ASC`）；余额 MUST 只统计未过期且 `used_count < granted_count` 的批次。

#### Scenario: Expired packs are excluded
- **WHEN** 查询额度或扣减时存在已过期批次
- **THEN** 该批次不计入余额，也不会被选中扣减

#### Scenario: Earliest-expiring pack is used first
- **WHEN** 用户有两个可用批次且到期时间不同
- **THEN** 下一次扣减落在最早到期的批次上

#### Scenario: Expiring-soon summary
- **WHEN** 用户有 30 天内到期的批次
- **THEN** 额度查询返回这些批次的剩余次数合计与最近到期时间；无则返回 `null`

### Requirement: Deduction order
额度扣减 MUST 按 VIP 特权 → 次数包 → 免费额度的顺序判定；判定与扣减 MUST 原子执行；次数包扣减 MUST 使用行锁或条件更新，影响行数不为 1 时 MUST 重试一次后再判定失败。

#### Scenario: Pack is used before free quota
- **WHEN** 非 VIP 用户同时拥有可用次数包和剩余免费额度
- **THEN** 本次调用扣减次数包，免费计数不变

#### Scenario: Concurrency does not overspend
- **WHEN** 同一用户并发发起多个 AI 请求且仅剩一次可用额度
- **THEN** 只有一个请求扣减成功，其余请求收到 `2011`

### Requirement: Deduction record and refund
每次成功扣减 MUST 以 `requestId` 记录扣减来源与批次，并设置有限期标记；AI 请求因超时、模型报错、JSON 重试后失败或后端异常而未产出可用结果时 MUST 退回本次扣减；用户主动取消或已输出部分有效内容后断开 MUST NOT 退回；重复退回 MUST 幂等。

#### Scenario: Failed AI request refunds
- **WHEN** AI 请求超时或模型报错且未产出可用结果
- **THEN** 系统按 `requestId` 退回本次扣减，额度恢复到调用前

#### Scenario: Repeated refund does not double-credit
- **WHEN** 同一 `requestId` 的退回逻辑被执行两次
- **THEN** 第二次不产生额外退回，额度只恢复一次

#### Scenario: User cancellation does not refund
- **WHEN** 用户主动取消生成，或已收到部分流式内容后断开
- **THEN** 本次扣减不退回

### Requirement: Quota query
已登录用户 MUST 能查询本人额度，返回 `plan`、`vipExpireAt`、`freeUsedToday`、`freeLimit=3`、`packBalance`、`packExpiringSoon`、`vipDailyLimit`、`vipDailyUsed` 和 `resetAt`（北京时间次日零点）。

#### Scenario: Free user sees daily usage
- **WHEN** 普通用户查询额度
- **THEN** 返回 `plan=FREE`、当日已用次数与剩余额度

#### Scenario: VIP sees pack balance unchanged
- **WHEN** VIP 用户查询额度
- **THEN** 返回 `plan=VIP`、`vipExpireAt` 和未消耗的 `packBalance`

### Requirement: Manual entitlement adjustment
管理员 MUST 能调整用户额度或 VIP：正数 `AI_QUOTA` 新建 365 天批次，负数从最早到期批次扣减且余额不得小于 0；VIP 调整 MUST 按 `GREATEST(NOW(), 现有到期时间) + delta 天` 计算；每次调整 MUST 写 `quota_ledger`（`source=ADMIN`）与审计日志。

#### Scenario: Positive pack adjustment creates a batch
- **WHEN** 管理员为用户增加 100 次 AI 额度
- **THEN** 系统新建一个 100 次的 365 天批次，余额增加 100

#### Scenario: Negative adjustment cannot go below zero
- **WHEN** 管理员扣减的次数超过用户可用余额
- **THEN** 系统返回 `2001`，不做任何扣减

#### Scenario: VIP extension is additive
- **WHEN** 用户 VIP 还剩 10 天，管理员增加 30 天
- **THEN** VIP 到期时间延长为剩余 40 天
