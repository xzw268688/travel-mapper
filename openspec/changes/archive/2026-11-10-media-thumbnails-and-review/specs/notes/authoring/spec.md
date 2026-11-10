## MODIFIED Requirements

### Requirement: Image upload presign

系统 MUST 只为 `NOTE_IMAGE` 提供上传签名，仅接受 `image/jpeg`、`image/png`、`image/webp`，单图 ≤5MB；签名有效期 300 秒；对象 Key MUST 遵循存储规范。EXIF GPS 剥离由前端压缩阶段完成。系统 MUST 支持为已上传图片生成缩略图上传签名：请求携带 `thumbOf` 时，`thumbOf` MUST 属于当前用户，Key MUST 固定为 `{thumbOf}.thumb.jpg`，内容类型 MUST 为 `image/jpeg`，大小 MUST ≤512KB，签名有效期 MUST 为 300 秒；`thumbOf` 不属于当前用户或携带在视频请求中 MUST 被拒绝。

#### Scenario: Valid image gets presigned URL
- **WHEN** 用户请求上传 2MB 的 JPEG
- **THEN** 返回 `objectKey`、300 秒有效的上传 URL、`PUT` 方法与必要请求头

#### Scenario: Valid thumbnail gets presigned URL
- **WHEN** 用户为本人已上传图片请求缩略图签名
- **THEN** 返回以 `{thumbOf}.thumb.jpg` 结尾的对象 Key 与 300 秒有效的上传 URL

#### Scenario: Thumbnail for another user's object is rejected
- **WHEN** 请求的 `thumbOf` 不以 `note/{当前userId}/` 开头
- **THEN** 系统返回 `2001`，不生成签名

#### Scenario: Oversized thumbnail is rejected
- **WHEN** 缩略图超过 512KB
- **THEN** 系统返回图片大小错误，不生成签名

#### Scenario: SVG is rejected
- **WHEN** 用户请求上传 `image/svg+xml`
- **THEN** 系统返回 `2021`，不生成签名

#### Scenario: Oversized image is rejected
- **WHEN** 用户请求上传超过 5MB 的图片
- **THEN** 系统返回 `2019`，不生成签名

### Requirement: Note detail

详情 MUST 只返回本人未删除游记；非本人或不存在的游记 MUST 统一返回 `2004`；响应 MUST 包含签名图片 URL（600 秒）、缩略图签名 URL（600 秒）、国家/城市名、`cityDisabled`、关联行程/旅行信息、分享状态和 `takedown` 标记。每张图片 MUST 同时返回原图 `url` 与 `thumbUrl`；历史图片没有缩略图对象时 MUST 仍返回 `thumbUrl`，由客户端加载失败后回退原图。

#### Scenario: Owner reads note detail
- **WHEN** 用户读取自己的游记
- **THEN** 返回完整游记、600 秒原图签名 URL 与缩略图签名 URL

#### Scenario: Thumbnail URL degrades to original
- **WHEN** 历史游记的图片没有缩略图对象
- **THEN** 详情仍返回 `thumbUrl`，客户端加载失败时使用原图 `url`

#### Scenario: Other user's note is hidden
- **WHEN** 用户读取他人游记
- **THEN** 系统返回 `2004`

#### Scenario: Disabled city is displayed
- **WHEN** 游记引用了已停用城市
- **THEN** 详情仍正常展示城市名并返回 `cityDisabled=true`

### Requirement: Video upload presign

系统 MUST 支持 `NOTE_VIDEO` 上传签名，接受 `video/mp4`、`video/webm` 与 `video/quicktime`，单个视频 MUST ≤100MB；视频上传签名有效期 MUST 为 1800 秒。对象 Key MUST 遵循 `note/{userId}/{yyyy}/{MM}/{uuid}.{ext}`，扩展名仅允许 mp4、webm、mov，MUST NOT 使用用户原始文件名。系统 MUST NOT 在服务端转码视频。MOV/HEVC 视频可能无法在部分 Android/Chrome 设备播放，前端 MUST 在选择 MOV 后、上传前展示确认提示，用户取消时 MUST NOT 上传，确认后才 MUST 继续。

#### Scenario: Valid video gets 30-minute presigned URL
- **WHEN** 用户请求上传 50MB 的 MP4
- **THEN** 返回 `objectKey`、1800 秒有效的上传 URL、`PUT` 方法与必要请求头

#### Scenario: Valid MOV gets presigned URL
- **WHEN** iPhone 用户确认提示后请求上传 `video/quicktime` 的 `.mov` 视频
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
- **WHEN** 用户选择 MOV 视频准备上传
- **THEN** 页面提示 MOV/HEVC 在部分 Android/Chrome 设备可能无法播放，建议导出 MP4 分享

#### Scenario: MOV playback caveat requires confirmation
- **WHEN** 用户选择 MOV 视频准备上传
- **THEN** 取消确认框后不上传，确认后才继续上传

### Requirement: Note listing and search

本人游记页 MUST 提供按创建时间倒序的分页列表，默认每页 10 条；MUST 支持关键字匹配标题或正文纯文本摘要、按创建日期区间（`from`/`to`，闭区间）筛选。列表项 MUST 返回游记 `id`、标题、摘要、封面原图签名 URL、封面缩略图签名 URL、国家与城市名称、可见性、下架状态与创建时间。列表 MUST 支持直接打开与删除本人游记。页面 MUST NOT 要求用户输入游记 ID 才能打开游记。封面缩略图缺失时前端 MUST 回退原图或「无封面」占位。

#### Scenario: Recent notes are listed newest first
- **WHEN** 用户打开游记页
- **THEN** 按创建时间倒序展示本人未删除游记，并给出总数

#### Scenario: Keyword matches title or summary
- **WHEN** 用户输入关键字并查询
- **THEN** 返回标题或正文摘要包含该关键字的游记

#### Scenario: Date range filters by creation time
- **WHEN** 用户选择开始与结束日期并查询
- **THEN** 只返回创建时间落在区间内的游记

#### Scenario: Open and delete from the list
- **WHEN** 用户在列表中点击「打开」或「删除」
- **THEN** 分别进入游记详情或确认后删除该游记，并刷新列表

#### Scenario: No ID input is required
- **WHEN** 用户想查看已有游记
- **THEN** 直接从列表选择，页面不出现游记 ID 输入框

#### Scenario: List uses cover thumbnail
- **WHEN** 游记封面存在缩略图对象
- **THEN** 列表优先加载 `coverThumbUrl`，不加载原图

#### Scenario: Missing cover degrades
- **WHEN** 封面文件或缩略图不存在、签名失败
- **THEN** 列表依次回退原图与「无封面」占位，不展示破图