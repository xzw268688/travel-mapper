## ADDED Requirements

### Requirement: Trip detail aggregation
系统 MUST 提供按旅行聚合的详情接口，返回旅行概览、汇总、行程列表、游记列表与最近消费。行程与游记 MUST 返回全部未删除记录；消费 MUST 只返回最近 10 条，并额外返回消费笔数与 CNY 合计。非本人或不存在旅行 MUST 返回 `2015`。聚合口径 MUST 与地图抽屉一致（同一旅行、同一用户、排除已删除数据）。

#### Scenario: Owner opens trip detail
- **WHEN** 用户请求本人某次旅行的详情
- **THEN** 返回旅行名称、国家、日期区间、行程数、游记数、消费笔数、CNY 合计，以及行程与游记完整列表

#### Scenario: Recent expenses are capped
- **WHEN** 某次旅行有 30 条消费
- **THEN** 响应最多返回最近 10 条消费，同时 `expenseCount=30` 且合计包含全部消费

#### Scenario: Other user's trip is hidden
- **WHEN** 用户请求他人的旅行详情
- **THEN** 系统返回 `2015`，不泄露旅行是否存在

#### Scenario: Unconfigured currency is reported
- **WHEN** 旅行中存在未配置汇率的消费
- **THEN** 汇总返回未配置币种列表，合计只包含已折算部分

### Requirement: Trip-aware entry points
从旅行详情页进入录入页时 MUST 带上旅行上下文：记账页 MUST 按该旅行筛选；游记页 MUST 预选该旅行并在保存时写入 `trip_id`；行程页 MUST 支持将新行程归入该旅行。用户 MUST 仍可在录入页改选其他旅行或“未归组”。

#### Scenario: Expense page filters by trip
- **WHEN** 用户从旅行详情页点击“记一笔”
- **THEN** 记账页自动按该旅行筛选列表与汇总

#### Scenario: Note creation keeps the trip
- **WHEN** 用户从旅行详情页进入游记创建并选择该旅行后保存
- **THEN** 游记的 `trip_id` 为该旅行，出现在旅行详情页

#### Scenario: Itinerary can join an existing trip
- **WHEN** 用户在行程规划页选择“归到现有旅行”并保存
- **THEN** 新行程挂到该旅行下，而不是新建旅行

#### Scenario: User can still choose ungrouped
- **WHEN** 用户在录入页把旅行选择清空
- **THEN** 数据保存为未归组，不强制关联