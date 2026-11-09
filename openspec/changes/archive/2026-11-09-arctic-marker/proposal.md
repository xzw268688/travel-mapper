## Why

当前北极地区以 84°N–90°N 的极冠区域渲染。在等距圆柱投影下，这块区域必然横贯地图顶部；一旦有行程或游记，整条色带被点亮，看起来像横幅而不是地理区域，用户反馈“太奇怪”。北极点本身是一个点，用点标记比填充区域更自然。

## What Changes

- **移除北极区域**：不再加载/合并 `frontend/public/arctic.json`，删除该文件；地图顶部不再出现横条。
- **新增北极点标记**：在地图约 **88°N、0°** 处绘制可点击的圆点/图钉，始终显示“北极地区”文字标签。
- **三态着色**：标记颜色跟随地图状态（灰色无记录、黄色有行程、绿色有游记），悬浮显示中文名与行程/游记/总计数。
- **点击打开抽屉**：点击标记打开“北极地区”抽屉，行为与普通国家一致。
- **保持南极洲**：南极洲仍为地图底部的多边形区域，不做改动。
- **实现方式**：在现有 `map` series 上使用 ECharts `markPoint` 绘制标记，保持国家三态着色与缩放/拖拽逻辑不变。
- **校验与文档**：校验脚本不再要求 `Arctic` GeoJSON，但保留 `X3` 目录与中文名校验；更新 README、本地开发说明与 `map/overview` 规格。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `map/overview`: 修改“Polar regions on the world map”，北极地区从 84°N–90°N 填充区域改为始终显示标签的可点击点标记，颜色与三态一致；南极洲区域保持不变。

## Impact

- 前端：`MapView.vue` 图表结构（geo + map series + scatter）、标记数据与状态刷新；删除 `frontend/public/arctic.json`。
- 脚本：`check-world-names.mjs` 调整极地要素校验，允许 `Arctic` 作为标记而非 GeoJSON 区域。
- 后端/数据库：无变更、无迁移。
- 文档：README、本地开发说明与 OpenSpec 规格。
- 归档顺序：`arctic-marker` 修改的是 `polar-regions` 新增的要求，应先归档 `polar-regions` 再归档本变更。