## Why

旅行归组目前要用户理解“旅行”这个概念，并在游记、记账、行程保存时手动选择；多数人每个国家只去一次，这层选择没有价值。改为系统按“国家 + 时间”自动归组后，用户不需要手动管理，仍能保留“同一国家多次旅行分开统计”的能力。

## What Changes

- **BREAKING（前端行为）** 移除游记创建页与行程保存页的“旅行”下拉；归组完全由系统决定。
- 行程保存时按“同国家、日期间隔 ≤30 天”匹配已有旅行，匹配不到才新建。
- 消费按 `expense_date`、游记按 `created_at` 自动归到覆盖它的旅行；游记允许旅行结束后 30 天内；匹配不到进入“未归组”。
- 保留 `trip` 表与 `tripId` 字段，接口结构不变。
- 新增一次性重算接口 `POST /api/admin/trips/recluster`，把历史数据按新规则重新聚类。
- 编辑日期不重新归类。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `trips/grouping`: 自动创建改为“按国家 + 30 天窗口匹配”；手动归组改为“按日期自动归类 + 未归组回退”；新增历史数据重算要求。

## Impact

- 后端：`TripService`（匹配与重建）、`ItineraryService`、`NoteService`、`ExpenseService`、新增 `AdminTripController`。
- 前端：`NotesView`、`PlanView` 移除旅行下拉；`TripDetailView` 的“写游记/规划行程”不再带 `tripId`（记账筛选保留）。
- 数据：执行一次重算；不删表、不改字段。
- 文档：API、SRS、README、`scripts/maintenance/recluster-trips.sh`。
