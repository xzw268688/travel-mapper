## Context

现状（见 proposal.md - Why）：

- `frontend/public/world.json` 有 217 个要素、215 个唯一英文名，属性只有 `name` / `childNum` / `cp`，没有国家代码。
- `frontend/src/assets/world-names-zh.json` 已覆盖全部 215 个名称。
- 后端 `country` 表只 seed 了 9 个启用国家；`MapService.drawer()` 与 `CountryGuideService` 都依赖 `catalogService.requireEnabledCountry()`。
- 地图前端用 `nameZh` / `nameEn` 建索引，点击时通过 `params.data.code` 判断能否打开抽屉（`MapView.vue:248`）。
- `city` 表只覆盖现有 9 国；`CountryCitySelector` 已有“暂无可选城市”空提示，`PlanView` 的城市下拉尚未设置空提示。
- 核心迁移已用到 `V8`，`V9` 被 `db/optional/V9__exchange_rate_history.sql` 占用，新核心迁移应使用 `V10`。

## Goals / Non-Goals

**Goals:**

- 以国家目录为唯一事实来源，覆盖底图全部有名称区域。
- 地图按 `name_en` 直接解析国家代码，不引入第二份运行时映射。
- 保持现有 9 国数据不变，迁移只补充缺失项。
- 通过脚本与集成测试防止国家数据遗漏或代码重复。

**Non-Goals:**

- 不新增城市，不改变“行程/游记必须选择城市”的规则。
- 不新增国家后台管理页。
- 不修改底图边界、归属或地区称谓。
- 不改动地图统计口径与抽屉结构。

## Decisions

### 1. 直接把国家数据写入 `country` 表，`name_en` 与底图对齐

新增国家以 `status=ENABLED` 写入目录，`name_en` 使用底图 feature 的英文名（如 `Germany`、`W. Sahara`、`Korea`）。前端现有的 `countryByName` 索引会自动覆盖全部要素，无需新增映射文件。

- 备选：单独维护英文名 → 代码 JSON。多一份运行时数据，且容易与目录漂移。
- 备选：改写 `world.json` 注入代码。底图资源会被业务数据污染，且未来替换底图时容易丢失。

现有别名（`Korea → KR`、`United States of America → US` 等）保留，作为名称不完全一致时的兜底。

### 2. 使用仓库内规范数据文件生成迁移

新增 `scripts/data/world-countries.json` 作为唯一数据源，每条包含 `code`、`nameEn`、`nameZh`、`continent`（可为空）。数据由底图名称、现有中文映射与一份公开 ISO 3166-1 数据合并生成，并人工抽查。

新增 `scripts/generate-world-countries-migration.mjs` 从 JSON 生成 `backend/src/main/resources/db/migration/V10__all_world_countries.sql`。SQL 使用 `INSERT IGNORE`（`code` 有唯一键 `uk_code`），只插入缺失项，不覆盖现有 9 国。

- 备选：手写 200+ 条 SQL。易出错且难以校验。
- 备选：启动时从 JSON 动态同步国家。会让 Flyway 与运行时数据职责混乱，且并发启动有风险。

### 3. 特殊区域使用保留码

`Siachen Glacier`、`N. Cyprus` 没有标准 ISO 3166-1 alpha-2 代码，分别使用 `X1`、`X2`。这两个代码满足 `CHAR(2)` 与唯一键约束，不与 ISO 现有代码冲突。

- 备选：跳过这两个区域。与“全部 215 个可点击”的目标冲突。
- 备选：合并到印度/巴基斯坦/塞浦路斯。会让用户点击后看到完全无关的国家数据，语义错误。

### 4. 排序与大洲

- 现有 9 国保持 `sort_order` 1–30 不变。
- 新增国家 `sort_order` 从 1000 开始，按“大洲 → 中文名”的稳定顺序编号，使下拉列表分组感一致。
- `continent` 从规范数据读取；无法可靠判断时留 `NULL`，不阻塞任何功能。

### 5. 前端点击兜底

`MapView` 的点击处理改为：

- 能解析 `params.data.code` → 调 `openCountry(code)`。
- 无法解析 → `ElMessage.info('该区域暂未收录')`。

两个无名称的底图要素仍无法解析，点击时会命中兜底提示；不为它们臆造名称或代码。

### 6. 下拉与空城市提示

国家下拉会自动出现全部 215 国，且现有 `el-select` 均已开启 `filterable`。仅为 `PlanView` 的城市下拉补充 `no-data-text="该国家暂未配置城市，请联系管理员补充"`，与 `CountryCitySelector` 保持一致。

### 7. 校验脚本

扩展 `scripts/check-world-names.mjs`（或新增 `check-world-countries.mjs`）校验：

1. 底图每个唯一名称在 `world-countries.json` 中都有 `nameEn` 对应项。
2. 每个 `code` 唯一且长度为 2。
3. `V10__all_world_countries.sql` 与规范数据文件同步（重新生成后内容一致）。
4. 中文名覆盖全部条目。

校验失败以非零退出码结束，可接入 CI。

### 8. 测试策略

- 后端集成测试：断言 `GET /api/countries` 包含新增国家（如 `DE`）；`GET /api/map/country/DE` 返回灰色状态与空汇总；现有 9 国的名称/排序不变。
- 手工/Playwright：点击德国、格陵兰、西撒哈拉，确认抽屉打开；点击无业务数据国家不报错；无城市国家在规划页城市下拉显示空提示。

## Risks / Trade-offs

- **215 条国家数据可能有代码或大洲错误** → 使用公开 ISO 数据生成并脚本校验；人工抽查高风险区域（`X1`、`X2`、`W. Sahara`、属地）。
- **国家下拉明显变长** → 所有下拉已支持搜索；新增国家排在现有 9 国之后，常用国家仍靠前。
- **无城市国家会让规划/游记停在城市步骤** → 明确的空提示；管理员可在后台补城市。记账不受影响，因为城市可选。
- **`X1`/`X2` 会进入业务数据** → 这是唯一能让这两个区域拥有独立记录的方式；文档中说明其非 ISO 语义。
- **`name_en` 与底图强耦合** → 若未来替换底图名称，需同步更新国家目录；校验脚本会在名称缺失时失败。
- **现有别名与新数据重叠** → 别名优先于目录名称匹配，不改变现有国家行为。

## Migration Plan

1. 生成并提交 `V10__all_world_countries.sql`，随应用启动由 Flyway 执行。
2. 迁移是纯新增：`INSERT IGNORE` 只补充缺失 code，不修改既有行。
3. 回滚：如需撤销，删除本次新增的 code 对应行即可；现有 9 国与业务数据不受影响。
4. 前端改动（点击兜底提示、规划页空城市提示）随同一版本发布；无需后端接口变更。

## Open Questions

（无）