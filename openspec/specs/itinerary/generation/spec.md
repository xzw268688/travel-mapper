# Itinerary Generation Specification

## Purpose

定义 AI 行程生成的完整行为：请求校验、额度与去重、LLM 与搜索并行、结构化结果校验、多城市分配与降级，以及草稿不落库的边界。

## Requirements

### Requirement: Generation request validation
生成请求 MUST 包含合法的 `requestId`（UUID）、`countryCode`、1–5 个属于该国家且已启用的城市、1–15 天的 `days`、不早于昨天的 `startDate` 和不超过 200 字的 `preference`；任一不满足 MUST 返回 `2001` 或 `2002`，且不得扣减额度。

#### Scenario: Invalid city is rejected before charging
- **WHEN** 用户选择了一个已停用或不属于所选国家的城市
- **THEN** 系统返回 `2002`，不扣额度、不调用 LLM

#### Scenario: Date in the past is rejected
- **WHEN** `startDate` 早于昨天
- **THEN** 系统返回 `2001`，不生成行程

#### Scenario: Day count out of range
- **WHEN** `days` 小于 1 或大于 15
- **THEN** 系统返回 `2001`，不生成行程

### Requirement: Quota check and deduction
系统 MUST 在调用 LLM 前完成额度可用性检查，并按 change-02 的扣减顺序先扣后调；额度不足 MUST 返回 `2011`，不得调用 LLM；扣减 MUST 记录 `requestId` 归属，供取消与退回使用。

#### Scenario: Exhausted quota blocks generation
- **WHEN** 普通用户当日免费额度已用完且没有次数包
- **THEN** 系统返回 `2011`，LLM 未被调用

#### Scenario: Deduction records request ownership
- **WHEN** 额度扣减成功
- **THEN** 系统记录该 `requestId` 属于当前用户，后续取消只能作用于本人请求

### Requirement: 60-second deduplication
系统 MUST 按“国家 + 排序后的城市集合 + 天数 + 规范化偏好”计算指纹；60 秒内相同指纹 MUST 返回首次结果且不二次扣费；若相同请求正在生成中，后到请求 MUST 返回 `3001`；在途锁 TTL 120 秒并 MUST 在 `finally` 中释放。

#### Scenario: Repeat within 60 seconds is free
- **WHEN** 用户 60 秒内用完全相同参数再次生成
- **THEN** 返回首次结果，`deduplicated=true`，额度不再扣减

#### Scenario: Concurrent identical requests
- **WHEN** 两个相同指纹的请求同时到达且首个尚未完成
- **THEN** 后到请求返回 `3001`“相同行程正在生成中”，不扣额度、不调用 LLM

#### Scenario: Changed parameters charge normally
- **WHEN** 用户修改城市、天数或偏好后重新生成
- **THEN** 指纹变化，按新请求正常扣减额度并生成

### Requirement: LLM output contract
LLM 结果 MUST 满足：`countryCode` 与请求一致；`days` 数量等于请求天数；`dayIndex` 从 1 连续递增；每天 `cityCode` 属于用户选择的城市集合；每天 1–6 个景点；景点 `name` 必填；每个景点 SHOULD 同时输出 `category`、`ticketPrice`、`highlights`、`bestTime`、`nearestStation`、`reservationRequired`、`area`、`warningNotes` 八个扩展字段；`date` 由后端按 `startDate + dayIndex - 1` 生成，不得由模型决定。扩展字段缺失、类型错误或超长 MUST 按空值/截断降级，MUST NOT 因此判定整份行程解析失败或触发重试；`reservationRequired` MUST 归一为布尔值，无法识别时降级为 `false`。

#### Scenario: Valid structured result is accepted
- **WHEN** LLM 返回符合 Schema 的 JSON
- **THEN** 系统补全日期与城市名后返回结构化行程

#### Scenario: Invalid day count is rejected
- **WHEN** 模型返回的天数不等于请求天数
- **THEN** 视为解析失败，进入重试流程

#### Scenario: City outside selection is rejected
- **WHEN** 模型返回的某天城市不在用户选择的集合中
- **THEN** 校验失败，进入重试或单城市兜底流程

#### Scenario: Extended spot fields are parsed
- **WHEN** LLM 为景点输出类别、门票、亮点、最佳时段、最近车站、需预约、区域与避雷备注
- **THEN** 系统将这些字段原样（超长截断）纳入生成响应与缓存，供保存时写入

#### Scenario: Missing extended fields degrade gracefully
- **WHEN** 模型返回的某个景点缺少全部或部分扩展字段
- **THEN** 缺失字段为空值（`reservationRequired` 为 `false`），行程仍生成成功，不重试、不退回额度

#### Scenario: Malformed extended field does not fail the plan
- **WHEN** 某个扩展字段类型错误或超过长度上限
- **THEN** 该字段被降级为空值或截断，其余字段与行程正常返回

### Requirement: Retry and failure compensation
LLM 调用 MUST 设置 30 秒超时；解析或 Schema 校验失败 MUST 重试 1 次，并在重试提示中要求只输出 JSON；两次均失败 MUST 退回本次额度并返回 `2012`。搜索失败 MUST NOT 导致额度退回。

