## 1. 北极点标记

- [x] 1.1 从 `MapView` 的底图加载中移除 `/arctic.json`，删除 `frontend/public/arctic.json`；确认南极洲区域仍正常渲染、地图不再出现顶部横条
- [x] 1.2 在现有 map series 上新增 `markPoint`，绘制 88°N/0° 的北极标记；确认国家底图、三态着色、缩放与拖拽正常
- [x] 1.3 标记始终显示“北极地区”标签，颜色跟随三态；点击打开 `X3` 抽屉，悬浮显示行程/游记/总计数；确认 `refreshStats()` 同时刷新地图与标记颜色

## 2. 校验与文档

- [x] 2.1 调整 `scripts/check-world-names.mjs`：极地 GeoJSON 只校验南极洲，允许 `Arctic` 作为标记条目；继续校验 `AQ`/`X3` 代码唯一、中文名与 V11 同步；运行后退出码为 0
- [x] 2.2 更新 README 与 `docs/ops/local-development.md`：说明北极改为点标记、南极洲仍为区域；运行 `openspec validate arctic-marker --strict` 通过

## 3. 实际运行验收

- [x] 3.1 启动前后端，截图确认地图顶部只有带标签的北极点标记、没有横条；点击标记打开“北极地区”抽屉；缩放/拖拽后标记与地图保持对齐