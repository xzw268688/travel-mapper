## Why

抽屉只按时间列出归档，无法区分“计划中的旅行”和“已经完成的旅行”；首页也只有地图视图，看不到按时间先后排列的足迹。用户希望能手动确认归档状态，并用时间线串起全部旅行。

## What Changes

- 新增 `trip.status`（`PLANNED` / `COMPLETED`），默认 `PLANNED`；提供管理员…（用户本人）手动切换接口。
- 抽屉把归档分为「计划中」「已完成」两组，卡片上可直接切换状态。
- 归档详情页显示状态并可切换。
- 地图首页新增「地图 / 时间线」视图切换；时间线按归档日期正序连接节点，点击进入归档详情。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `trips/grouping`: 新增“归档状态”的要求；新增“时间线视图”的要求。

## Impact

- 数据库：新增 `V6__trip_status.sql`。
- 后端：`Trip` 实体、`TripService`、`TripController`、`MapService`、抽屉 DTO。
- 前端：`MapView`（抽屉分组 + 时间线）、`TripDetailView`、`api/trips.ts`。
- 文档：API、SRS、README。
