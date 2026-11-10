## Why

当前旅行状态完全依赖手动切换，和用户的理解不一致：

- 旅行中有消费/游记时，不应提前显示“已完成”；旅行结束后才应完成。
- 出发前记录酒店预订等消费时，不应提前完成。
- 用户规划了、也去了，但不写游记/不记账时，仍需要能手动标记完成。
- 强制删除旅行会把数据置为“未加入旅行”，抽屉里只能显示一行数量，用户不知道这是什么。

需要把状态改为“按内容与日期自动推导，并允许手动覆盖”，同时消除未加入旅行的散落状态。

## What Changes

- **三种有效状态**：`PLANNED`（计划中）、`IN_PROGRESS`（进行中）、`COMPLETED`（已完成），由后端计算。
- **自动规则**：
  - 有游记或消费，且旅行结束日期已过 → 已完成。
  - 今天在旅行起止日期内（含首尾） → 进行中，即使已有消费/游记。
  - 未开始，或没有游记/消费 → 计划中。
  - 只有行程且结束日期已过 → 计划中，并标记 `expired=true`（前端显示“已过期”）。
  - 没有旅行日期时，用游记创建日期与消费日期补出有效起止；记录日期当天或更早 → 已完成，未来记录 → 计划中。
- **手动覆盖**：
  - `PUT /api/trips/{id}/status` 设置手动状态并标记 `statusManual=true`。
  - 新增 `DELETE /api/trips/{id}/status` 恢复自动判断。
  - 手动状态优先，直到用户恢复自动。
- **抽屉三组**：进行中的旅行（有数据时显示）、计划中的旅行、已完成的旅行；显示“手动标记”“已过期”标签；移除“另有 N 条未加入旅行”提示。
- **删除旅行**：`force=true` 时不再把数据置空，而是创建 **“未命名旅行 · yyyy-MM”** 并迁移原数据；返回新旅行信息；无数据时直接删除。
- **历史数据**：迁移现有 `trip_id IS NULL` 的行程/游记/消费，按“用户 + 国家”归入未命名旅行；现有旅行状态只在和自动判断不一致时保留为手动。
- **空旅行清理**：当旅行下最后一条行程/游记/消费被删除后，自动软删除该旅行；升级迁移时清理现有空旅行。删除响应增加 `tripDeleted`，前端提示并离开详情页。
- **精简展示**：旅行详情页与抽屉卡片只显示非零统计；没有旅行日期时显示由游记/消费补出的有效日期，仍没有则隐藏日期行。
- **时间线**：行程节点与兜底旅行节点使用有效状态。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `trips/grouping`: 修改“Archive status”为自动推导 + 手动覆盖；修改“Trip deletion and detaching”为迁移到未命名旅行；修改“Derived trip date range”与新增“Empty trip cleanup”处理空旅行；新增“Orphan records migration to unnamed trips”。
- `itinerary/management`: 修改“Itinerary deletion”：最后一条内容删除后允许自动软删除空旅行。
- `map/overview`: 新增“Drawer trip status groups”，规定三组旅行、标签、非零统计与移除未加入旅行汇总。

## Impact

- 数据库：新增 `V15__trip_status_auto.sql`（`status_manual` 字段、历史状态迁移、孤儿数据归组）。
- 后端：`Trip` 实体、`TripService`、`TripController`、`MapService`、`ItineraryService` 时间线查询、相关 DTO。
- 前端：`MapView.vue` 抽屉分组与标签、`api/trips.ts` / `api/itinerary.ts` 类型。
- 测试：状态推导、手动覆盖/恢复、强制删除、孤儿迁移、抽屉分组。
- 文档：API、README、OpenSpec 规格。