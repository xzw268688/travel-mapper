# Notes Authoring Specification

## Purpose

定义图文游记的创建、查看、编辑与删除，包括富文本安全、图片直传与大小限制、国家/城市必选、行程关联与旅行归组，使游记成为地图绿色点亮与分享的可靠内容源。

## Requirements

### Requirement: Note creation
创建游记 MUST 校验：标题 1–50 字；正文 HTML UTF-8 字节 ≤200KB；图片为 `objectKey` 数组且 ≤20 张；`coverImage` MUST 属于 `images`；国家与城市必填且城市已启用；`visibility` 默认 `PRIVATE`。入库前 MUST 做 XSS 与敏感词过滤，并生成纯文本摘要。

#### Scenario: Valid note is created and lights map green
- **WHEN** 用户提交合法游记
- **THEN** 游记保存成功，返回 `countryStatusAfter=GREEN`，默认仅本人可见

#### Scenario: Missing country or city is rejected
- **WHEN** 创建游记时未提供国家或城市
- **THEN** 系统返回 `2001` 或 `2002`，不保存

#### Scenario: Too many images are rejected
- **WHEN** 提交超过 20 张图片
- **THEN** 系统返回 `2018`，不保存

#### Scenario: Oversized content is rejected
- **WHEN** 正文 UTF-8 字节超过 200KB
- **THEN** 系统返回 `2020`，不保存

#### Scenario: Script tags are sanitized
- **WHEN** 正文包含 `<script>` 或其他可执行标记
- **THEN** 脚本被移除或转义，展示时不会执行

### Requirement: Image upload presign
系统 MUST 只为 `NOTE_IMAGE` 提供上传签名，仅接受 `image/jpeg`、`image/png`、`image/webp`，单图 ≤5MB；签名有效期 300 秒；对象 Key MUST 遵循存储规范。EXIF GPS 剥离由前端压缩阶段完成。

#### Scenario: Valid image gets presigned URL
- **WHEN** 用户请求上传 2MB 的 JPEG
- **THEN** 返回 `objectKey`、300 秒有效的上传 URL、`PUT` 方法与必要请求头

#### Scenario: SVG is rejected
- **WHEN** 用户请求上传 `image/svg+xml`
- **THEN** 系统返回 `2021`，不生成签名

#### Scenario: Oversized image is rejected
- **WHEN** 用户请求上传超过 5MB 的图片
- **THEN** 系统返回 `2019`，不生成签名

### Requirement: Itinerary association and trip grouping
关联行程时 MUST 以行程为准继承国家、城市与 `tripId`，入参冲突时忽略入参；行程不属于本人 MUST 返回 `2003`。未关联行程时 MUST 允许手动选择同国家的旅行或未归组，跨国家归组 MUST 返回 `2001`；默认选中该国家最近一次旅行。

#### Scenario: Linked note inherits all fields
- **WHEN** 用户创建游记并关联某条日本行程
- **THEN** 游记的国家、城市与 `tripId` 均来自该行程，`tripInherited=true`

#### Scenario: Conflicting input is ignored
- **WHEN** 关联行程为东京，但请求传入大阪
- **THEN** 系统以行程的东京为准保存

#### Scenario: Cross-country manual grouping is rejected
- **WHEN** 用户把日本游记归到泰国旅行
- **THEN** 系统返回 `2001`

#### Scenario: Ungrouped note is allowed
- **WHEN** 用户不关联行程并选择未归组
- **THEN** 游记保存成功，`trip_id` 为 NULL

### Requirement: Note detail
详情 MUST 只返回本人未删除游记；非本人或不存在的游记 MUST 统一返回 `2004`；响应 MUST 包含签名图片 URL（600 秒）、国家/城市名、`cityDisabled`、关联行程/旅行信息、分享状态和 `takedown` 标记。

#### Scenario: Owner reads note detail
- **WHEN** 用户读取自己的游记
- **THEN** 返回完整游记与 600 秒图片签名 URL

#### Scenario: Other user's note is hidden
- **WHEN** 用户读取他人游记
- **THEN** 系统返回 `2004`

#### Scenario: Disabled city is displayed
- **WHEN** 游记引用了已停用城市
- **THEN** 详情仍正常展示城市名并返回 `cityDisabled=true`

### Requirement: Note edit
编辑 MUST 支持部分更新，但 `content`、`images`、`visibility` 至少传一项；编辑 MUST 重新执行 XSS/敏感词过滤与大小限制；已下架游记 MUST 返回 `2024`，不可编辑。

#### Scenario: Partial edit succeeds
- **WHEN** 用户只修改游记标题或可见性
- **THEN** 其他字段保持不变，修改成功

#### Scenario: Takedown note cannot be edited
- **WHEN** 用户编辑已被管理员下架的游记
- **THEN** 系统返回 `2024`

#### Scenario: Edit revalidates limits
- **WHEN** 编辑后正文超过 200KB 或图片超过 20 张
- **THEN** 系统返回 `2020` 或 `2018`，原内容不变

### Requirement: Note deletion
删除 MUST 为软删除，并 MUST 在同一事务内撤销该游记的全部分享令牌；MUST 返回删除后国家的 `countryStatusAfter`。

#### Scenario: Delete revokes shares
- **WHEN** 用户删除一篇有分享令牌的游记
- **THEN** 游记软删除，所有分享令牌撤销，返回 `sharesRevoked`

#### Scenario: Delete updates map status
- **WHEN** 用户删除某国最后一篇游记且仍有行程
- **THEN** `countryStatusAfter=YELLOW`；若无行程则为 `GRAY`

### Requirement: Takedown visibility for owner
游记被下架后，本人 MUST 仍可查看详情，但 MUST 看到 `takedown=true`，且不可编辑、不可创建分享；他人访问分享页 MUST 得到失效结果。

#### Scenario: Owner sees takedown flag
- **WHEN** 用户查看自己被下架的游记
- **THEN** 详情可见且 `takedown=true`

#### Scenario: Owner cannot re-share
- **WHEN** 用户对被下架游记调用创建分享
- **THEN** 系统返回 `2024`

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
