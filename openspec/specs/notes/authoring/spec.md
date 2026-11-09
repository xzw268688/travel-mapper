# Notes Authoring Specification

## Purpose

定义图文游记的创建、查看、编辑与删除，包括富文本安全、图片直传与大小限制、国家/城市必选、行程关联与旅行归组，使游记成为地图绿色点亮与分享的可靠内容源。

## Requirements

### Requirement: Note creation
创建游记 MUST 校验：标题 1–50 字；正文 HTML UTF-8 字节 ≤200KB；图片为 `objectKey` 数组且 ≤20 张；`coverImage` MUST 属于 `images`；国家与城市必填且城市已启用；`visibility` 默认 `PRIVATE`。入库前 MUST 对标题和正文执行敏感词过滤并将命中词替换为 `***`，对正文执行 XSS 清洗，并生成纯文本摘要；标题过滤后若超过 50 字 MUST 截断为 50 字。

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

#### Scenario: Title sensitive words are filtered
- **WHEN** 标题包含配置的敏感词
- **THEN** 入库标题中的敏感词被替换为 `***`，且标题长度不超过 50 字

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
游记被下架后，本人 MUST 仍可查看详情，但 MUST 看到 `takedown=true`、下架原因与下架时间，并被告知可通过「意见反馈」申诉；本人 MUST NOT 能编辑或创建分享；他人访问分享页 MUST 得到失效结果。下架原因为空时 MUST 返回 NULL。

#### Scenario: Owner sees takedown flag
- **WHEN** 用户查看自己被下架的游记
- **THEN** 详情可见且 `takedown=true`

#### Scenario: Owner sees takedown reason
- **WHEN** 管理员填写原因后下架游记，作者查看详情
- **THEN** 返回下架原因与下架时间

#### Scenario: Owner cannot re-share
- **WHEN** 用户对被下架游记调用创建分享
- **THEN** 系统返回 `2024`

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

### Requirement: Note listing and search
本人游记页 MUST 提供按创建时间倒序的分页列表，默认每页 10 条；MUST 支持关键字匹配标题或正文纯文本摘要、按创建日期区间（`from`/`to`，闭区间）筛选。列表项 MUST 返回游记 `id`、标题、摘要、封面签名 URL、国家与城市名称、可见性、下架状态与创建时间。列表 MUST 支持直接打开与删除本人游记。页面 MUST NOT 要求用户输入游记 ID 才能打开游记。

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

#### Scenario: Missing cover degrades
- **WHEN** 封面文件不存在或签名失败
- **THEN** 列表显示「无封面」占位，不展示破图

### Requirement: Editor toolbar guidance
游记编辑器工具栏的每个操作按钮 MUST 提供悬停提示，说明其用途；有条件限制的操作 MUST 在提示中说明条件（例如「AI 润色」需先选中 1–5000 字文本，「撤销/重做」在无历史时不可用）。提示 MUST NOT 依赖点击后才展示，且 MUST NOT 遮挡正文编辑区域。

#### Scenario: Hovering a format button explains its purpose
- **WHEN** 用户将鼠标悬停在「加粗」或其他格式按钮上
- **THEN** 显示该按钮用途的提示

#### Scenario: Polish tooltip states the selection requirement
- **WHEN** 用户将鼠标悬停在「AI 润色」上
- **THEN** 提示说明需要先选中文本及字数范围

#### Scenario: Tooltips do not block editing
- **WHEN** 提示展示时
- **THEN** 正文编辑区域仍可正常点击与输入
