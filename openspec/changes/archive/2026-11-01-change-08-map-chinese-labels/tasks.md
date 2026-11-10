## 1. 名称数据

- [x] 1.1 新增 `frontend/src/assets/world-names-zh.json`，覆盖底图全部要素；运行 `node scripts/check-world-names.mjs` 确认无遗漏
- [x] 1.2 新增 `scripts/check-world-names.mjs`：读取 `frontend/public/world.json` 与对照表，列出缺失名称并在缺失时以非 0 退出

## 2. 地图展示

- [x] 2.1 `MapView.vue` 增加名称解析：目录中文名 → 对照表 → 底图原名
- [x] 2.2 悬浮提示使用中文名，有数据保持行程/游记/总计口径，无数据显示“暂无记录”
- [x] 2.3 悬停高亮标签使用中文名，并加描边保证可读；构建通过

## 3. 验收

- [x] 3.1 更新 `frontend/README.md` 地图说明：中文名来源与回退规则
- [x] 3.2 运行名称校验脚本、`npm run build`、现有 E2E，并截图确认地图提示为中文