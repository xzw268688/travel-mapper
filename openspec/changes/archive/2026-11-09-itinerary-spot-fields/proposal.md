## Why

规划行程的景点目前只有「名称 / 描述 / 建议时长」三个业务字段，行程详情页无法承载真实出行所需的类别、门票、最佳时段、交通、预约等信息；用户拿着行程出门仍需另查资料。AI 一次性生成这些信息，并在编辑页完整可改，能把行程从「景点清单」升级为可直接使用的出行手册。

## What Changes

- `itinerary_spot` 新增 8 个字段：`category`（类别）、`ticket_price`（门票）、`highlights`（亮点）、`best_time`（最佳时段）、`nearest_station`（最近车站）、`reservation_required`（需预约，BOOLEAN）、`area`（区域）、`warning_notes`（避雷备注）；原有 `sort_order` 继续承担「排名」。
- 新增 Flyway 迁移（V17）扩展表结构；既有行程数据新字段为空，展示为占位符，不报错。
- LLM 行程生成提示词的 JSON Schema 扩展为一次输出全部新字段；`SpotPlan`、缓存结构、生成响应同步扩展。
- 生成结果的字段校验采用「缺失/超长即截断或留空、不因此判定整份行程失败」，只有 `name`、天数、`dayIndex`、`cityCode` 维持现有强校验。
- 保存、详情、新增/编辑/移动/删除景点接口的 DTO 全部承载新字段；编辑对话框/抽屉可修改全部 11 个字段，手动新增景点同样可填。
- 前端行程编辑页把景点呈现为「卡片 + 抽屉」：卡片展示主要字段与徽标（需预约等），抽屉内编辑全部字段；移动端抽屉改为底部面板。
- 生成结果页、旅行详情页、共享/海报等既有只读展示保持兼容，不因新字段缺失而失败。
- 非目标：本 change 不引入「餐饮」实体（由后续独立 change 交付）；本 change 不改变「已人工修改的 AI 景点在重新生成时保留」的机制，该机制随餐饮 change 一并引入，当前重新生成行为保持不变。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `itinerary/generation`: LLM 输出契约扩展为每个景点输出 8 个新字段，并定义缺失字段的降级规则与对重试/额度的影响。
- `itinerary/management`: 保存、详情、景点 CRUD 的字段集合扩展为 11 个字段；编辑新字段不改变 `source`、不消耗额度、不改变地图状态；手动景点上限规则不变。
- `itinerary/manual-planning`: 手动添加/编辑景点时允许填写全部 11 个字段，仍标记 `source=MANUAL`。

## Impact

- 数据库：`itinerary_spot` 表 ALTER（V17 迁移）。
- 后端：`ItinerarySpot` 实体、`ItinerarySpotMapper`（自定义列 SQL）、`ItineraryDtos`（SaveRequest/SpotRequest/SpotView/AddSpotRequest/UpdateSpotRequest 等）、`GenerateItineraryResponse.SpotPlan`、`CachedGeneration`、`ItineraryGenerationService`（LLM 解析）、`ItineraryService`（保存/重组/合并）、`PromptBuilder`。
- 前端：`ItineraryEditView.vue`（卡片与抽屉）、`PlanView.vue`（草稿编辑与保存）、`api/itinerary.ts`、`api/types.ts`；`TripDetailView.vue` 只读展示按需补充。
- 契约文档：`TravelMapper API  V1.0.md` 行程相关接口字段表更新。
- 测试：`ItineraryService`/生成解析相关单测、`frontend/e2e/app.spec.ts` 行程编辑用例；额度、去重、重生合并规则不变，现有用例应保持通过。