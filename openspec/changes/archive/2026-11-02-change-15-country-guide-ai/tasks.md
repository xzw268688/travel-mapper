## 1. 后端攻略能力

- [x] 1.1 在 `PromptBuilder` 增加国家攻略提示词（固定三章节与字段格式），并在 `PromptBuilderTest` 断言国家名、三个章节标题与输出约束存在
- [x] 1.2 在 `LlmProvider` 增加 `guideStream`，`HttpLlmProvider` 抽取共用流式方法并实现攻略流式调用；`FakeLlmProvider` 同步实现可配置的攻略输出与失败开关
- [x] 1.3 实现 `CountryGuideService`：国家校验、`GET` 缓存读取、`POST` 流式生成、24h 国家级缓存、同国并发锁、额度扣减与按 `delta` 的退款规则；用集成测试验证首次生成、第二用户缓存命中不扣费、并发返回 `3001`
- [x] 1.4 完成攻略文本解析（`intro/attractions/notice`，缺失字段降级，三块全空视为失败），并用单元测试覆盖正常、缺字段、空输出三种情况
- [x] 1.5 实现 `GET /api/country-guide/{countryCode}` 与 `POST /api/country-guide/stream`，补充参数校验测试（`2001`/`2002`/`401`/`2011`）与 SSE 事件顺序测试（`meta`/`delta`/`done`/`error`）

## 2. 行程生成移除行前准备

- [x] 2.1 从行程生成提示词与 `ItineraryGenerationService` 中删除 `preparations` 要求、解析与 `prep:*` 缓存，响应固定返回空值；更新 `ItineraryGenerationIntegrationTest` 与 `PromptBuilderTest` 断言不再产出与缓存
- [x] 2.2 确认保存与详情接口兼容历史 `preparations_json`，运行 `ItineraryManagementIntegrationTest` 验证旧数据仍可读

## 3. 前端国家攻略

- [x] 3.1 新增 `frontend/src/api/guide.ts`：缓存读取、SSE 流式生成与类型定义，复用现有 Bearer Token 与事件解析方式
- [x] 3.2 `MapView` 抽屉增加「AI 国家攻略」区域：打开时读取缓存；无缓存显示生成入口与额度提示；生成中流式展示并禁用重复提交；完成后按三块结构化渲染；失败可重试
- [x] 3.3 增加攻略样式（三块分区、流式文本、加载态、移动端），并在 `styles.css` 保持现有设计令牌
- [x] 3.4 `PlanView` 仅在 `preparations` 非空时展示行前准备卡片，避免新生成行程出现空卡片

## 4. 验收与文档

- [x] 4.1 后端全量测试通过，前端 `npm run build` 通过
- [x] 4.2 Playwright E2E 通过，并新增/调整抽屉攻略缓存展示与生成入口断言；截图确认桌面与移动端布局
- [x] 4.3 更新 `TravelMapper API V1.0.md`、`TravelMapper SRS V1.0.md`、`frontend/README.md` 中的国家攻略接口、缓存/额度规则与行前准备变更
- [x] 4.4 `openspec validate change-15-country-guide-ai --strict` 通过并归档 change-15