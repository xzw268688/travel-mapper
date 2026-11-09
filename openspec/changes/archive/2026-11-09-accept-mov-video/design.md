## Context

见 `proposal.md`。现有上传链路为前端校验 → `POST /api/upload/presign` → 直接 PUT 到对象存储；后端只在签名阶段校验 MIME 并生成对象 Key。`notes/authoring` 规格目前只允许 mp4/webm。

## Goals / Non-Goals

**Goals**

- iPhone 默认 `.mov` 视频可以完成上传并保存到游记。
- 明确提示 MOV/HEVC 的跨设备播放限制。

**Non-Goals**

- 服务端转码、H.264 统一播放、上传后异步处理。

## Decisions

- **直接加入 `video/quicktime` 白名单**：`UploadController.VIDEO_TYPES` 与 `ObjectKeyGenerator` 扩展名映射同时扩展；前端 `accept` 与 `file.type` 校验同步。替代方案是只在前端转换，但 iOS 浏览器缺少可靠的本地转码能力。
- **扩展名固定为 `mov`**：仍不使用用户原始文件名，保持现有 Key 规则与安全边界。
- **播放限制用文案提示**：在视频上传说明旁标注 MOV/HEVC 在部分 Android/Chrome 可能无法播放；不做运行时设备检测，避免误导。
- 服务端转码作为后续独立 change，不在本次实现。

## Risks / Trade-offs

- [Android/Chrome 可能无法播放 MOV/HEVC] → 上传说明明确提示，并建议导出 MP4 后分享；后续可增加服务端转码。
- [用户上传超大 MOV] → 仍受 100MB 上限约束。
- [对象存储按扩展名识别] → Key 使用 `.mov`，S3 不依赖扩展名，播放由浏览器能力决定。

## Migration Plan

1. 部署前后端：开始接受 `video/quicktime`。
2. 无需数据库迁移；既有 mp4/webm 行为不变。
3. 回滚时移除白名单项即可，已上传的 `.mov` 对象仍保留。

## Open Questions

无。