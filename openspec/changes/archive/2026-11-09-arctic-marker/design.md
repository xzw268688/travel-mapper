## Context

见 proposal.md - Why。相关现状：

- `MapView.initMap()` 合并 `world.json` + `antarctica.json` + `arctic.json` 后注册 ECharts 地图。
- 北极当前是 `84°N–90°N`、`-180°~180°` 的矩形，等距圆柱投影下必然是顶部全宽色带。
- 现有图表只有一个 `type: 'map'` series，负责国家三态着色、悬浮、点击与 roam。
- `stats` 来自 `GET /api/map/stats`，`statusColor()` 负责三态颜色；`X3` 已在国家目录中。
- 南极洲多边形与 `AQ` 数据保持不变。

## Goals / Non-Goals

**Goals:**

- 北极改为始终显示标签的可点击点标记，消除顶部横条。
- 标记颜色、悬浮计数、点击抽屉与其他国家一致。
- 保持现有地图三态着色、缩放/拖拽与南极洲区域不变。

**Non-Goals:**

- 不修改国家目录、数据库或后端接口。
- 不改变南极洲的展示方式。
- 不为北极重建极地投影或独立小图。

## Decisions

### 1. 使用 map series 的 `markPoint`，而不是 geo + scatter

实现时先尝试了 `geo` + `map series(geoIndex)` + `scatter`，但在当前 ECharts/配置组合下，绑定 `geoIndex` 后国家底图不再渲染，地图变成空白。改为在**现有 map series** 上使用 `markPoint`：

- map series 保持原来的 `roam`、`scaleLimit`、三态着色与点击逻辑不变。
- `markPoint` 使用 `coord: [0, 88]` 直接挂在地图坐标系上，自动跟随缩放/拖拽/resize。
- `markPoint.data` 项携带 `code: 'X3'` 与 `stat`，现有点击与 tooltip 逻辑可以直接复用。

备选方案：

- `geo` + `scatter`：坐标共享更通用，但本次实测底图不渲染，风险高。
- 手动把经纬度换算成像素再放 `graphic`：需要监听 roam/resize 自行重算，容易错位。

### 2. 北极标记配置

- 坐标：`[0, 88]`。精确 90°N 会被地图顶边裁掉一半，88°N 在视觉上仍位于顶部中央。
- 符号：圆形，`symbolSize: 14`，白色描边 + 阴影，颜色由 `statusColor(stat)` 决定。
- 标签：始终显示，`position: 'bottom'`，文本“北极地区”，字号 12、白色半透明底、圆角，文字颜色与圆点一致。
- 数据项：`{ name: 'Arctic', coord: [0, 88], code: 'X3', stat }`，使现有点击处理（读取 `params.data.code`）无需特殊分支即可打开 `X3` 抽屉。
- tooltip：沿用现有 formatter；`params.name` 为 `Arctic`，`countryNameOf()` 会解析为“北极地区”，`stat` 来自标记数据。

### 3. 状态刷新

`refreshStats()` 使用：

```js
chart.setOption({
  series: [{
    id: 'countries',
    data: buildMapData(),
    markPoint: { data: buildArcticMarker() }
  }]
})
```

同时刷新国家颜色与标记颜色，避免只刷新地图数据导致标记颜色滞后。

### 4. 移除北极 GeoJSON

- `loadPolarFeatures()` 只请求 `/antarctica.json`，或改名为 `loadAntarcticaFeature()`。
- 删除 `frontend/public/arctic.json`，并确认无其他引用。
- `mergeGeoFeatures()` 仍保留，用于把南极洲合并进世界底图。

### 5. 校验脚本调整

- 极地 GeoJSON 名称只从 `antarctica.json` 读取（当前只有 `Antarctica`）。
- `polar-regions.json` 中 `Arctic` 是标记而非区域：定义 `MARKER_ONLY_NAMES = new Set(['Arctic'])`，在“极地数据缺少底图区域 / 多余条目”检查中排除它。
- 继续校验 `AQ`/`X3` 代码唯一、中文名存在且与 `world-names-zh.json` 一致。
- V11 迁移同步检查不变。

### 6. 归档顺序

本变更以 MODIFIED 方式修改 `polar-regions` 新增的“Polar regions on the world map”要求。归档时应先归档 `polar-regions`，再归档 `arctic-marker`，确保主规格中存在被修改的要求。

## Risks / Trade-offs

- **`geo` 与 map series 布局不一致** → 两者使用同一 `map` 与默认布局，且 map series 通过 `geoIndex` 绑定；实现后截图与 roam 验证。
- **标记被顶边裁切或标签重叠** → 使用 88°N、标签 `position:'bottom'`；必要时调整 `label.offset`。
- **roam 只作用于 geo，map series 跟随** → 因为 map series 使用 `geoIndex`，交互统一由 geo 控制；验证缩放/拖拽后标记与地图同步。
- **scatter 点击与地图点击冲突** → scatter 位于上层，点击标记只会触发标记数据；其他区域仍由 map series 处理。
- **删除 `arctic.json` 影响校验脚本** → 同步调整脚本，保留 `X3` 目录与名称校验。
- **归档顺序错误** → 在 proposal 与任务中明确先归档 `polar-regions`。

## Migration Plan

1. 前端改动：删除 `frontend/public/arctic.json`，调整 `MapView` 与校验脚本。
2. 无数据库迁移、无后端接口变更。
3. 回滚：恢复 `arctic.json` 与区域合并逻辑即可；目录数据不受影响。

## Open Questions

（无）