#### Scenario: First parse failure retries once
- **WHEN** 首次 LLM 返回无法解析的文本
- **THEN** 系统重试一次，成功后正常返回行程

#### Scenario: Both attempts fail refunds quota
- **WHEN** 两次调用均超时或均无法通过校验
- **THEN** 本次扣减被退回，接口返回 `2012`

#### Scenario: Search failure does not refund
- **WHEN** 行程生成成功但搜索超时或报错
- **THEN** 行程正常返回，`guides=[]`，额度不退

### Requirement: Multi-city allocation and fallback
多城市生成时 prompt MUST 要求按天分配城市并尽量保持连续；若天数不少于城市数，每个所选城市 MUST 至少被安排一天，未被安排的城市 MUST 写入 `warnings`；模型无法输出合法城市时 MUST 回退为所选城市中的第一个单城市生成，并在 `warnings` 中说明，不得报错或退回额度。

#### Scenario: All selected cities appear
- **WHEN** 用户选择东京和大阪、生成 5 天
- **THEN** 行程中至少各有一天分配给东京和大阪

#### Scenario: Unassigned city is warned
- **WHEN** 模型中某个所选城市没有分配到任何一天
- **THEN** `warnings` 增加“城市未被安排”的提示，行程仍返回

#### Scenario: Invalid allocation falls back to single city
- **WHEN** 模型未输出城市、输出空值或输出不在所选集合的城市且重试后仍失败
- **THEN** 系统按第一个所选城市生成单城市行程，`warnings` 说明已回退，额度不退

### Requirement: Guides search
系统 MUST 与 LLM 并行发起攻略搜索，搜索独立超时 5 秒；结果 MUST 按规范化指纹缓存 24 小时；每日搜索次数超过配置上限时 MUST 返回空 `guides` 且不报错；最终 `guides` MUST 最多 3 条、去重并仅接受 http/https 链接。

#### Scenario: Search timeout degrades
- **WHEN** 搜索 5 秒未返回
- **THEN** 行程照常返回，`guides=[]`，不展示攻略区

#### Scenario: Cached guides are reused
- **WHEN** 24 小时内以相同国家、城市集合、天数和规范化偏好再次生成
- **THEN** 直接复用缓存 `guides`，不重复调用搜索

#### Scenario: Daily search limit is silent
- **WHEN** 用户当日搜索次数超过上限
- **THEN** 返回空 `guides`，接口成功，额度不额外扣除

### Requirement: Preparations generation and cache
AI 行程生成 MUST NOT 再产出或缓存“行前准备”内容；模型输出中的 `preparations` MUST 被忽略。生成响应中的 `preparations` 字段 MUST 保留以兼容既有客户端，但 MUST 始终为空值（空字符串与空数组）。国家签证、货币、插头、最佳季节与旅行提示统一由 `ai/country-guide` 能力按国家提供。该变更 MUST NOT 影响行程生成的其他行为、扣费或去重规则。

#### Scenario: Preparations accompany itinerary
- **WHEN** 用户成功生成行程
- **THEN** 响应中的 `preparations` 五项均为空字符串或空数组，不再包含模型生成的行前准备文本

#### Scenario: Model output is ignored
- **WHEN** LLM 返回包含 `preparations` 的 JSON
- **THEN** 系统忽略该字段，不写入响应缓存，也不保存到行程

#### Scenario: Existing saved itineraries remain readable
- **WHEN** 用户查看变更前已保存、且库中存有行前准备文本的行程
- **THEN** 详情接口仍按原有字段返回历史内容，不报错

#### Scenario: Cached preparations do not change billing
- **WHEN** 用户生成行程
- **THEN** 系统不再读写行前准备缓存，扣费、60 秒去重与额度退回规则与变更前一致

#### Scenario: Billing is unchanged
- **WHEN** 用户生成行程
- **THEN** 扣费、60 秒去重与额度退回规则与变更前一致

### Requirement: Draft is not persisted
生成结果 MUST NOT 写入数据库，MUST NOT 点亮地图；只有用户调用保存行程后才进入 `itinerary` 表并影响地图状态。

#### Scenario: Generated draft leaves no record
- **WHEN** 用户生成行程但未保存
- **THEN** 数据库中不存在该行程，地图保持原状态

#### Scenario: Generated draft does not light the country
- **WHEN** 用户生成日本行程后直接返回地图
- **THEN** 日本仍为暗灰

### Requirement: Generation response
成功响应 MUST 返回 `requestId`、`countryCode`、`startDate`/`endDate`、按天行程、`guides`、`preparations`、`warnings`、`deduplicated` 和额度摘要；命中 60 秒去重时 MUST 额外返回 `X-Dedup: hit`。

#### Scenario: Response contains quota summary
- **WHEN** 生成成功
- **THEN** 响应包含本次扣减来源、`freeUsedToday`、`freeLimit` 和 `packBalance`

#### Scenario: Dedup header is present on hit
- **WHEN** 请求命中 60 秒去重
- **THEN** 响应头包含 `X-Dedup: hit`，`deduplicated=true`
