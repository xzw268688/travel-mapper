## 1. 极地底图

- [x] 1.1 将 `frontend/dist/antarctica.json` 的原文件保存为 `scripts/data/antarctica-azimuthal.json`，新增 `scripts/convert-antarctica.mjs` 按 `lon = atan2(y,x)+90°`、`lat = -90+hypot(x,y)` 逆投影，生成 `frontend/public/antarctica.json`；确认输出 bbox 纬度约 -90~-63、feature 名为 `Antarctica`
- [x] 1.2 新增 `frontend/public/arctic.json`：`[[-180,84],[180,84],[180,90],[-180,90],[-180,84]]` 的 FeatureCollection，feature 名为 `Arctic`；确认 84°N 以上不包含格陵兰/俄罗斯/加拿大陆地
- [x] 1.3 修改 `MapView.initMap()`：并行加载 `WORLD_GEO_URL`、`/antarctica.json`、`/arctic.json`，按 feature 名去重合并后 `registerMap`；极地文件失败时主图仍可用；确认地图渲染南极洲、北极地区与原有国家，`npm run build` 通过

## 2. 目录数据与迁移

- [x] 2.1 新增 `scripts/data/polar-regions.json`（`AQ` 南极洲 / `X3` 北极地区），并在 `world-names-zh.json` 增加 `Antarctica`、`Arctic` 中文名；确认校验数据可读
- [x] 2.2 新增 `scripts/generate-polar-regions-migration.mjs` 生成 `V11__polar_regions.sql`（`INSERT IGNORE`，只插两条）；在干净库执行 V1-V11 后确认国家总数为 217、AQ/X3 正确、现有 9 国不变
- [x] 2.3 扩展 `scripts/check-world-names.mjs`：校验极地 GeoJSON 覆盖、极地 code 唯一且与 V10 数据不冲突、V11 与数据源同步；运行后退出码为 0
- [x] 2.4 为本地库已存在的旧 V11 增加兼容迁移：V12 统一极地名称、V13 移除早期预置城市、V14 仅在存在历史引用时恢复城市；确认旧库与新库均可启动

## 3. 后端测试

- [x] 3.1 扩展 `CatalogIntegrationTest`：`GET /api/countries` 包含 `AQ`/`X3` 且名称为“南极洲”“北极地区”；`GET /api/map/country/AQ` 返回灰色空抽屉；`GET /api/cities?countryCode=AQ` 返回空数组；现有国家排序不变；确认用例通过
- [x] 3.2 运行 `CatalogIntegrationTest`、`MapIntegrationTest`、`TripIntegrationTest`（`-Dtest.containers.enabled=false`），确认全部通过

## 4. 文档与规格

- [x] 4.1 更新 README / `docs/ops/local-development.md`：说明底图现在包含南极洲与北极地区、主图会整体缩放、极地地区暂无城市；确认文档与实现一致
- [x] 4.2 运行 `openspec validate polar-regions --strict`，确认变更规格与任务格式通过校验

## 5. 实际运行验收

- [x] 5.1 重启前后端，截图确认地图底部出现南极洲、顶部出现北极地区，点击两者分别打开“南极洲”“北极地区”抽屉；确认规划页选择 AQ/X3 时城市下拉显示空提示