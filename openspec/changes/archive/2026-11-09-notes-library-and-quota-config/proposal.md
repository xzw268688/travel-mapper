## Why

游记页只有发布表单和一个「输入游记 ID 打开」的入口，用户无法浏览、搜索或管理已有游记；这个 ID 入口实际上没人会用。记账页的「仅未归组」筛选对日常使用没有价值却占据筛选区；每日免费额度写死在种子配置里，管理员无法调整；攻略搜索配置缺少说明，管理员不知道它影响什么。另外本地对象存储使用了无数据卷的 S3Mock，容器重启即丢媒体文件。

## What Changes

- **游记列表与搜索**：新增 `GET /api/notes`，支持关键字（标题 + 正文摘要）、创建日期区间、分页；游记页改为「最近游记 + 搜索 + 发布」结构，列表支持打开与删除；移除「打开已有游记 / 游记 ID」入口。
- **移除记账「仅未归组」**：前端不再展示该筛选，查询与重置逻辑同步移除；后端参数保留以兼容旧客户端。
- **修复记账筛选与编辑逻辑**：汇总与月度合计补齐城市、分类过滤，与列表保持一致；修复编辑已有消费时城市与所属归档被回填过程清空的问题。
- **可配置每日免费额度**：新增 `GET/PUT /api/admin/config/quota`，范围 0–100，生成与润色共用；后台「AI 模型」页提供输入与保存。
- **攻略搜索说明**：后台「攻略搜索」页补充结构化说明（作用、每日上限、超时、缓存、不配置的影响）。
- **本地媒体持久化（运维）**：开发环境对象存储由无卷的 S3Mock 切到带持久化卷的 SeaweedFS；已丢失的历史媒体不可恢复，需重新上传。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `notes/authoring`: 新增游记列表与搜索要求（分页、关键字、日期、打开/删除、不得要求输入 ID）。
- `expenses/accounting`: 新增记账页筛选区展示要求（不得展示「仅未归组」）。
- `platform/config`: 新增每日免费额度配置要求（读取、更新、范围与热加载）。

## Impact

- 后端：`NoteController` / `NoteService` / `NoteDtos`（列表 DTO）、`AdminConfigController` + `QuotaConfigDto`。
- 前端：`NotesView.vue`、`ExpensesView.vue`、`AdminSettingsView.vue`、`api/notes.ts`、`api/admin.ts`。
- 契约文档：`TravelMapper API  V1.0.md` 新增 9.1.1 游记列表与 12.4.1 免费额度配置。
- 测试：`frontend/e2e/app.spec.ts` 修正标题输入选择器歧义；Playwright 22/22 通过。
- 运维：开发环境对象存储端点改为 `http://127.0.0.1:19091`（SeaweedFS，持久化卷）。