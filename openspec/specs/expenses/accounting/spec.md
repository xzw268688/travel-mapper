# Expenses Accounting Specification

## Purpose

定义多币种消费记录与多维汇总口径：每笔消费保存汇率值快照，国家必选、城市可选，关联行程或旅行后自动归组，汇总始终以历史快照折算为 CNY。

## Requirements

### Requirement: Expense creation
创建消费 MUST 校验：国家必填；城市可选但若提供必须属于该国家且已启用；币种为 ISO 4217；金额为两位小数字符串且允许负数；分类属于固定 7 类；消费日期不得晚于今天；备注 ≤100 字。保存时 MUST 写入当前汇率值快照，不得保存汇率 ID。

#### Scenario: Valid expense is saved with snapshot
- **WHEN** 用户提交合法 JPY 消费
- **THEN** 保存成功，返回 `exchangeRateSnapshot` 与折算后的 `amountCny`

#### Scenario: Missing country is rejected
- **WHEN** 新增消费未选国家
- **THEN** 系统返回 `2001`，不保存

#### Scenario: Future date is rejected
- **WHEN** 消费日期晚于今天
- **THEN** 系统返回 `2001`

#### Scenario: Missing rate is allowed
- **WHEN** 币种尚未配置汇率
- **THEN** 消费仍可保存，返回 `rateMissing=true`、`amountCny=null`

#### Scenario: Negative amount is accepted
- **WHEN** 用户录入负数金额表示退款
- **THEN** 保存成功，折算金额同为负，汇总时冲减

### Requirement: Expense association and grouping
提供 `itineraryId` 时 MUST 以行程为准继承国家、城市与 `tripId`，入参冲突时忽略入参；未提供时 MAY 手动选择同国家的旅行或未归组；跨国家手动归组 MUST 返回 `2001`；默认选中该国家最近一次旅行。

#### Scenario: Linked expense inherits trip
- **WHEN** 消费关联某条行程
- **THEN** 国家、城市与 `tripId` 来自行程，返回 `tripInherited=true`

#### Scenario: City is optional without itinerary
- **WHEN** 用户选择国家但不选择城市
- **THEN** 消费允许保存，城市为空

#### Scenario: Cross-country trip is rejected
- **WHEN** 用户把日本消费归到泰国旅行
- **THEN** 系统返回 `2001`

### Requirement: Expense edit
编辑 MUST 支持部分更新；币种或金额变化时 MUST 按当前汇率表重新计算快照；仅修改备注/分类/日期时 MUST 保留原快照；非本人或不存在的消费 MUST 返回 `2005`。

#### Scenario: Amount change refreshes snapshot
- **WHEN** 用户修改金额或币种
- **THEN** 系统按当前汇率重新写入快照并更新 `amountCny`

#### Scenario: Remark change keeps snapshot
- **WHEN** 用户只修改备注
- **THEN** 原 `exchangeRateSnapshot` 与 `amountCny` 保持不变

#### Scenario: Other user's expense is hidden
- **WHEN** 用户编辑他人消费
- **THEN** 系统返回 `2005`

### Requirement: Expense deletion
删除 MUST 为软删除；汇总与列表 MUST 实时排除已删除记录；不保留历史汇总版本。

#### Scenario: Deleted expense disappears from totals
- **WHEN** 用户删除一笔消费
- **THEN** 列表与汇总中不再包含该笔金额

### Requirement: Expense listing
列表 MUST 支持按国家、城市、旅行、未归组、日期区间和分类过滤，并分页；MUST 只返回本人未删除消费；响应 MUST 包含国家名、城市名、原币金额、快照汇率、CNY 金额和 `rateMissing`。

#### Scenario: Filter by trip
- **WHEN** 用户按 `tripId` 查询消费
- **THEN** 只返回该旅行下的消费

#### Scenario: Filter ungrouped
- **WHEN** 用户使用 `ungrouped=true`
- **THEN** 只返回 `trip_id IS NULL` 的消费

### Requirement: Expense summary
汇总 MUST 支持 `country`、`city`、`trip`、`date` 单维或多维分组，默认按国家；多维时 `key` MUST 用 `|` 拼接；金额 MUST 按历史快照折算为 CNY；负数 MUST 参与求和；软删除 MUST 排除；未配置汇率的记录 MUST NOT 计入总额，并返回 `hasUnconfiguredCurrency=true` 与币种列表；按国家或旅行汇总时 MUST 返回原币种明细。

#### Scenario: Country summary
- **WHEN** 用户按国家汇总
- **THEN** 返回每个国家的 CNY 总额、分类小计和原币种明细

#### Scenario: Multi-dimensional key
- **WHEN** 用户按国家和城市分组
- **THEN** 每组 `key` 形如 `JP|TYO`

#### Scenario: Unconfigured currency is excluded
- **WHEN** 汇总中存在未配置汇率的消费
- **THEN** 该金额不计入 `totalCny`，并在 `unconfiguredCurrencies` 中列出币种

#### Scenario: Refund offsets spending
- **WHEN** 某分类存在负数消费
- **THEN** 分类小计与总额按代数和计算

#### Scenario: Trip summary
- **WHEN** 用户按旅行汇总
- **THEN** 未归组消费归入“未归组”，各旅行返回小计

### Requirement: Expense isolation
所有消费接口 MUST 按当前用户过滤；越权访问 MUST 返回 `2005`，不得暴露资源是否存在。

#### Scenario: Cross-user summary is scoped
- **WHEN** 用户请求消费汇总
- **THEN** 结果只包含本人数据
