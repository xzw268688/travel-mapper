Triage: ready-for-agent

## Why

游记润色目前要跳到独立 `/polish` 页面复制粘贴文本，与写作流程割裂；游记也只支持图片，无法记录短视频。此外，上传成功后直接保存 objectKey 的模式缺少服务端校验，上传中断或对象被清理后会产生打不开的媒体，孤儿清理也只识别 `images_json`，存在误删正文引用对象的可能。

## What Changes

- 游记编辑器工具栏新增「AI 润色」：选中文本后直接流式润色，完成后原位替换；支持取消与重试；生成期间编辑器只读以保持选区稳定。
- 删除独立 `/polish` 页面、路由与导航入口；润色 API 保持不变。
- 支持游记视频：`video/mp4`、`video/webm`，单个 ≤100MB，每篇 ≤3 个；视频上传签名有效期 30 分钟（图片仍为 5 分钟）。
- 新增游记视频字段与接口：`videos`（objectKey 数组）在创建/编辑/详情中读写，详情返回 600 秒签名播放地址；视频以自定义节点嵌入正文。
- **BREAKING**：新增 Flyway 迁移 `travel_note.videos_json`；保存/编辑游记时对图片与视频 objectKey 做归属前缀校验与存储 HEAD 校验，不存在或不属于当前用户的对象返回新错误码。
- 孤儿清理扩展为同时扫描 `images_json`、`videos_json` 与正文 `/objects/` 引用，避免删除仍在正文中使用的媒体。
- 新增存储介质与版本化文档，说明对象 Key 布局、媒体类型、TTL 与后续升级规则。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `ai/polish`: 新增“编辑器内选中文本润色”的交互要求；删除独立页面不改变流式协议。
- `notes/authoring`: 新增视频上传/数量与大小限制、`videos` 字段、媒体 HEAD 校验与详情返回规则。
- `platform/storage`: 扩展预签名上传（视频类型与 30 分钟 TTL）、对象归属校验、孤儿清理覆盖视频与正文引用。

## Impact

- 数据库：新增 `V8__note_videos.sql`。
- 后端：`UploadController`、`ObjectKeyGenerator`、`StorageUrlService`、`StorageProvider`/清理服务、`TravelNote` 实体、`NoteService`、`NoteDtos`、`ErrorCode`。
- 前端：`RichTextEditor`（润色面板 + 视频节点）、`NotesView`、`NoteDetailView`、`api/notes.ts`、`api/ai.ts`、`utils/noteContent.ts`、`router`、`App.vue`；删除 `PolishView.vue`。
- 测试：后端游记/上传/存储测试，Playwright 编辑器润色与视频上传用例，截图。
- 文档：API、SRS、README、存储运维文档。