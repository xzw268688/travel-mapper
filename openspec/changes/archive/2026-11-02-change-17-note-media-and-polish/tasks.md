## 1. 后端视频与媒体校验

- [x] 1.1 新增 `V8__note_videos.sql`（`travel_note.videos_json`）与 `TravelNote.videosJson` 字段；验证启动迁移成功
- [x] 1.2 扩展 `ObjectKeyGenerator` 与 `UploadController`：支持 `NOTE_VIDEO`（mp4/webm、≤100MB、1800 秒签名），新增错误码 `2027`–`2030`；用集成测试验证合法视频、超限、非法类型与图片 TTL 不变
- [x] 1.3 扩展 `NoteDtos`/`NoteService`：创建、编辑、详情读写 `videos`（≤3），封面仅图片；保存前校验 `note/{userId}/` 前缀与逐个 HEAD，缺失/越权返回 `2027`、存储不可用返回 `5003`；验证已有图片游记仍可保存
- [x] 1.4 扩展 Jsoup 白名单保留 `<video controls src="/objects/...">` 相对路径，并让摘要/内容判定不因视频报错
- [x] 1.5 扩展 `StorageCleanupService` 与 Mapper：引用集合为 `images_json ∪ videos_json ∪ 正文 /objects/`；用单元/集成测试覆盖视频引用、正文引用与幂等清理
- [x] 1.6 后端全量测试通过（媒体测试在对象存储不可用时跳过）

## 2. 编辑器内 AI 润色

- [x] 2.1 在 `RichTextEditor` 工具栏增加风格选择与「AI 润色」按钮；无选区或超过 5000 字时阻止并提示，验证不发起请求
- [x] 2.2 实现流式预览、生成期间只读、完成后原位替换、撤销可恢复；取消先调用 `/api/ai/cancel` 再 abort，失败保留原文并可重试
- [x] 2.3 删除 `PolishView.vue`、`/polish` 路由与导航入口；构建确认无残留引用

## 3. 前端视频

- [x] 3.1 实现 TipTap 视频节点与 `contentToPreview`/`previewToContent`/`hasContent` 的视频处理；验证视频-only 正文可保存
- [x] 3.2 在 `api/notes.ts` 增加 `uploadVideo` 与 `videos` 类型；编辑器增加「插入视频」入口，校验 mp4/webm、≤100MB、每篇 ≤3，上传后插入节点
- [x] 3.3 `NotesView`、`NoteDetailView` 维护 `videos` 列表、提交与展示；详情按 600 秒签名 URL 播放，封面列表仅图片
- [x] 3.4 前端 `npm run build` 通过

## 4. 验收与文档

- [x] 4.1 Playwright 覆盖：编辑器选中润色后替换、取消不修改正文、视频上传/播放（或对象存储不可用时显式跳过）并在全量套件通过
- [x] 4.2 生成桌面/移动端截图确认润色面板与视频布局
- [x] 4.3 更新 API、SRS、`frontend/README.md`，新增 `docs/ops/storage-media.md`（对象布局、白名单、TTL、清理与版本化约定）
- [x] 4.4 `openspec validate change-17-note-media-and-polish --strict` 通过并归档