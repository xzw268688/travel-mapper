## ADDED Requirements

### Requirement: Video upload presign
系统 MUST 支持 `NOTE_VIDEO` 上传签名，仅接受 `video/mp4` 与 `video/webm`，单个视频 MUST ≤100MB；视频上传签名有效期 MUST 为 1800 秒。对象 Key MUST 遵循 `note/{userId}/{yyyy}/{MM}/{uuid}.{ext}`，扩展名仅允许 mp4、webm，MUST NOT 使用用户原始文件名。

#### Scenario: Valid video gets 30-minute presigned URL
- **WHEN** 用户请求上传 50MB 的 MP4
- **THEN** 返回 `objectKey`、1800 秒有效的上传 URL、`PUT` 方法与必要请求头

#### Scenario: Unsupported video type is rejected
- **WHEN** 用户请求上传 `video/quicktime` 或其他非白名单类型
- **THEN** 系统返回 `2030`，不生成签名

#### Scenario: Oversized video is rejected
- **WHEN** 用户请求上传超过 100MB 的视频
- **THEN** 系统返回 `2029`，不生成签名

#### Scenario: Image TTL is unchanged
- **WHEN** 用户请求图片上传签名
- **THEN** 有效期仍为 300 秒

### Requirement: Note video fields and validation
游记创建与编辑 MUST 接受 `videos`（objectKey 数组），数量 MUST ≤3；未提供时 MUST 保持原值（创建时为空）。视频 MUST 以自定义视频节点嵌入正文，保存时以相对路径 `/objects/{objectKey}` 持久化，展示/编辑时 MUST 替换为 600 秒签名 URL。`coverImage` MUST 仍只允许来自 `images`。仅包含视频、没有文字的正文 MUST 被视为有效内容。分享页与海报的视频展示不在本要求范围内。

#### Scenario: Video is attached to a note
- **WHEN** 用户上传 1 个 MP4 并保存游记
- **THEN** 详情返回 `videos` 数组，含 objectKey 与 600 秒签名 URL，正文可播放

#### Scenario: Fourth video is rejected
- **WHEN** 提交 4 个视频
- **THEN** 系统返回 `2028`，不保存

#### Scenario: Video-only note is allowed
- **WHEN** 正文只包含一个视频节点、没有文字
- **THEN** 前端允许保存，后端接受

#### Scenario: Video cover is rejected
- **WHEN** `coverImage` 指向视频而不是图片
- **THEN** 系统返回 `2001`，不保存

### Requirement: Referenced media validation
创建或编辑游记时，系统 MUST 校验所有 `images` 与 `videos` 的 objectKey：MUST 以 `note/{当前userId}/` 开头；MUST 通过存储 HEAD 确认对象存在。不存在、不属于当前用户或格式非法的对象 MUST 返回 `2027`，整单不保存；存储服务不可用 MUST 返回 `5003`。校验 MUST 在 XSS 清洗与入库之前完成。

#### Scenario: Missing object blocks save
- **WHEN** 用户提交一个从未上传成功或已被清理的 objectKey
- **THEN** 系统返回 `2027`，游记不保存

#### Scenario: Other user's object is rejected
- **WHEN** 用户提交以其他用户 ID 为前缀的 objectKey
- **THEN** 系统返回 `2027`，不保存

#### Scenario: Storage unavailable blocks save
- **WHEN** 存储服务未配置或 HEAD 请求失败
- **THEN** 系统返回 `5003`，不保存

#### Scenario: Valid objects pass
- **WHEN** 所有图片与视频均为本人上传且存在
- **THEN** 游记正常保存，媒体字段按原顺序返回