## 1. 国家数据与迁移

- [x] 1.1 新增 `scripts/data/world-countries.json`：覆盖底图 215 个有名称区域，包含 `code`、`nameEn`、`nameZh`、`continent`；`nameEn` 与底图英文名对齐，`nameZh` 沿用 `world-names-zh.json`，两个无 ISO 区域使用 `X1`/`X2`；运行校验脚本确认 215/215 覆盖
- [x] 1.2 新增 `scripts/generate-world-countries-migration.mjs`，从规范数据生成 `V10__all_world_countries.sql`（`INSERT IGNORE`，只补缺失 code）；在干净库执行 Flyway 后确认新增国家存在、现有 9 国名称/状态/排序未变
- [x] 1.3 扩展校验脚本：检查底图名称到规范数据的覆盖（允许 `Korea → South Korea` 等既有别名）、code 唯一且长度为 2、生成的 SQL 与规范数据同步；运行后以退出码 0 结束

## 2. 前端交互

- [x] 2.1 `MapView.vue` 点击处理增加无法解析代码时的 `ElMessage.info('该区域暂未收录')`；确认点击德国打开抽屉、点击无名要素出现提示而不是静默
- [x] 2.2 `PlanView` 的城市下拉补充 `no-data-text="该国家暂未配置城市，请联系管理员补充"`（`CountryCitySelector` 已有同类提示）；确认无城市国家的规划页显示该提示
- [x] 2.3 确认规划/游记/记账的国家下拉自动包含新增国家且均可搜索；运行 `npm run build` 通过

## 3. 后端测试

- [x] 3.1 新增或扩展集成测试：`GET /api/countries` 包含 `DE` 等新增国家；`GET /api/map/country/DE` 返回灰色状态与空汇总；现有 9 国名称与排序不变；确认测试通过
- [x] 3.2 运行相关后端测试（`mvn -q -Dtest=TripIntegrationTest,MapIntegrationTest -Dtest.containers.enabled=false test` 或新增测试类）并确认全部通过

## 4. 文档与规格

- [x] 4.1 更新 README / `docs/ops/local-development.md`：说明国家目录覆盖全部底图区域、新增国家暂无城市时的行为；确认文档不再暗示只有 9 国可用
- [x] 4.2 运行 `openspec validate all-map-countries --strict`，确认变更规格与任务格式通过校验

## 5. 实际运行验收

- [x] 5.1 启动前后端，分别点击德国、格陵兰、西撒哈拉等新增区域，截图确认抽屉打开、灰色空状态、AI 攻略入口可见；确认规划页选择无城市国家时出现空提示