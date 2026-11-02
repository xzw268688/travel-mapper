## Context

见 `proposal.md`。现有编辑器 `RichTextEditor` 已封装 TipTap、图片压缩上传与 `/objects/` 相对路径转换；润色 API 已具备 SSE、取消与退款规则，但只被独立 `/polish` 页面使用。存储层已有预签名上传、`exists` HEAD 与孤儿清理，但只服务图片。

## Goals / Non-Goals

**Goals:**

- 润色在原编辑器内完成，不打断写作。
- 视频与图片共用直传、签名访问和清理体系。
- 保存前阻止无效/越权媒体，清理不误删正文引用对象。

**Non-Goals:**

- 不改变润色 SSE 协议、额度与退款规则。
- 不支持视频压缩、转码、封面截帧或流媒体分片；浏览器直接播放原文件。
- 分享页、海报、摘要不展示视频。
- 不支持音频、GIF 或用户原始文件名。

## Decisions

1. **数据库新增 `travel_note.videos_json`**
   - 与 `images_json` 对称，存 objectKey 数组；null 表示无视频。
   - 备选：把视频键混入 `images_json`。会让封面、分享图片白名单与 20 张上限语义混乱，不采用。

2. **上传签名按媒体类型区分 TTL 与白名单**
   - `UploadController` 根据 `bizType` 选择：`NOTE_IMAGE` → 5 种图片规则 + 300 秒；`NOTE_VIDEO` → mp4/webm + ≤100MB + 1800 秒。
   - `StorageUrlService` 增加带 TTL 的重载；图片行为不变。
   - 新增错误码：`2027` 媒体对象无效、`2028` 视频数量超限、`2029` 视频大小超限、`2030` 视频类型不支持。

3. **保存前做归属前缀 + HEAD 存在性校验**
   - 所有 `images`/`videos` 必须匹配 `note/{当前userId}/`，否则 `2027`。
   - 逐个调用 `StorageProvider.exists`（HEAD）；不存在返回 `2027`；存储不可用抛出 `5003`，整单不保存。
   - 备选：只校验格式不 HEAD。无法发现上传中断造成的坏对象，不采用。
   - 上限校验顺序：数量/大小/类型在 presign 阶段，归属与存在性在保存阶段；前端同样在插入前校验。

4. **视频使用自定义 TipTap 节点，正文与列表双写**
   - 正文节点渲染 `<video controls preload="metadata" src="/objects/{key}">`；保存时 JSON 数组记录同一批 key，便于详情、封面排除与清理。
   - `contentToPreview`/`previewToContent` 同时处理 `img` 与 `video`；`hasContent` 将 video 视为有效内容。
   - 后端 Jsoup 白名单增加 `video` 标签与 `src/controls/preload/poster/width/height` 属性，保留相对 `src`。
   - 备选：不写 `videos_json`，只从正文解析。清理与详情都要重复解析 HTML，且无法保证顺序，不采用。

5. **编辑器内润色采用“流式预览 + 完成后替换”**
   - 点击润色时记录 `{from,to,text}`，生成期间 `editor.setEditable(false)`，在工具栏下方显示流式预览、状态、取消按钮。
   - `done` 后恢复可编辑，用 `deleteRange + insertContent` 原位替换；换行转成 `<br>`，用户可用撤销恢复。
   - 取消：先 `POST /api/ai/cancel` 再 abort，不修改正文；失败保留原文并提供重试（新 `requestId`）。
   - 备选：每个 delta 直接写入选区。位置漂移、失败后原文丢失且难撤销，不采用。

6. **删除独立润色页面**
   - 删除 `PolishView.vue`、`/polish` 路由与导航链接；保留 `api/ai.ts` 的 SSE/cancel 能力供编辑器调用。

7. **孤儿清理扩展为三类引用并集**
   - `TravelNoteMapper` 增加读取 `videos_json` 与 `content` 的查询；正文用正则提取 `/objects/...`。
   - 清理对象为 `images_json ∪ videos_json ∪ 正文引用`；仍保留 24 小时宽限期。
   - 备选：只清理已知扩展名。正文引用可能包含任意扩展名，不采用。

8. **新增存储介质运维文档**
   - 记录 Key 布局、媒体白名单、各 TTL、HEAD 校验、清理规则与“新增媒体类型需要同步扩展名/白名单/清理”的版本化约定。

## Risks / Trade-offs

- **100MB 视频上传占用浏览器内存** → 直接以 `File` 作为 `fetch` body，不做前端压缩；显示上传进度/禁用重复提交。
- **600 秒播放签名过期** → 与图片一致，页面重新加载即可刷新；不引入 CDN 或长时签名。
- **保存时存储不可用会阻止游记保存** → 与“避免无效媒体”目标一致；错误码明确为 `5003`，用户可稍后重试。
- **历史游记引用了已清理对象** → 再次编辑并保存会返回 `2027`；这是有意的数据完整性保护，需在文档中说明。
- **视频文件过大影响海报/摘要** → 分享页与海报不读取视频，不增加渲染负担。
- **测试环境无 S3** → 媒体保存测试在对象存储可用时运行，否则跳过；同时在 Service 层用假存储覆盖 HEAD 失败/成功分支。

## Migration Plan

1. 执行 Flyway `V8__note_videos.sql`（新增可空列，无回填）。
2. 先发布后端，再发布前端；旧客户端不传 `videos` 时保持兼容。
3. 回滚前端后，视频字段仍保留但不展示；回滚后端需同时回滚 V8（开发环境）或保留列（生产建议保留）。
4. 对象存储无需迁移；已有图片 Key 与 300 秒 TTL 行为不变。

## Open Questions

无。