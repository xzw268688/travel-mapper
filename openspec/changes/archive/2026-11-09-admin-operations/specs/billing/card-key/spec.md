## ADDED Requirements

### Requirement: Card pricing
生成卡密时 MUST 可填写可选售价 `priceCny`（人民币，两位小数，≥0）；未填写时 MUST 存为 `null`。卡密列表与导出 MUST 返回该字段（导出列追加在固定列之后）。

#### Scenario: Batch generated with price
- **WHEN** 管理员以 29.90 元售价生成 50 张 AI 次数卡
- **THEN** 该批卡密记录售价 29.90，列表可见

#### Scenario: Price is optional
- **WHEN** 管理员不填售价生成卡密
- **THEN** 卡密正常生成，`priceCny` 为 `null`

#### Scenario: Negative price is rejected
- **WHEN** 管理员填写负数售价
- **THEN** 系统返回 `2001`，不生成卡密

### Requirement: Batch statistics
管理员 MUST 能按批次查看统计：类型、面额、售价、创建时间、生成数、已用、未用、作废、过期，以及估算收入（已用数量 × 售价；售价为空时收入为 `null`）。统计 MUST 仅管理员可访问。

#### Scenario: Batch shows usage counts
- **WHEN** 管理员查看某批次统计
- **THEN** 返回各状态数量，且已用 + 未用 + 作废 + 过期等于生成数

#### Scenario: Estimated revenue uses price
- **WHEN** 批次售价为 10 元且已兑换 30 张
- **THEN** 估算收入为 300.00

#### Scenario: Missing price yields no revenue
- **WHEN** 批次未填写售价
- **THEN** 估算收入为 `null`，不按 0 计算

### Requirement: Redemption records
管理员 MUST 能分页查询卡密兑换记录：卡密、批次、类型、面额、售价、兑换用户、兑换时间；MUST 支持按用户筛选（用于用户详情）。记录 MUST 仅来自已使用卡密，作废与未使用卡密 MUST NOT 出现在记录中。

#### Scenario: Redemption appears after use
- **WHEN** 用户兑换一张卡密
- **THEN** 兑换记录中出现该卡密、用户与兑换时间

#### Scenario: Filter by user
- **WHEN** 管理员在用户详情查看兑换记录
- **THEN** 只返回该用户兑换过的卡密

#### Scenario: Void cards are excluded
- **WHEN** 卡密被作废
- **THEN** 兑换记录中不出现该卡密