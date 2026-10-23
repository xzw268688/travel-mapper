## Why

AI 一键生成行程是 TravelMapper 的核心价值，也是地图点亮、行前准备和后续游记/记账的数据源头。额度、城市与旅行归组已经在 change-02/03 就绪，本 change 把“选择国家城市 → AI 生成 → 微调 → 保存 → 地图点亮”这条最小闭环真正跑通。

## What Changes

- 实现 AI 行程生成：请求校验、额度扣减、60 秒去重（结果缓存 + 在途锁）、LLM 30 秒超时与失败重试 1 次、JSON Schema 校验、多城市按天分配与单城市兜底、`warnings` 返回。
- 实现并行攻略搜索：独立 5 秒超时、24 小时 Redis 缓存、搜索日上限、URL 安全校验、失败时 `guides=[]` 且不阻断行程、不退回额度。
- 实现行前准备 `preparations`：与行程同一次 LLM 调用返回，按国家 + 城市集合缓存 24 小时，不改变额度语义。
- 实现行程保存/详情/编辑/删除/重新生成；首次保存自动创建或挂载 `trip`，保存即点亮地图为黄色。
- 实现手动景点：新增/编辑/删除/移动到某天，每条行程上限 30；重新生成时按 dayIndex/cityCode 合并并返回提示。
- 实现天管理：新增/修改城市/删除某天，1–15 天限制、日期连续性、`dayIndex` 平移、日期区间与旅行日期重算，全程不消耗 AI 额度。
- 实现固定 7 项行前清单的读取与 upsert；实现国家官方链接读取（未配置时通用搜索兜底）与后台维护（每国 ≤3 条）。
- 实现 AI 流式润色 SSE：`meta`/`delta`/`done`/`cancelled`/`error` 事件、`requestId` 取消、断网/部分输出/主动取消的额度退回边界。
- **不包含**游记正文、图片上传、分享与记账（change-05）；本 change 只消费 change-02 的额度契约和 change-03 的城市/旅行契约。

## Capabilities

### New Capabilities

- `itinerary/generation`: AI 生成请求校验、额度扣减、去重、LLM/搜索并行、JSON 校验、多城市分配与兜底、preparations/guides 组装、草稿不落库。
- `itinerary/management`: 行程保存/详情/编辑/删除/重新生成、景点与天管理、手动景点上限与合并、行程日期重算、地图状态回传。
- `prep/checklist`: 固定 7 项行前清单的读取、默认值、upsert 与行级隔离。
- `prep/official-links`: 官方链接读取、未配置兜底、URL 校验与后台维护。
- `ai/polish`: SSE 流式润色、事件协议、取消、额度扣减与退回边界。

### Modified Capabilities

无。全部为新增能力。

## Impact

- **新增接口**：`/api/itinerary/generate`、`/api/ai/cancel`、`/api/itinerary/save`、`/api/itinerary/{id}`、`/api/itinerary/{id}/regenerate`、景点与天的增删改移、`/api/prep/checklist`、`/api/prep/official-links`、`/api/ai/polish/stream`、`/api/admin/official-links/*`（API 6、7、12.13、13）。
- **数据表**：写入 `itinerary`、`itinerary_day`、`itinerary_spot`、`trip_checklist`、`country_official_link`；读取 `city`、`trip`；调用 change-02 的 `quota_pack`/`quota_ledger` 扣减服务。
- **Redis**：生成去重结果与在途锁、`guides` 缓存、`preparations` 缓存、搜索日计数、取消标志、`requestId` 归属、额度扣减记录。
- **外部依赖**：LLM（LangChain4j，供应商由配置中心切换）、SearchProvider（change-01 契约）、Nginx SSE 免缓冲配置。
- **前端**：行程生成表单、草稿微调页、已保存行程编辑页、行前准备卡片、流式润色编辑器、官方链接兜底入口。
- **后续依赖**：change-05 的游记/消费关联行程时使用本 change 的 `itineraryId` 与 `tripId`；地图完整 E2E 在本 change 后可以验证黄色点亮。
- **文档依据**：SRS REQ-AI-001/002/003、REQ-PREP-001/002/003、REQ-REC-002、BR-002/006/007/009；Design 7.0/7.1/7.4/7.7/7.10–7.13/7.17/7.21/7.22；API 6、7、12.13、13。
- **无破坏性变更**。
