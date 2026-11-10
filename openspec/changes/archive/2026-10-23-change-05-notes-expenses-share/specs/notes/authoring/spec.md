## Purpose

定义图文游记的创建、查看、编辑与删除，包括富文本安全、图片直传与大小限制、国家/城市必选、行程关联与旅行归组，使游记成为地图绿色点亮与分享的可靠内容源。

## ADDED Requirements

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
