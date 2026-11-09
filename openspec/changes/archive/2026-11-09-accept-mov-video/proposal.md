Triage: ready-for-agent

## Why

iPhone 相机默认把视频录成 `.mov`（`video/quicktime`），而当前上传白名单只允许 `video/mp4` 与 `video/webm`，导致苹果手机用户无法上传游记视频。

## What Changes

- `NOTE_VIDEO` 上传签名白名单加入 `video/quicktime`；对象 Key 扩展名映射为 `mov`。
- 前端视频选择与校验加入 `video/quicktime` / `.mov`，并提示 MOV/HEVC 在部分 Android/Chrome 设备可能无法播放。
- 更新 API 文档中的视频类型说明；补充 `ObjectKeyGenerator` 与上传接口测试。
- 非目标：服务端转码为 MP4/H.264。跨设备统一播放需要 ffmpeg 转码，作为后续独立 change。

## Capabilities

### Modified Capabilities

- `notes/authoring`: 视频上传签名接受 `video/quicktime`（`.mov`），并明确 MOV/HEVC 的跨设备播放限制。

## Impact

- 后端：`UploadController`、`ObjectKeyGenerator`、相关测试。
- 前端：`RichTextEditor.vue`、`NotesView.vue` 的类型校验与提示文案。
- 文档：`TravelMapper API  V1.0.md` 上传接口的视频类型列表。
- 测试：`ObjectKeyGeneratorTest`、新增上传签名集成测试。