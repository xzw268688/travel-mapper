## 1. 数据库、上传签名与清理

- [x] 1.1 新增 Flyway `V25__media_review.sql`：`travel_note` 增加 `media_reviewed_at`、`media_reviewed_by`；`TravelNote` 实体同步字段。运行集成测试启动 Flyway，确认迁移成功。
- [x] 1.2 `UploadDtos.PresignRequest` 增加可选 `thumbOf`；`UploadController` 校验 `thumbOf` 属于当前用户、仅用于图片、内容类型为 `image/jpeg`、大小 ≤512KB，生成 `{thumbOf}.thumb.jpg`；补充集成测试覆盖合法/越权/超大缩略图。
- [x] 1.3 `StorageCleanupService` 将 `{base}.thumb.jpg` 视为被 `{base}` 的引用保护，原图孤儿时一并清理；扩展 `StorageCleanupServiceTest` 覆盖缩略图保留与删除。

## 2. 后端读取缩略图

- [x] 2.1 `NoteDtos.ImageView` 增加 `thumbUrl`，`NoteDtos.ListItem` 增加 `coverThumbUrl`；`NoteService` 列表与详情为 `{key}.thumb.jpg` 生成 600 秒签名 URL。集成测试断言字段存在且 URL 指向 `.thumb.jpg`。
- [x] 2.2 `ShareDtos.SharePageResponse` 增加 `coverThumbUrl` 与 `imageThumbs`，分享页按分享令牌有效期签名缩略图；扩展分享集成测试断言字段与回退所需原图 URL 同时存在。

## 3. 前端缩略图

- [x] 3.1 `utils/image.ts` 新增 `createThumbnail(file)`（480px、JPEG 0.8、≤512KB）；`api/notes.ts` 的 `presignUpload` 支持 `thumbOf`，上传流程先传原图再尽力上传缩略图，缩略图失败不阻断原图。运行 `npm run build` 通过。
- [x] 3.2 `RichTextEditor.vue` 插入图片时同时上传缩略图；缩略图上传失败只记录提示，不阻止图片插入和游记保存。
- [x] 3.3 `NotesView.vue` 列表封面优先 `coverThumbUrl`、失败回退 `coverImageUrl`；`NoteDetailView.vue` 图片网格优先 `thumbUrl`、点击查看使用原图；`ShareView.vue` 封面与图片网格优先缩略图并回退原图。运行 `npm run build` 通过。

## 4. MOV 提示

- [x] 4.1 `RichTextEditor.vue` 选择 MOV 时弹出确认框（Android/Chrome 可能无法播放，建议导出 MP4）；取消不上传，确认后继续。运行 `npm run build` 通过。

## 5. 管理员媒体审核队列

- [x] 5.1 后端：`GET /api/admin/notes` 增加 `mediaReview=PENDING|REVIEWED` 筛选；`AdminNoteView` 返回 `hasMedia` 与 `mediaReviewedAt`；新增 `PUT /api/admin/notes/{id}/media-review` 标记已审核并写审计 `NOTE_MEDIA_REVIEW`；编辑游记导致图片/视频变化时重置审核字段。集成测试覆盖待审核筛选、标记、幂等与编辑重置。
- [x] 5.2 `AdminNotesView.vue` 增加「媒体审核」筛选、状态列与详情中的「标记已审核」按钮；`api/admin.ts` 增加接口与类型。运行 `npm run build` 通过。

## 6. 文档同步与整体验收

- [x] 6.1 更新 `docs/ops/storage-media.md`：缩略图 Key 约定、生成方式、前端回退、清理保护与 MOV 提示。
- [x] 6.2 更新 `TravelMapper API  V1.0.md`：`thumbOf`、列表/详情/分享缩略图字段、管理员媒体审核筛选与接口。
- [x] 6.3 更新 `TravelMapper DB  V1.0.md`：`travel_note` 新增审核字段。
- [x] 6.4 运行后端全量测试、前端 `npm run build` 与 `openspec validate media-thumbnails-and-review --strict`，全部通过。