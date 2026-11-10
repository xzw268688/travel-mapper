## MODIFIED Requirements

### Requirement: Video upload presign
系统 MUST 支持 `NOTE_VIDEO` 上传签名，接受 `video/mp4`、`video/webm` 与 `video/quicktime`，单个视频 MUST ≤100MB；视频上传签名有效期 MUST 为 1800 秒。对象 Key MUST 遵循 `note/{userId}/{yyyy}/{MM}/{uuid}.{ext}`，扩展名仅允许 mp4、webm、mov，MUST NOT 使用用户原始文件名。MOV/HEVC 视频可能无法在部分 Android/Chrome 设备播放，前端 MUST 在视频上传说明中提示该限制。

#### Scenario: Valid video gets 30-minute presigned URL
- **WHEN** 用户请求上传 50MB 的 MP4
- **THEN** 返回 `objectKey`、1800 秒有效的上传 URL、`PUT` 方法与必要请求头

#### Scenario: Valid MOV gets presigned URL
- **WHEN** iPhone 用户请求上传 `video/quicktime` 的 `.mov` 视频
- **THEN** 系统生成以 `.mov` 结尾的 objectKey 与 1800 秒有效的上传 URL

#### Scenario: Unsupported video type is rejected
- **WHEN** 用户请求上传 `video/x-msvideo` 或其他非白名单类型
- **THEN** 系统返回 `2030`，不生成签名

#### Scenario: Oversized video is rejected
- **WHEN** 用户请求上传超过 100MB 的视频
- **THEN** 系统返回 `2029`，不生成签名

#### Scenario: Image TTL is unchanged
- **WHEN** 用户请求图片上传签名
- **THEN** 有效期仍为 300 秒

#### Scenario: MOV playback caveat is shown
- **WHEN** 用户准备上传视频
- **THEN** 页面提示 MOV/HEVC 在部分 Android/Chrome 设备可能无法播放，建议导出 MP4 分享