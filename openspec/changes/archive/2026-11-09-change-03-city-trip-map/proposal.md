## Why

“世界地图点亮”是 TravelMapper 的核心留存机制，但它依赖两个前置能力：行程/游记必须锁定到国家 + 城市，同一国家的多次旅行必须能归组。没有城市字典、`trip` 归组和地图统计读模型，change-04 保存行程时无法生成 `tripId`，change-05 的游记/消费也无法挂到旅行上，地图更无法展示三态与侧边抽屉。

## What Changes

- 实现国家与城市字典查询：国家按 `sort_order` 返回启用项；城市按国家返回 `ENABLED` 项，统一展示 `城市名（国家名）`；无数据返回空数组。
- 实现城市引用规则：行程/游记必须选择启用城市，记账必选国家、城市可选；新建数据只允许启用城市，历史数据中的停用城市仍正常展示。
- 实现后台城市维护：新增、改名、启用/停用、删除；已被行程/游记/消费引用的城市只能停用，物理删除返回 `2025`。
- 实现 `trip` 归组：首次保存行程自动创建旅行（默认名“国家 · yyyy-MM”），支持改名、挂载已有旅行、跨国家挂载拦截、日期区间重算、删除时 `force` 校验与挂载数据置空。
- 实现地图统计：`GREEN`/`YELLOW` 三态、悬浮计数、`expired` 角标；同一国家多条行程/多次旅行只返回一条统计。
- 实现国家侧边抽屉：按“旅行 → 城市 → 行程/游记”分组，展示旅行小计与城市标签；`ungrouped` 同时返回未归组行程、游记和消费；多城市行程在每个涉及城市下各出现一次。
- 实现地图前端渲染、悬浮提示、点击抽屉与移动端地图手势（手势为 P1）。
- **不包含**行程/游记/消费的 CRUD、AI 生成和分享页；本 change 只提供字典、归组与地图读模型，数据由 change-04/05 写入。

## Capabilities

### New Capabilities

- `catalog/country-city`: 国家/城市字典查询、城市引用校验、展示命名与后台城市维护。
- `trips/grouping`: 旅行自动创建、命名、挂载、日期区间派生、删除与未归组规则。
- `map/overview`: 三态统计、悬浮计数、国家侧边抽屉组装、地图前端交互与移动端手势。

### Modified Capabilities

无。本 change 全部为新增能力。

## Impact

- **新增接口**：`GET /api/countries`、`GET /api/cities`、`GET /api/trips`、`PUT/DELETE /api/trips/{id}`、`GET /api/map/stats`、`GET /api/map/country/{countryCode}`、`GET/POST/PUT/DELETE /api/admin/cities*`（API 文档 4、5、12.12）。
- **数据表**：`country`、`city`、`trip` 由本 change 负责写入与维护；`itinerary`、`travel_note`、`expense` 只作为统计读源（结构由 change-01 提供）。
- **前端**：地图页、国家侧边抽屉、国家/城市选择器、移动端手势。
- **后续依赖**：change-04 保存行程时必须调用本 change 的 `trip` 创建/挂载与日期重算契约；change-05 的游记/消费必须支持继承或手动选择 `trip_id`；地图完整 E2E 需在 change-04/05 产生数据后补测。
- **文档依据**：SRS REQ-CITY-001、REQ-TRIP-001、REQ-MAP-001/002、BR-001/010/011；Design 7.5/7.6/7.14；API 4、5、12.12；DB `country`、`city`、`trip` 及 7.1–7.3 查询。
- **无破坏性变更**。
