Triage: ready-for-agent

## Why

用户打开国家抽屉时只能看到自己的行程与游记，缺少“这个地方值不值得去、怎么玩、要注意什么”的通用信息；而这些内容目前混在行程生成的“行前准备”里，既重复又只在生成行程时出现。把国家攻略独立出来，按需生成并缓存，能让未去过或准备去的国家也有内容可看，同时让行程生成更专注。

## What Changes

- 国家抽屉新增「AI 国家攻略」区域，包含三块：国家介绍、推荐景点、旅游须知。
- 攻略由用户点击「生成攻略」后按需生成，通过 SSE 流式返回；一次生成覆盖三块内容，只扣 1 次 AI 额度。
- 攻略按国家全局缓存 24 小时，缓存命中不扣额度、不调用 LLM，所有用户共享同一份国家攻略。
- 未登录用户不可生成；缓存读取与生成均校验国家存在且启用。
- 生成失败且未输出任何内容时退回额度；已输出部分内容后失败不退回。
- **BREAKING**：AI 行程生成不再产出 `preparations`（行前准备）；生成响应中的该字段保留但始终为空，保存行程时不再写入新的行前准备文本。国家相关行前信息统一由国家攻略提供。

## Capabilities

### New Capabilities

- `ai/country-guide`: 国家攻略的按需生成、SSE 协议、额度规则、国家级 24 小时缓存、结构化解析与抽屉展示。

### Modified Capabilities

- `itinerary/generation`: 移除“Preparations generation and cache”要求，改为“行前准备不再由行程生成产出”；响应中的 `preparations` 固定为空。

## Impact

- 后端：新增 `service/guide/CountryGuideService`、`controller/guide` DTO 与接口；`LlmProvider`/`HttpLlmProvider`/`PromptBuilder` 增加国家攻略流式方法与提示词；`ItineraryGenerationService` 移除行前准备生成与缓存；测试桩同步。
- 前端：新增 `api/guide.ts`；`MapView` 抽屉增加攻略区域与流式渲染；`PlanView` 仅在 `preparations` 非空时展示行前准备；`styles.css` 增加攻略样式。
- 缓存/额度：Redis `country-guide:v1:{countryCode}` TTL 24h；生成锁防止同国并发重复调用。
- 文档：API、SRS、README 同步国家攻略接口与行前准备变更。