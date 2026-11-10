## MODIFIED Requirements

### Requirement: Presigned upload constraints

上传签名 MUST 由服务端生成；图片签名有效期为 300 秒，视频签名有效期为 1800 秒。对象 Key MUST 遵循 `note/{userId}/{yyyy}/{MM}/{uuid}.{ext}`，禁止使用用户提供的原始文件名。图片扩展名仅允许 jpg、png、webp；视频扩展名仅允许 mp4、webm、mov。上传类型、大小与业务类型 MUST 由服务端校验，MUST NOT 仅依赖客户端声明。缩略图 MUST 使用 `{原图 Key}.thumb.jpg` 的派生 Key，仅允许当前用户为本人图片申请，内容类型仅允许 `image/jpeg`，大小 MUST ≤512KB；服务端 MUST NOT 接受客户端指定的任意缩略图 Key。

#### Scenario: Valid image upload is presigned
- **WHEN** 已登录用户为 jpg/png/webp 图片请求上传签名
- **THEN** 系统返回 300 秒有效的上传 URL、必要请求头与符合规定的 Key

#### Scenario: Valid video upload is presigned
- **WHEN** 已登录用户为 mp4/webm/mov、≤100MB 的视频请求上传签名
- **THEN** 系统返回 1800 秒有效的上传 URL、必要请求头与符合规定的 Key

#### Scenario: Disallowed image type is rejected
- **WHEN** 用户请求上传 SVG 或其他不在图片白名单的类型
- **THEN** 系统拒绝签名请求并返回图片类型错误

#### Scenario: Disallowed type is rejected
- **WHEN** 用户请求上传不在对应媒体白名单内的类型（如 SVG、AVI）
- **THEN** 系统拒绝签名请求并返回对应类型错误

#### Scenario: Disallowed video type is rejected
- **WHEN** 用户请求上传非 mp4/webm/mov 的视频
- **THEN** 系统返回 `2030`，不生成签名

#### Scenario: Oversized media is rejected
- **WHEN** 图片超过 5MB、缩略图超过 512KB 或视频超过 100MB
- **THEN** 系统返回对应大小错误，不生成签名

#### Scenario: Thumbnail uses derived key
- **WHEN** 当前用户为本人图片申请缩略图签名
- **THEN** 服务端返回 `{原图 Key}.thumb.jpg`，不接受其他 Key 形式

#### Scenario: Thumbnail for another user is rejected
- **WHEN** 请求的 `thumbOf` 不属于当前用户
- **THEN** 系统拒绝签名，不暴露他人对象

### Requirement: Orphan object cleanup

系统 SHALL 每日清理上传成功但超过 24 小时仍未被任何游记引用的对象；引用判定 MUST 同时覆盖 `images_json`、`videos_json` 与正文中对 `/objects/{objectKey}` 的引用。缩略图 `{base}.thumb.jpg` MUST 在 `{base}` 仍被引用时保留，仅在原图本身成为孤儿时清理。清理任务 MUST 可重复执行，且 MUST NOT 删除任何仍被引用的对象。

#### Scenario: Unreferenced object is removed
- **WHEN** 一个图片或视频对象上传超过 24 小时且未被任何游记的 `images_json`、`videos_json` 或正文引用
- **THEN** 清理任务删除该对象

#### Scenario: Referenced image is retained
- **WHEN** 图片对象已写入任意游记的 `images_json`
- **THEN** 清理任务跳过该对象

#### Scenario: Referenced object is retained
- **WHEN** 对象已被任意游记通过 `images_json`、`videos_json` 或正文引用
- **THEN** 清理任务跳过该对象

#### Scenario: Referenced video is retained
- **WHEN** 视频对象已写入任意游记的 `videos_json`
- **THEN** 清理任务跳过该对象

#### Scenario: Content-only reference is retained
- **WHEN** 对象仅在游记正文中以 `/objects/{objectKey}` 出现，未写入 `images_json` 或 `videos_json`
- **THEN** 清理任务仍跳过该对象

#### Scenario: Thumbnail of referenced image is retained
- **WHEN** 缩略图 `{base}.thumb.jpg` 的对象超过 24 小时，但 `{base}` 仍被游记引用
- **THEN** 清理任务跳过该缩略图

#### Scenario: Thumbnail of orphan image is removed
- **WHEN** 缩略图 `{base}.thumb.jpg` 超过 24 小时且 `{base}` 未被任何游记引用
- **THEN** 清理任务删除该缩略图

#### Scenario: Cleanup is idempotent
- **WHEN** 清理任务连续执行两次
- **THEN** 第二次不重复删除已删除对象，也不影响已引用对象