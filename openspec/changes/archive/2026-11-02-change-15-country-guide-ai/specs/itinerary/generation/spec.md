## MODIFIED Requirements

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