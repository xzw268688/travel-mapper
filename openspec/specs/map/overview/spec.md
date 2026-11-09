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

### Requirement: Chinese country names on the map
世界地图的悬浮提示与悬停高亮标签 MUST 显示中文国家名。名称解析 MUST 按以下优先级：目录中该国家的中文名 → 内置中英对照表中的中文名 → 底图原始名称。展示 MUST NOT 因缺少中文映射而报错或留空。系统 MUST NOT 因该展示需求调整任何边界、归属或地区称谓。

#### Scenario: Lit country shows catalog Chinese name
- **WHEN** 用户将鼠标移到有行程或游记的国家
- **THEN** 提示与该国高亮标签显示目录中的中文名，并保持行程/游记/总计口径

#### Scenario: Unlit country shows translated Chinese name
- **WHEN** 用户将鼠标移到没有业务数据的国家
- **THEN** 提示显示中文名与“暂无记录”

#### Scenario: Missing translation falls back to source name
- **WHEN** 底图要素在对照表中没有对应中文名
- **THEN** 提示与标签回退显示底图原始名称，不报错

#### Scenario: Boundaries are not altered
- **WHEN** 用户查看地图
- **THEN** 国家边界与归属完全来自底图，系统不因显示中文名而修改或新增边界

#### Scenario: Fallback list keeps working
- **WHEN** 底图加载失败并降级为国家列表
- **THEN** 列表仍显示中文国家名并可打开详情

### Requirement: Polar regions on the world map
世界地图 MUST 在同一张底图中展示南极洲与北极地区：

- 南极洲 MUST 以多边形区域显示在地图底部。
- 北极地区 MUST 以地图顶部约 88°N、0° 处的可点击点标记显示，MUST NOT 再以填充区域或横贯地图顶部的色带出现。
- 北极标记 MUST 始终显示“北极地区”文字标签。
- 北极标记颜色 MUST 跟随三态：无记录灰色、有行程黄色、有游记绿色；悬浮 MUST 显示中文名与行程/游记/总计数；点击 MUST 打开“北极地区”抽屉。
- 底图纬度范围 MUST 继续容纳南极洲（-90°~90°），主地图自动缩放。
- 极地区域的展示 MUST NOT 修改其他国家的边界、归属或称谓。

#### Scenario: Antarctica is visible
- **WHEN** 用户打开世界地图
- **THEN** 南极洲作为可点击区域显示在地图底部

#### Scenario: Arctic is visible
- **WHEN** 用户打开世界地图
- **THEN** 北极地区以带“北极地区”标签的点标记显示在地图顶部，地图上不再出现横贯顶部的色带

#### Scenario: Polar regions open drawers
- **WHEN** 用户点击南极洲区域或北极点标记
- **THEN** 分别打开“南极洲”或“北极地区”抽屉

#### Scenario: Polar regions follow three-state colors
- **WHEN** 用户在南极洲或北极地区记录行程或游记
- **THEN** 对应区域或标记按同一规则显示黄色或绿色，其余国家不受影响

#### Scenario: Arctic marker shows counts on hover
- **WHEN** 用户悬浮在北极点标记上
- **THEN** 提示显示“北极地区”与行程/游记/总计数

#### Scenario: Other boundaries are unchanged
- **WHEN** 地图加载极地展示
- **THEN** 其他国家的边界与归属保持原样

### Requirement: Drawer trip status groups
国家抽屉 MUST 按旅行的有效状态分组展示：

- `IN_PROGRESS` → “进行中的旅行”分组，仅当存在进行中的旅行时显示；
- `PLANNED` → “计划中的旅行”分组；
- `COMPLETED` → “已完成的旅行”分组。

手动标记的旅行 MUST 显示“手动标记”标签；自动判断为计划中且已过期的旅行 MUST 显示“已过期”标签。抽屉 MUST NOT 单独展示未加入旅行汇总；所有旅行卡片 MUST 使用有效状态，而不是仅使用持久化的手动状态。

#### Scenario: In-progress group appears
- **WHEN** 某国家存在今天正处于起止日期内的旅行
- **THEN** 抽屉显示“进行中的旅行”分组，该旅行不在“计划中的旅行”或“已完成的旅行”中

#### Scenario: Manual tag is shown
- **WHEN** 旅行状态由用户手动设置
- **THEN** 卡片显示“手动标记”标签与“恢复自动”按钮

#### Scenario: Expired tag is shown
- **WHEN** 自动判断为计划中且结束日期已过、也没有游记/消费
- **THEN** 卡片显示“已过期”标签

#### Scenario: Ungrouped summary is removed
- **WHEN** 抽屉中存在未加入旅行的历史数据
- **THEN** 抽屉不显示“另有 N 条未加入旅行”提示，历史数据迁移后以未命名旅行卡片展示

#### Scenario: Zero statistics are hidden
- **WHEN** 旅行只有行程、没有游记和消费
- **THEN** 卡片只显示“行程 N”，不显示“游记 0”“花费 ¥0.00”

#### Scenario: Missing dates are hidden
- **WHEN** 旅行没有任何可用日期（包括由记录日期补出的日期）
- **THEN** 卡片不显示日期占位行
