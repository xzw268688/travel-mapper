## MODIFIED Requirements

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

