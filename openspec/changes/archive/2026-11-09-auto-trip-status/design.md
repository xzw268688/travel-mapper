## Context

见 proposal.md - Why。相关现状：

- `TripStatus` 只有 `PLANNED` / `COMPLETED`，`trip.status` 由 `PUT /api/trips/{id}/status` 手动保存。
- 旅行状态在 `TripService.list/detail`、`MapService.drawer`、时间线兜底节点中直接读取 `trip.status`。
- 强制删除旅行会把 `itinerary` / `travel_note` / `expense` 的 `trip_id` 置为 NULL。
- 国家抽屉前端按 `status` 分为计划中/已完成，并在底部显示未加入旅行统计。
- 时间线（`itinerary-timeline` 变更）使用旅行级状态。
- 当前迁移版本到 V14，新增迁移从 V15 开始。

## Goals / Non-Goals

**Goals:**

- 后端统一计算 `effectiveStatus`（PLANNED / IN_PROGRESS / COMPLETED）与 `expired`。
- 手动状态优先，支持恢复自动。
- 强制删除旅行不再产生 `trip_id IS NULL` 的新数据。
- 迁移现有孤儿数据并尽量保留用户手动状态意图。
- 前端三组展示、手动/过期标签与恢复自动按钮。

**Non-Goals:**

- 不改变国家三态（无记录/仅有行程/有游记）。
- 不改变游记/消费的自动归组窗口规则。
- 不新增第四种有效状态。
- 不修改分享页聚合口径。

## Decisions

### 1. 新增 `status_manual`，有效状态在读取时计算

`trip.status` 继续保存手动状态；新增 `status_manual`：

- `status_manual=1`：`effectiveStatus = trip.status`，不再自动变化。
- `status_manual=0`：忽略 `trip.status`，按日期与记录计算。

有效状态不落库，避免在游记/消费频繁增删时维护派生字段。所有对外 DTO 增加：

- `effectiveStatus`：`PLANNED` / `IN_PROGRESS` / `COMPLETED`
- `statusManual`：是否手动
- `expired`：仅自动计划中、结束日期已过且无记录时为 true

`trip.status` 保留在 DTO 中用于兼容，但前端以 `effectiveStatus` 为准。

### 2. 状态推导规则

公共组件 `TripStatusResolver` 输入：手动状态、`statusManual`、旅行起止、是否有游记/消费、记录最早/最晚日期、今天。

```
if statusManual: effective = storedStatus
else if hasRecords:
    if startDate != null and endDate != null:
        today > endDate            -> COMPLETED
        today >= startDate         -> IN_PROGRESS
        else                       -> PLANNED
    else:
        effectiveStart = min(record dates)
        effectiveEnd = max(record dates)
        today >= effectiveEnd      -> COMPLETED
        today >= effectiveStart    -> IN_PROGRESS
        else                       -> PLANNED
else:
    effective = PLANNED

expired = !statusManual && effective == PLANNED
          && endDate != null && today > endDate && !hasRecords
```

记录日期口径：游记使用 `DATE(created_at)`，消费使用 `expense_date`。有明确旅行日期时优先使用旅行日期，不被记录日期覆盖。

### 3. 手动设置与恢复自动

- `PUT /api/trips/{id}/status`：保存 `status`，设置 `status_manual=1`。
- 新增 `DELETE /api/trips/{id}/status`：设置 `status_manual=0`，返回重新计算后的有效状态。
- 非本人仍返回 `2015`；非法状态仍返回 `2001`。
- 响应 DTO 增加 `statusManual`，前端据此显示“手动标记”和“恢复自动”。

### 4. 强制删除旅行改为迁移到未命名旅行

`DELETE /api/trips/{id}?force=true`：

1. 计算原旅行挂载记录的最早日期（行程 `start_date`、游记 `DATE(created_at)`、消费 `expense_date`）。
2. 新建“未命名旅行 · yyyy-MM”（无记录时不创建）；`status_manual=0`。
3. 把行程/游记/消费的 `trip_id` 更新为新旅行。
4. 重算新旅行日期（仅由行程派生）。
5. 软删除原旅行。
6. 返回 `newTripId`、`newTripName` 与迁移数量。

没有挂载数据时直接软删除，不创建新旅行。原响应中的 `detached` 保留为迁移数量，避免前端立即报错。

### 5. V15 数据迁移

`V15__trip_status_auto.sql`：

- `ALTER TABLE trip ADD COLUMN status_manual TINYINT(1) NOT NULL DEFAULT 0`。
- 历史状态只在与自动 Completion 判断不一致时保留为手动：
  - `status='COMPLETED'` 但按记录/日期自动判断不是 Completed → `status_manual=1`
  - `status='PLANNED'` 但按记录/日期自动判断是 Completed → `status_manual=1`
  - 其他行 `status_manual=0`
- 孤儿数据迁移：使用 MySQL 存储过程按 `(user_id, country_code)` 遍历 `trip_id IS NULL` 的记录：
  - 计算最早/最晚记录日期，名称取“未命名旅行 · yyyy-MM”；
  - 新建 `status_manual=0` 的旅行，把该组行程/游记/消费全部挂入；
  - 无记录时不会出现分组。
