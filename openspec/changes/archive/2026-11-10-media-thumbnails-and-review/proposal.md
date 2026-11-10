Triage: ready-for-agent

## Why

游记图片在列表、详情和分享页都直接加载原图：客户端虽已压到 1600px，但手机端仍要为每张封面和缩略网格下载数百 KB。后台已有「可选 CDN 域名」配置，但代码从未使用，实际没有 CDN。与此同时，图片与视频没有任何内容审核，只有事后举报与下架；MOV 视频虽允许上传，却可能无法在 Android 播放。本 change 只做**不增加云服务费用、不显著增加服务器负载**的部分：缩略图、人工审核队列与 MOV 明确提示；服务端转码、自动内容审核与 CDN 接入不在本期。

## What Changes

- **客户端生成缩略图**：图片压缩后额外生成 480px JPEG 小图（≤512KB），通过新增的 `thumbOf` 参数请求上传签名；服务端只允许为当前用户前缀内的图片生成派生 Key，固定为 `{原图 objectKey}.thumb.jpg`，不新增映射表。
- **缩略图读取**：游记列表、详情与分享页返回缩略图签名 URL（`coverThumbUrl` / `thumbUrl` / `imageThumbs`）；前端优先用小图，加载失败或历史游记无缩略图时自动回退原图。详情大图与编辑仍使用原图。
- **孤儿清理兼容缩略图**：清理任务将 `{base}.thumb.jpg` 视为被 `{base}` 的引用保护，避免 24 小时后误删缩略图；被删除游记的缩略图随原图一起清理。
- **人工媒体审核队列（先发后审）**：`travel_note` 增加 `media_reviewed_at`、`media_reviewed_by`；带图片或视频的新游记进入「待审核」，管理员在后台筛选并标记「已审核」，操作写审计日志。游记仍按原规则立即发布，不增加发帖前阻塞；编辑后媒体发生变化时重置审核状态。
- **MOV 明确提示**：保留 `video/quicktime` 上传，前端在选择 MOV 时弹出确认框，提示 Android/Chrome 可能无法播放并建议导出 MP4；不实现服务端转码。
- **规格修正**：统一 `platform/storage` 与 `notes/authoring` 的视频白名单（mp4 / webm / mov），并更新媒体运维文档、API 文档与 DB 文档。

不做：服务端 ffmpeg 转码、自动图像/视频审核、CDN 接入、视频缩略图/封面截帧、分享页视频展示。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `notes/authoring`: 图片上传签名支持派生缩略图；列表/详情/分享返回缩略图 URL；MOV 上传前确认提示。
- `platform/storage`: 上传签名约束增加缩略图派生 Key；统一视频白名单为 mp4/webm/mov；孤儿清理保护缩略图。
- `admin/moderation`: 内容列表增加媒体审核状态筛选；新增标记已审核的操作与审计。
- `platform/persistence`: `travel_note` 增加媒体审核字段。

## Impact

- 后端：`UploadController`/`UploadDtos`（`thumbOf`）、`ObjectKeyGenerator`（缩略图 Key）、`NoteDtos`（`thumbUrl`/`coverThumbUrl`）、`NoteService`（列表/详情/分享/编辑重置审核）、`ShareDtos`、`AdminNoteView`/`AdminNoteController`/`NoteService.adminList`（审核筛选与标记）、`StorageCleanupService`、`TravelNote` 实体、Flyway `V25__media_review.sql`。
- 前端：`utils/image.ts`（生成缩略图）、`api/notes.ts`（`thumbOf` 与类型）、`components/RichTextEditor.vue`（上传缩略图 + MOV 确认）、`views/NotesView.vue`、`views/NoteDetailView.vue`、`views/ShareView.vue`、`views/AdminNotesView.vue`。
- API：`POST /api/upload/presign` 增加可选 `thumbOf`；游记列表/详情与分享页响应增加缩略图字段；`GET /api/admin/notes` 增加 `mediaReview` 筛选；新增 `PUT /api/admin/notes/{id}/media-review`。
- 文档：`docs/ops/storage-media.md`、`TravelMapper API  V1.0.md`、`TravelMapper DB  V1.0.md`。
- 测试：新增缩略图签名/读取回退、清理保护、审核队列集成测试；扩展 `StorageIntegrationTest`、`AdminNotes` 相关测试与 `StorageCleanupServiceTest`。