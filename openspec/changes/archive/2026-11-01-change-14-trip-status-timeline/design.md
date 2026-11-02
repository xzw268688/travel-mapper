## Context

见 `proposal.md`。归档 `trip` 已有名称与日期区间，但没有状态；首页只有地图视图。

## Decisions

1. 状态存 `trip.status`，枚举 `PLANNED/COMPLETED`，默认 `PLANNED`；切换接口 `PUT /api/trips/{id}/status`，仅本人可改。
2. 抽屉按状态分组展示；卡片上直接提供“标记完成 / 改回计划中”，点击整卡进入归档详情。
3. 新增 `GET /api/trips/timeline`：返回本人全部归档，按 `start_date`（为空用创建日期）正序，包含国家名与数量统计。
4. 首页用同一页面切换「地图 / 时间线」，时间线懒加载。

## Risks / Trade-offs

- **旧归档默认都是“计划中”** → 用户需要手动标记一次；符合“手动确认最简单”的选择。

## Migration Plan

1. 执行 Flyway V6（新增列，默认值）。
2. 无数据回填；旧数据自动为 `PLANNED`。

## Open Questions

无。