- 迁移完成后删除临时存储过程。

### 6. 查询与 DTO 接入

- `TripService.list/detail`：查询增加 `has_records`、`record_start`、`record_end`，调用 Resolver 填充有效状态。
- `MapService.drawer`：TripRow 增加同样字段，TripGroup 返回 `effectiveStatus`、`statusManual`、`expired`。
- `ItineraryService.timeline`：行程节点的旅行级状态与兜底 TRIP 节点使用有效状态，并返回 `statusManual`/`expired`。
- 前端 `api/trips.ts`、`api/itinerary.ts` 增加字段类型。

### 7. 前端分组与标签

- `MapView` 抽屉按 `effectiveStatus` 计算 `inProgressTrips` / `plannedTrips` / `completedTrips`。
- “进行中的旅行”仅在有数据时显示；计划中/已完成保持现有展示。
- 卡片标签：
  - `statusManual=true` → “手动标记”，悬浮/触碰显示“你手动改过状态，系统不会再自动改”
  - `expired=true` → “已过期”
- 操作：
  - 手动设置仍调用 `PUT /trips/{id}/status`
  - `statusManual=true` 时显示“恢复自动”，调用 `DELETE /trips/{id}/status`；悬浮/触碰提示“改回系统自动判断（根据日期、游记和消费）”
- 移除 `ungroupedNote` 与“另有 N 条未加入旅行”提示。
- 旅行详情页同步展示有效状态与恢复自动。

### 8. 空旅行自动清理

新增 `TripService.deleteIfEmpty(tripId)`：

- 统计该旅行下未删除的行程、游记、消费数量。
- 全部为 0 时软删除旅行，返回 `true`。
- 在行程、游记、消费删除的同一事务内调用。
- 行程/游记/消费删除响应增加 `tripDeleted` 字段。
- V15 迁移软删除现有空旅行。

前端在删除最后一条内容后，根据 `tripDeleted=true` 提示“旅行已无内容，已自动清理”，并返回地图或国家抽屉。

### 9. 有效展示日期与零值隐藏

- 后端 DTO 增加 `effectiveStartDate`、`effectiveEndDate`：
  - 优先使用旅行自身起止日期；
  - 没有行程日期时使用游记 `DATE(created_at)` 与 `expense_date` 的最早/最晚值；
  - 都没有时为 null。
- 旅行详情页与抽屉卡片：
  - 日期两者都为空时不显示日期行；
  - 旅行详情页固定显示四个统计框（行程、游记、消费笔数、合计花费），0 也显示，避免单项时布局失衡；
  - 抽屉旅行卡片仍只渲染非零项，避免文字行出现大量 0；
  - 全部为 0 的空旅行已被自动清理，不会出现四个 0 的详情页。

### 10. 测试策略

- 后端集成测试覆盖：
  - 出发前消费保持计划中；旅行中为进行中；结束后有记录为已完成；
  - 只有行程且结束日期已过 → 计划中 + expired；
  - 无日期记录当天完成；未来记录保持计划中；
  - 手动完成/手动计划优先；恢复自动后重新推导；
  - 强制删除旅行创建未命名旅行并迁移记录；
  - 孤儿数据迁移后抽屉以未命名旅行展示。
- 前端构建 + Playwright 截图验证三组、标签与按钮。

## Risks / Trade-offs

- **记录日期补全可能让当天写游记的历史旅行直接完成** → 这是刻意避免“当天显示进行中”的取舍；用户仍可手动改回计划中。
- **出发前预订消费不会提前完成** → 因为日期未到开始日期；符合需求。
- **存储在 `trip.status` 的旧值可能被忽略** → 迁移只把不一致的历史状态标记为手动，其余交给自动判断；用户可在 UI 恢复自动/手动覆盖。
- **存储过程迁移复杂** → 在干净库与已有库都执行验证；失败时 Flyway 会阻止启动，便于发现。
- **多处查询重复计算有效状态** → 统一使用 `TripStatusResolver`，避免规则漂移。
- **前端分组从 `status` 切换到 `effectiveStatus`** → 保留旧字段兼容，逐步替换所有使用点。
- **强制删除语义变化** → API 文档与测试同步更新；旧客户端仍可拿到 `detached` 计数。
- **自动删除空旅行可能让用户在删除最后一条内容后“迷路”** → 删除响应返回 `tripDeleted`，前端提示并离开已失效详情页。
- **有效展示日期与真实旅行日期不同** → 只在没有行程日期时使用记录日期，且仅用于展示，不写回 `trip.startDate/endDate`。

## Migration Plan

1. 发布 V15：新增字段、标记历史手动状态、迁移孤儿数据。
2. 后端上线有效状态计算与恢复自动接口。
3. 前端切换到 `effectiveStatus` 分组与标签。
4. 回滚：恢复旧代码后，`status_manual` 与未命名旅行数据保留但不影响旧逻辑；如需彻底回滚，删除本次新增字段/未命名旅行需单独脚本。

## Open Questions

（无）