## ADDED Requirements

### Requirement: Expense page filter surface
记账页筛选区 MUST 只展示国家、城市、旅行（归档）、分类、日期区间与「查询」「重置」；MUST NOT 展示「仅未归组」筛选。列表查询与重置 MUST NOT 发送 `ungrouped` 参数。后端 MAY 继续接受 `ungrouped` 参数以兼容旧客户端。

#### Scenario: Ungrouped filter is not shown
- **WHEN** 用户打开记账页
- **THEN** 筛选区没有「仅未归组」选项

#### Scenario: Query still works after removal
- **WHEN** 用户按国家、分类或日期筛选并点击「查询」
- **THEN** 列表与汇总按所选条件更新，不发送 `ungrouped`

#### Scenario: Reset clears visible filters
- **WHEN** 用户点击「重置」
- **THEN** 国家、城市、旅行、分类与日期条件被清空并重新查询

### Requirement: Summary and monthly total filter consistency
汇总与月度合计 MUST 支持与列表相同范围的国家、城市、旅行、分类与日期过滤；非法 `category` MUST 返回 `2001`。前端执行查询时 MUST 同步刷新列表、汇总与月度合计，避免筛选条件只作用于列表。

#### Scenario: Category filter applies to summary
- **WHEN** 用户选择分类后查询
- **THEN** 汇总与月度合计只统计该分类

#### Scenario: City filter applies to summary
- **WHEN** 用户选择城市后查询
- **THEN** 汇总与月度合计只统计该城市

#### Scenario: Invalid category is rejected
- **WHEN** 请求汇总时传入未知分类
- **THEN** 系统返回 `2001`

### Requirement: Expense edit form retention
编辑已有消费时 MUST 回填并保留其国家、城市与所属归档；只有在用户主动切换国家、且原城市或归档不属于新国家时 MUST 才清空对应字段。保存 MUST NOT 因回填过程丢失 `cityCode` 或 `tripId`。

#### Scenario: Editing keeps city and trip
- **WHEN** 用户打开一条带城市与归档的消费并直接保存
- **THEN** 记录的城市与归档保持不变

#### Scenario: Country change clears incompatible values
- **WHEN** 用户把国家从泰国改为日本
- **THEN** 不属于日本的城市与归档被清空，仍属于日本的值保留