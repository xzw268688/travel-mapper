## Why

行程、游记、记账在数据上都挂在同一个 `trip_id` 下，但界面把它们拆成三个独立入口，用户看一趟旅行要来回切换。地图抽屉已经按旅行聚合，却只能看不能进。需要一个“旅行详情页”，让一次旅行的行程、游记、花费在同一页出现，并能从那里继续录入。

## What Changes

- 新增 `GET /api/trips/{id}` 聚合接口：返回旅行概览、汇总、行程列表、游记列表、最近消费与合计；非本人返回 `2015`。
- 新增 `/trips/:id` 旅行详情页：概览 + 三个可跳转区块；地图国家抽屉中的旅行名可点击进入。
- `/expenses` 支持 `?tripId=` 自动筛选；`/notes` 增加“旅行”下拉并在保存时传 `tripId`；`/plan` 保存时可选“新建旅行 / 归到现有旅行”。
- 保留三个独立导航入口，不合并页面。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `trips/grouping`: 新增“旅行详情聚合”要求，定义聚合接口字段、条数上限、越权行为，以及从明细页带旅行回到录入页的联动。

## Impact

- **后端**：`TripController`、`TripService`、`ExpenseMapper`（旅行消费合计查询）、新增 DTO；集成测试。
- **前端**：新增 `TripDetailView.vue`、`api/trips.ts` 扩展、`MapView` 抽屉链接、`NotesView` 旅行选择、`PlanView` 旅行选择、`ExpensesView` 查询参数。
- **文档**：API 第 4 章新增接口、SRS 功能清单补充、`frontend/README.md`。
- **不做**：旅行改名/删除界面、合并导航、改动未归组规则。