## Context

见 proposal.md - Why。与本设计直接相关的现状：

- `world.json` 纬度范围 -55.78°~83.56°，没有南极洲大陆；ECharts `map` series 按 feature `name` 匹配国家目录并打开抽屉。
- `frontend/dist/antarctica.json` 只有一个 `Antarctica` 多边形，但坐标是 **南极方位投影**：`properties.projection = "south-polar-azimuthal"`，坐标范围约 -23~24，半径约 12~27，不是经纬度。
- 北极在底图中是海洋，没有陆地边界；84°N 以上没有陆地，格陵兰最北约 83.6°N。
- V10 国家迁移已应用，不能修改；极地条目需要新的 V11。
- 现有校验脚本 `check-world-names.mjs` 只检查 `world.json` 的名称覆盖与 V10 迁移同步。

## Goals / Non-Goals

**Goals:**

- 在同一张 ECharts 世界地图上渲染南极洲与北极地区，交互与普通国家一致。
- 复用现有国家目录、抽屉、统计与 AI 攻略，不修改后端业务逻辑。
- 保持 `world.json` 原样，极地底图独立维护，缺失时主地图仍可降级使用。
- 通过脚本与集成测试防止极地数据遗漏或迁移漂移。

**Non-Goals:**

- 不新增城市，不改变“行程/游记必须选择城市”的规则。
- 不修改 V10 迁移或现有国家/城市/业务数据。
- 不改变地图三态统计口径与抽屉结构。
- 不为北极引入独立的极地投影或小图视图。

## Decisions

### 1. 运行时合并三份 GeoJSON，按名称去重

前端 `initMap()` 并行请求：

- `WORLD_GEO_URL`（默认 `/world.json`，保持可覆盖）
- `/antarctica.json`
- `/arctic.json`

合并 `features` 后按 `properties.name` 去重，再 `echarts.registerMap('world', merged)`。极地文件加载失败时只记录警告，主地图仍然渲染；极地文件成功时覆盖同名 feature。

理由：`world.json` 不动，极地底图可独立替换；去重避免外部 `VITE_WORLD_GEO_URL` 已包含极地要素时出现重复数据。备选是直接修改 `world.json`，会让底图与业务数据耦合，且未来替换底图容易丢失极地要素。

### 2. 南极洲方位投影逆转换为经纬度

一次性脚本 `scripts/convert-antarctica.mjs` 读取 `frontend/dist/antarctica.json`，按以下公式转换并输出 `frontend/public/antarctica.json`：

```
r = hypot(x, y)                    // 距南极点的角距离（度）
lon = normalize(atan2(y, x) * 180 / PI + 90)
lat = -90 + r
```

校准依据：半径最大点（r≈27）落在 -60° 经线附近，对应南极半岛；r=12 的极小值落在 180°（罗斯冰架）与 -50°（菲尔希纳-龙尼冰架）附近。转换后属性改为 `name: "Antarctica"`、`projection: "equirectangular"`，并保留来源说明。

原始方位投影文件保留在 `scripts/data/antarctica-azimuthal.json`，转换脚本可重复执行；`frontend/public/antarctica.json` 是生成的经纬度底图。

备选：直接使用方位投影坐标合并。会落到错误位置，不可行。备选：手写经纬度轮廓。精度更低，且已有可用数据。

### 3. 北极地区使用 84°N–90°N 全宽极冠多边形

新增静态文件 `frontend/public/arctic.json`，单个 `Polygon`：

```
[[-180, 84], [180, 84], [180, 90], [-180, 90], [-180, 84]]
```

选择 84°N 是为了不覆盖格陵兰最北端（约 83.6°N）与加拿大北极群岛；该区域在底图上是一条横跨顶部的条带。属性为 `name: "Arctic"`。

备选：北极圈（66.5°N）环形区域，会与多国陆地重叠，点击时挡国家。备选：独立标记点，与用户“做成区域”的要求不符。

### 4. 极地目录数据独立生成 V11

新增 `scripts/data/polar-regions.json`：

