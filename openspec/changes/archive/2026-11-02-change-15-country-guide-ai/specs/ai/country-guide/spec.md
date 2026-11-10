## Purpose

按国家提供可缓存的 AI 旅行攻略：国家介绍、推荐景点与旅游须知，通过 SSE 流式返回，一次生成只扣 1 次额度，并在国家抽屉中按需展示。

## ADDED Requirements

### Requirement: Country guide request validation
生成接口 MUST 要求登录，并包含合法的 `requestId`（UUID）与两位字母的 `countryCode`；国家 MUST 存在且处于启用状态。非法请求 MUST 返回 `2001`，国家不可用 MUST 返回 `2002`，且 MUST NOT 扣减额度或调用 LLM。

#### Scenario: Invalid requestId is rejected
- **WHEN** 用户提交非 UUID 的 `requestId`
- **THEN** 系统返回 `2001`，不建立 SSE 流

#### Scenario: Unknown country is rejected
- **WHEN** 用户请求不存在或已停用的国家
- **THEN** 系统返回 `2002`，不扣额度

#### Scenario: Anonymous user cannot generate
- **WHEN** 未登录用户调用生成接口
- **THEN** 系统返回 `401`，不扣额度

### Requirement: Country-level 24-hour cache
系统 MUST 以国家为维度缓存完整攻略 24 小时，缓存对所有用户共享。缓存命中 MUST 直接返回完整攻略且 `cached=true`，MUST NOT 扣减额度、MUST NOT 调用 LLM。缓存读取接口 MUST 不扣额度，并在无缓存时返回 `available=false`。缓存 MUST NOT 因单个用户的查看或生成次数而改变 TTL。

#### Scenario: First generation fills cache
- **WHEN** 某国无缓存且用户生成成功
- **THEN** 系统写入 24 小时缓存，响应 `cached=false`

#### Scenario: Second user hits cache for free
- **WHEN** 另一用户在国家缓存有效期内打开同一国家
- **THEN** 返回缓存攻略，`cached=true`，该用户额度不变

#### Scenario: Missing cache reports unavailable
- **WHEN** 用户打开一个从未生成过攻略的国家
- **THEN** 缓存读取接口返回 `available=false`，不报错、不扣额度

### Requirement: Quota deduction for guide generation
缓存未命中时，系统 MUST 在调用 LLM 前按与其他 AI 能力相同的顺序检查并扣减 1 次额度；额度不足 MUST 返回 `2011`，MUST NOT 建立 SSE 流或调用 LLM。一次生成 MUST 只扣 1 次额度，覆盖国家介绍、推荐景点、旅游须知三块内容。

#### Scenario: Exhausted quota blocks generation
- **WHEN** 普通用户当日免费额度已用完且没有次数包
- **THEN** 系统返回 `2011`，LLM 未被调用

#### Scenario: One charge covers all three sections
- **WHEN** 用户成功生成一次攻略
- **THEN** 三块内容全部返回，额度只减少 1

#### Scenario: Concurrent generation does not double charge
- **WHEN** 两个请求同时尝试生成同一无缓存国家
- **THEN** 只有一个请求调用 LLM 并扣费，另一个 MUST 返回 `3001` 或读取已完成的缓存

### Requirement: Guide SSE event protocol
生成成功时响应 MUST 为 `text/event-stream`，并按顺序发送 `meta`、若干 `delta`，以及 `done`/`error` 之一。`meta` MUST 包含 `requestId`、`quotaSource` 与 `cached`；`delta` MUST 包含模型输出的文本增量；`done` MUST 包含结构化攻略 `guide` 与 `cached`；`error` MUST 包含错误码与用户可读消息。缓存命中的响应 MUST 至少发送 `meta`（`cached=true`）与携带完整 `guide` 的 `done`。

#### Scenario: Normal stream renders progressively
- **WHEN** 缓存未命中且生成成功
- **THEN** 前端依次收到 `meta`、多个 `delta` 和包含三块结构化内容的 `done`

#### Scenario: Cache hit completes immediately
- **WHEN** 缓存命中
- **THEN** 前端收到 `cached=true` 的 `meta` 与完整 `guide`，无需等待 LLM

#### Scenario: Stream timeout terminates
- **WHEN** LLM 在 120 秒内没有完成
- **THEN** 流以 `error` 结束，前端可重试

### Requirement: Structured guide content
攻略 MUST 包含三块内容：`intro`（国家介绍）、`attractions`（推荐景点，每项含名称与描述）、`notice`（旅游须知，含签证、货币、插头、最佳季节与最多 5 条提示）。模型输出 MUST 被解析为上述结构；缺失字段 MUST 使用空字符串或空数组；若三块内容全部为空，系统 MUST 视为生成失败并按失败规则处理。攻略内容 MUST 使用简体中文。

#### Scenario: Valid output is parsed
- **WHEN** 模型返回包含三个固定章节的文本
- **THEN** 系统解析出 `intro`、`attractions` 与 `notice`，`done` 事件返回结构化结果

#### Scenario: Missing notice fields degrade gracefully
- **WHEN** 模型未给出插头或最佳季节
- **THEN** 对应字段为空字符串，其余内容正常返回

#### Scenario: Empty content is a failure
- **WHEN** 模型输出无法解析出任何有效内容
- **THEN** 系统返回 `error`，未输出 `delta` 时退回额度

### Requirement: Refund boundaries
未向客户端输出任何 `delta` 即失败或被断开时，系统 MUST 退回本次额度；已经输出至少一个 `delta` 后失败或断开 MUST NOT 退回；同一 `requestId` 的退回 MUST 幂等。缓存命中 MUST NOT 产生扣减或退回。

#### Scenario: Failure before output refunds
- **WHEN** LLM 在第一个 `delta` 前报错
- **THEN** 本次扣减被退回，响应以 `error` 结束

#### Scenario: Failure after partial output keeps charge
- **WHEN** 已输出至少一个 `delta` 后连接断开
- **THEN** 本次额度不退回

#### Scenario: Repeated refund is idempotent
- **WHEN** 同一 `requestId` 的退回逻辑触发两次
- **THEN** 不产生额外退回

### Requirement: Country drawer guide panel
国家抽屉 MUST 提供「AI 国家攻略」区域，包含国家介绍、推荐景点、旅游须知三块。无缓存时 MUST 显示「生成攻略」入口并提示消耗 1 次额度；有缓存时 MUST 直接展示缓存内容。生成中 MUST 流式展示文本；完成后 MUST 用结构化内容替换流式文本并按三块分区渲染。失败时 MUST 显示可重试的错误提示。移动端 MUST 可正常阅读与操作。

#### Scenario: Empty country offers generation
- **WHEN** 用户打开一个没有攻略缓存的国家抽屉
- **THEN** 显示生成入口与额度提示，不自动生成

#### Scenario: Cached guide is shown on open
- **WHEN** 用户打开已有缓存的国家抽屉
- **THEN** 无需点击即可看到三块攻略内容

#### Scenario: Streaming shows progress
- **WHEN** 用户点击生成且模型持续输出
- **THEN** 抽屉内按增量显示文本，并禁用重复提交

#### Scenario: Failure offers retry
- **WHEN** 生成以 `error` 结束
- **THEN** 显示错误信息与重试入口，额度按退回规则处理