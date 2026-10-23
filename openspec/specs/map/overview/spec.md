# Map Overview Specification

## Purpose

定义世界地图的三态点亮、悬浮计数与国家侧边抽屉的组装规则，为首页提供唯一的聚合读模型，并保证用户只能看到自己的旅行足迹。

## Requirements

### Requirement: Three-state map statistics
系统 MUST 按当前用户聚合未删除数据：存在游记的国家为 `GREEN`；无游记但存在已保存正式行程的国家为 `YELLOW`；两者都无的国家 MUST NOT 返回，由前端渲染为暗灰。同一国家 MUST 只返回一条统计。

#### Scenario: Note makes country green
- **WHEN** 某国存在至少一篇未删除游记（含私密）
- **THEN** 该国状态为 `GREEN`，即使同时存在行程

#### Scenario: Only itineraries makes country yellow
- **WHEN** 某国没有游记但至少有一条已保存行程
- **THEN** 该国状态为 `YELLOW`

#### Scenario: Draft does not light up
- **WHEN** 用户只生成了 AI 结果但没有保存行程
- **THEN** 该国不出现在统计中，保持暗灰

#### Scenario: Deleted data does not count
- **WHEN** 某国最后一条行程或游记被删除
- **THEN** 统计按剩余数据重算；全部删除后该国不再返回

#### Scenario: Deleted users are excluded
- **WHEN** 用户账号已注销
- **THEN** 其数据不计入任何地图统计

### Requirement: Expired plan flag
`expired` MUST 仅在 `YELLOW` 国家、且该国所有已保存行程的 `endDate` 都早于今天时返回 `true`；只要存在一条未过期行程，或国家为 `GREEN`，`expired` MUST 为 `false`。

#### Scenario: All itineraries expired
- **WHEN** 某国只有行程、且所有行程结束日期都已过、没有游记
- **THEN** 该国为 `YELLOW` 且 `expired=true`，前端标记“计划已过期”

#### Scenario: One unexpired itinerary
- **WHEN** 某国有多条行程，其中至少一条尚未结束
- **THEN** 该国保持 `YELLOW` 且 `expired=false`

### Requirement: Map counts and hover data
每条国家统计 MUST 返回 `itineraryCount`、`noteCount` 和 `totalCount`，其中 `totalCount = itineraryCount + noteCount`；悬浮提示 MUST 使用与侧边抽屉相同的口径。

#### Scenario: Counts include all trips in the country
- **WHEN** 某国有 3 条行程、2 篇游记，分布在 2 次旅行中
- **THEN** 返回一条统计，`itineraryCount=3`、`noteCount=2`、`totalCount=5`

#### Scenario: Private note counts for owner
- **WHEN** 用户发布一篇私密游记
- **THEN** 本人地图的 `noteCount` 增加，其他国家不受影响

### Requirement: Country drawer structure
国家侧边抽屉 MUST 返回国家状态、汇总、按旅行分组的数据和 `ungrouped`；旅行 MUST 按 `startDate` 倒序，内部按城市分组；同一旅行内的多城市行程 MUST 在涉及的每个城市下各出现一次；`ungrouped` MUST 同时包含 `trip_id IS NULL` 的行程、游记和消费，无数据时返回空数组而不是省略字段。

#### Scenario: Trips are grouped before cities
- **WHEN** 用户打开日本抽屉且存在多次旅行
- **THEN** 响应按“旅行 → 城市 → 行程/游记”层级返回，最近旅行在前

#### Scenario: Multi-city itinerary appears in each city
- **WHEN** 一条行程包含东京和大阪
- **THEN** 该行程在东京和大阪两个城市分组下各出现一次，前端按 `id` 去重链接

#### Scenario: Ungrouped includes itineraries
- **WHEN** 删除旅行导致行程 `trip_id` 变为 NULL
- **THEN** 该行程出现在 `ungrouped.itineraries`，不会从抽屉中消失

#### Scenario: City label reflects completion
- **WHEN** 某城市下存在游记
- **THEN** `cityLabel=COMPLETED`；只有行程时为 `PLANNED`；只有花费时不生成城市分组

### Requirement: Drawer summary and currency handling
抽屉 MUST 返回 `totalExpenseCny`、各旅行 `subtotalCny`、各城市 `expenseCny`，金额按汇率快照折算；存在未配置汇率的币种时 MUST 返回 `hasUnconfiguredCurrency=true` 和 `unconfiguredCurrencies` 列表；未配置汇率的金额 MUST NOT 被计入总额。

#### Scenario: Unconfigured currency is flagged
- **WHEN** 某国的消费中包含未配置汇率的币种
- **THEN** 汇总只计入已折算金额，并返回 `hasUnconfiguredCurrency=true` 与币种列表

#### Scenario: Subtotal matches trip expenses
- **WHEN** 某旅行下有多笔已折算消费
- **THEN** `subtotalCny` 等于这些消费 `amount_cny` 之和

### Requirement: Drawer privacy and empty state
抽屉 MUST 只返回当前用户的数据，所有查询 MUST 以当前 `userId` 过滤；无任何数据时 MUST 返回完整空结构（含 `trips=[]` 与 `ungrouped` 空数组），不得返回他人数据或权限错误。

#### Scenario: Empty country returns empty structure
- **WHEN** 用户打开一个自己没有数据的国家抽屉
- **THEN** 返回该国名称、灰色状态与空分组，不返回 403 或他人数据

#### Scenario: Trip names stay private
- **WHEN** 任意用户查看抽屉
- **THEN** `tripName` 只对本人可见，分享页不复用该接口

### Requirement: Map client interaction
地图前端 MUST 根据统计结果渲染 `GREEN`、`YELLOW` 与默认暗灰；悬浮 MUST 展示国家名与行程数/游记数/总次数；点击国家 MUST 打开对应抽屉；移动端 MUST 支持点击打开抽屉，并（P1）支持双指缩放与单指拖拽且不与页面滚动冲突。

#### Scenario: Colors match statistics
- **WHEN** 地图加载完成
- **THEN** 有游记的国家显示绿色，仅有行程的国家显示黄色，其余显示暗灰

#### Scenario: Click opens the correct drawer
- **WHEN** 用户点击日本
- **THEN** 打开日本抽屉并展示该国的旅行与未归组数据

#### Scenario: Mobile gestures are usable
- **WHEN** 用户在 iOS Safari 15+、Android Chrome 100+ 或微信内置浏览器中缩放/拖拽地图
- **THEN** 地图正常缩放或移动，页面滚动不被意外触发

### Requirement: Map performance
`GET /map/stats` 与国家抽屉接口的 P95 MUST 小于 500ms（常规数据量下），并 MUST 使用按 `user_id`、`country_code`、`deleted` 建立的关键索引。

#### Scenario: Map loads within budget
- **WHEN** 在常规数据量下请求地图统计与抽屉
- **THEN** P95 响应时间低于 500ms