```json
[
  { "code": "AQ", "nameEn": "Antarctica", "nameZh": "南极洲", "continent": "ANTARCTICA", "sortOrder": 1300 },
  { "code": "X3", "nameEn": "Arctic", "nameZh": "北极地区", "continent": null, "sortOrder": 1301 }
]
```

新增 `scripts/generate-polar-regions-migration.mjs` 生成 `V11__polar_regions.sql`，使用 `INSERT IGNORE`，只插入两条记录。V10 与 `scripts/data/world-countries.json` 完全不动。

理由：V10 已应用，修改会触发 Flyway checksum 校验失败；独立数据文件也让“世界国家”和“极地地区”两类数据职责清晰。

### 5. 校验脚本扩展

扩展 `scripts/check-world-names.mjs`：

1. 读取 `antarctica.json`、`arctic.json` 的 feature 名称，确认都能在 `polar-regions.json` 中找到 `nameEn`。
2. 极地 code 唯一、长度 2，且不与 `world-countries.json` 冲突。
3. V11 与 `polar-regions.json` 同步（重新生成内容一致）。
4. 在 `world-names-zh.json` 增加 `Antarctica`、`Arctic` 中文名，供悬停兜底。

### 6. 地图布局与交互

- ECharts 根据合并后的 GeoJSON bbox（-90°~90°）自动缩放，主图整体缩小；不做自定义投影或手动 zoom 补偿。
- `buildMapData()` 无需特殊分支：新 feature 的 `name` 与目录 `nameEn` 一致即可解析出 `AQ`/`X3`，获得三态颜色与抽屉点击。
- 悬停标签、图例与统计逻辑沿用现有实现。
- 规划/游记选择 AQ/X3 时城市下拉为空并显示既有“该国家暂未配置城市”提示；记账不强制城市，可直接使用。

### 7. 测试策略

- `CatalogIntegrationTest`：`GET /api/countries` 包含 `AQ`/`X3` 且名称为“南极洲”“北极地区”；`GET /api/map/country/AQ` 返回灰色空抽屉；`GET /api/cities?countryCode=AQ` 返回空数组；现有国家排序不变。
- 脚本校验：极地 GeoJSON 覆盖、代码唯一、V11 同步。
- 手工/Playwright：地图底部出现南极洲、顶部出现北极地区；点击分别打开抽屉；截图确认主图缩放可接受。

## Risks / Trade-offs

- **南极洲来自 77 点简化轮廓** → 海岸线较粗；可接受，后续可替换为更高精度经纬度数据。
- **逆投影旋转角度需要视觉校准** → 已用半岛、罗斯冰架、菲尔希纳冰架三个地标确定 +90° 偏移；验收时截图复核，若明显偏移再调整一次。
- **主图整体缩小** → 用户已确认接受；地图仍可 roam 缩放。
- **北极极冠条带较窄** → 默认缩放下点击区域约几十像素；保留 roam，必要时可放大后点击。
- **外部 `VITE_WORLD_GEO_URL` 已含极地** → 按名称去重，极地本地文件优先。
- **AQ/X3 出现在所有国家下拉** → 与上一批新增国家一致；无城市时已有明确空提示。

## Migration Plan

1. 提交 `frontend/public/antarctica.json`、`frontend/public/arctic.json` 与 `V11__polar_regions.sql`。
2. V11 为纯新增：只插入 `AQ`、`X3`；回滚时删除这两行即可，不影响现有数据。
3. 兼容早期未发布的 V11（本地库曾写入“北极”名称与两个预置城市）：
   - `V12__polar_region_names.sql` 统一 `AQ`/`X3` 的名称与属性；
   - `V13__remove_polar_cities.sql` 移除早期预置的 `ANT`/`NPO` 城市；
   - `V14__restore_referenced_polar_cities.sql` 仅在旧库确有历史行程/游记/消费引用时恢复这些城市，保证旧数据可展示；全新库不会插入任何城市。
4. 前端与后端同版本发布；无需修改接口或表结构。
5. 更新 README、本地开发说明与 OpenSpec 规格。

## Open Questions

（无）