## 1. 数据库与实体

- [x] 1.1 新增 `V17__itinerary_spot_fields.sql`：为 `itinerary_spot` 增加 8 列（`category` 32、`ticket_price` 64、`highlights` 255、`best_time` 64、`nearest_station` 128、`reservation_required` TINYINT(1) NOT NULL DEFAULT 0、`area` 128、`warning_notes` 255），验证方式：启动后端 Flyway 迁移成功，`SHOW COLUMNS FROM itinerary_spot` 显示新列
- [x] 1.2 `ItinerarySpot` 实体补充 8 个字段（`reservationRequired` 用 `Boolean`），验证方式：`mvn -q compile` 通过
- [x] 1.3 检查 `ItinerarySpotMapper` 中所有显式列名的自定义 SQL 并补齐新列，验证方式：grep 确认无遗漏列清单，集成测试读回新字段

## 2. 后端 DTO 与保存/详情链路

- [x] 2.1 `ItineraryDtos.SpotRequest`、`SpotView`、`AddSpotRequest`、`UpdateSpotRequest` 增加 8 个字段，验证方式：`mvn -q compile` 通过
- [x] 2.2 在 `ItineraryService` 统一实现 `truncate` 映射并在保存、手动新增、手动更新三处应用（扩展字段缺失为空、超长截断、`reservationRequired` 缺省 `false`），验证方式：单测覆盖超长与缺失两种输入
- [x] 2.3 详情接口按天返回新字段且旧数据返回空值而非缺字段，验证方式：单测断言响应包含全部 8 个字段
- [x] 2.4 确认重新生成合并逻辑（`insertSpot`/手动景点合并）透传新字段，验证方式：既有 Regeneration 相关测试通过且新增字段在合并后保留

## 3. LLM 生成链路

- [x] 3.1 `GenerateItineraryResponse.SpotPlan` 增加 8 个字段，验证方式：`mvn -q compile` 通过
- [x] 3.2 `PromptBuilder.buildItineraryPrompt` 扩展 JSON Schema，要求每个景点输出 8 个扩展字段，并注明金额用原币文本、不确定时输出空字符串；验证方式：PromptBuilder 单测断言 Schema 包含全部字段名
- [x] 3.3 `ItineraryGenerationService` 按「可降级」规则解析扩展字段（`safeText` + `reservationRequired` 布尔归一），保留 name/天数/城市等强校验，验证方式：单测覆盖缺字段、类型错误、超长、布尔文本四种情况均不抛异常
- [x] 3.4 确认 `CachedGeneration` 复用 DayPlan 且 Redis 旧缓存反序列化兼容，验证方式：单测用旧格式 JSON 反序列化成功且新字段为空

## 4. 前端 API 与编辑界面

- [x] 4.1 `api/types.ts` 与 `api/itinerary.ts` 补充 8 个字段类型，验证方式：`npm run build`（vue-tsc）通过
- [x] 4.2 新建景点编辑抽屉组件（两列分组：基本信息/实用信息，移动端底部面板，扩展字段折叠），供新增与编辑复用，验证方式：组件在 `/plan` 与 `/itineraries/:id` 两处可打开并保存
- [x] 4.3 行程天卡片内的景点行展示主要字段与「需预约」徽标，点击打开抽屉；扩展字段为空的行不渲染占位，验证方式：页面截图人工检查 1440 与 390 宽度
- [x] 4.4 `PlanView.vue` 手动添加/编辑景点接入抽屉与新字段，验证方式：e2e 手动规划用例填写扩展字段后保存成功
- [x] 4.5 `ItineraryEditView.vue` 已保存行程的景点新增/编辑接入新字段与抽屉，验证方式：e2e 编辑用例修改门票后刷新仍保留

## 5. 契约文档与测试

- [x] 5.1 更新 `TravelMapper API  V1.0.md` 中行程保存/详情/景点 CRUD 的字段表，验证方式：文档字段与 DTO 一致
- [x] 5.2 更新 `frontend/e2e/app.spec.ts` 行程编辑用例，断言新字段可编辑并持久化，验证方式：`npx playwright test e2e/app.spec.ts` 通过
- [x] 5.3 全量回归：`mvn test` 单元测试与 `npx playwright test` 通过；启动应用后生成一条真实行程，人工确认 8 个字段展示、编辑、重进详情均正确