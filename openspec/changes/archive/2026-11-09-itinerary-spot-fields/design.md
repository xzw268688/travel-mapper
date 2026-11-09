## Context

见 `proposal.md` 的 Why。当前实现约束（已核实）：

- `itinerary_spot` 现有列为 `day_id / sort_order / source / name / description / suggested_duration`（`V1__schema.sql`），自定义 SQL 集中在 `ItinerarySpotMapper`，实体为 MyBatis-Plus `@Data` 映射。
- 生成链路：`PromptBuilder.buildItineraryPrompt` 输出固定 JSON Schema → `ItineraryGenerationService` 手动解析 `JsonNode` → `GenerateItineraryResponse.SpotPlan` → `CachedGeneration` 缓存 60 秒 → `ItineraryService` 保存。
- 保存链路同时服务 AI 草稿与手动草稿（`ItineraryDtos.SpotRequest`），保存后走 `insertSpot`。
- 手动景点的实时增改走 `AddSpotRequest` / `UpdateSpotRequest`，上限 30。
- 前端 `PlanView.vue` 与 `ItineraryEditView.vue` 目前用 3 字段对话框和事件栈管理草稿。

## Goals / Non-Goals

**Goals**
- 新字段在「生成 → 缓存 → 保存 → 详情 → 编辑器」全链路可往返，且旧数据不报错。
- 字段上限在 API 层统一截断，避免 LLM 幻觉长文本引发 500 或超长入库。
- 保持额度、去重、重生合并、地图状态规则完全不变。

**Non-Goals**
- 不做餐饮实体、不做「已人工修改保留」的 dirty 标记（后续 change）。
- 不为新字段做搜索、筛选或统计；不引入枚举校验（类别、区域保存为自由文本）。
- 不改动共享页/海报的数据契约。

## Decisions

### D1：8 个自由文本字段用独立列，`reservation_required` 用 BOOLEAN
- 选独立列而非 JSON：需要逐字段截断与展示，且后续可能按需增加筛选；JSON 列无法在 DTO 层直接约束，会静默丢字段。
- 类别、区域保持 VARCHAR 而非枚举：AI 覆盖全球目的地，枚举需要持续维护，且用户可自由填写。
- 列定义：`category VARCHAR(32)`、`ticket_price VARCHAR(64)`、`highlights VARCHAR(255)`、`best_time VARCHAR(64)`、`nearest_station VARCHAR(128)`、`reservation_required TINYINT(1) NOT NULL DEFAULT 0`、`area VARCHAR(128)`、`warning_notes VARCHAR(255)`。全列可空（`reservation_required` 除外）。
- 金额存文本原币：与 `exchange_rate` 解耦，AI 按目的地货币输出。

### D2：除 `name` 外所有景点字段改为「可降级」解析
- `ItineraryGenerationService` 现有逻辑是缺 `name` 即抛 `InvalidPlanException` 并触发重试。新字段若同样严格，会让带小瑕疵的输出整份重试并可能浪费额度。
- 采用逐字段 `safeText(node, path, maxLen)`：类型不是文本则取空、超长截断；`reservationRequired` 接受布尔或 `"是"/"true"/"需要"` 等常见文本，无法识别时 `false`。
- 仅保留强校验：天数、`dayIndex` 连续、`cityCode` 在集合内、每天 1–6 个景点、`name` 非空。

### D3：DTO 字段命名与视图对象
- `SpotPlan` / `SpotView` / `SpotRequest` / `AddSpotRequest` / `UpdateSpotRequest` 增加同名字段：`category`、`ticketPrice`、`highlights`、`bestTime`、`nearestStation`、`reservationRequired`、`area`、`warningNotes`。
- `reservationRequired` 使用 `boolean`，响应中始终存在；其余使用 `String`，空值为 `""` 而非 `null`（保持既有 `preparations` 的空字符串风格，前端无需判空）。
- `CachedGeneration` 复用 `GenerateItineraryResponse.DayPlan`，无需单独扩展；Redis 中的旧缓存反序列化时新字段缺失，Jackson 默认置空/`false`，天然兼容。

### D4：截断在服务端统一执行，不依赖 Bean Validation
- `@Size` 校验失败会整单 400，与「可降级」冲突；因此在 `ItineraryService` 的映射入口做 `truncate(value, maxLen)`，并让 DTO 只保留 `name` 的 `@NotBlank @Size(50)`。
- 前端输入框同步 `maxlength`，减少无谓往返。

### D5：前端卡片 + 抽屉
- 天卡片内每个景点显示为紧凑行：名称、类别/区域标签、`需预约` 徽标、建议时长、门票；点击打开 `el-drawer`（移动端 `direction: btt`、高度 90%）编辑全部 11 个字段。
- 复用同一个抽屉组件承载「手动新增」与「编辑」，减少两套表单。
- 生成草稿（`PlanView`）与已保存行程（`ItineraryEditView`）共用该组件，避免逻辑分叉。
- 新增字段不参与原有排序交互（拖动仍只影响 `sort_order`）。

### D6：迁移与回滚
- 新增 `V17__itinerary_spot_fields.sql`，只做 `ALTER TABLE ... ADD COLUMN`，全部带默认值/可空，向后兼容。
- 回滚策略：应用回退后旧代码忽略新列；如需彻底回填，提供独立维护 SQL（不在本 change 实现）。

## Risks / Trade-offs

- [LLM 票价/车站幻觉] → 提示词要求不确定时输出空字符串；前端在扩展字段区域标注「AI 生成，出行前请核实」。
- [单次生成 token 增加导致超时/解析失败率上升] → 扩展字段使用短文本上限（≤255），保留 30 秒超时与一次重试；字段不可解析时降级而非整份失败。
- [前端表单变长，移动端操作负担] → 抽屉内以两列网格分组（基本信息 / 实用信息），次要字段折叠；移动端全屏面板。
- [旧行程与新行程展示不一致] → 扩展字段为空时前端不渲染对应行，视觉上自然降级。
- [手动景点表单过度复杂] → 扩展字段默认折叠在「更多信息」区，只展开名称/描述/时长。

## Migration Plan

1. 发布前执行 `V17` 迁移（Flyway 自动）。
2. 部署后端：新 DTO 对旧客户端请求向后兼容（可选字段）。
3. 部署前端。
4. 验证：旧行程详情正常、生成新行程包含扩展字段、手动编辑往返。
5. 回滚：回退应用版本即可；新列保留不影响旧代码。

## Open Questions

- 无。类别/区域是否改为枚举、是否需要按区域筛选，留待后续 change，不影响本次规格与任务拆分。