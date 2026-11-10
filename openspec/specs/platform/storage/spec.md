# Platform Storage Specification

## Purpose

为游记图片等对象提供与云厂商无关的 S3 兼容存储能力，通过配置在云 OSS 与本地 MinIO 之间切换，并用私有桶和预签名 URL 控制访问。

## Requirements

### Requirement: Provider-neutral storage contract
系统 MUST 通过统一的 `StorageProvider` 契约提供预签名上传、预签名下载、删除和存在性检查；云 OSS 与 MinIO MUST 共享同一套 S3 兼容实现，仅端点、区域、桶名和凭证不同。

#### Scenario: Switching storage backend
- **WHEN** 管理员把 `storage.provider` 从 `minio` 改为 `oss` 并保存
- **THEN** 新的预签名请求使用 OSS 配置，无需修改业务代码或重启应用

#### Scenario: Existing object remains reachable
- **WHEN** 存储配置切换后访问已有对象
- **THEN** 只要对象仍在当前桶中，下载签名仍可正常生成

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

### Requirement: Private bucket and signed downloads
存储桶 MUST 为私有，禁止公开读；普通下载签名有效期为 600 秒；分享页图片签名有效期 MUST 为分享令牌剩余有效期与 3600 秒中的较小值。

#### Scenario: Private object is not publicly readable
- **WHEN** 未携带签名直接访问对象 URL
- **THEN** 存储服务返回拒绝访问

#### Scenario: Share-page signature is capped
- **WHEN** 分享令牌剩余有效期超过 1 小时
- **THEN** 分享页图片签名有效期不超过 3600 秒

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

### Requirement: Storage connection test

管理员 MUST 能在对象存储配置页使用当前表单值测试连接，未填写的字段 MUST 回退到已保存配置。测试 MUST NOT 修改已保存配置，MUST 执行 `HeadBucket` 连通性探测，并在 `healthcheck/` 前缀上传、确认存在、删除一个临时对象，以验证凭证、签名与读写权限。测试 MUST 返回成功标识、耗时与可读错误信息；错误信息 MUST NOT 包含 AccessKey 或 SecretKey。页面 MUST 提示阿里云 OSS 需要关闭 Path Style（使用虚拟托管风格）。

#### Scenario: Reachable bucket passes the test
- **WHEN** 管理员填写正确的 Endpoint、Region、Bucket 与密钥并点击测试
- **THEN** 系统返回成功、耗时，并已删除临时测试对象

#### Scenario: Test does not change saved configuration
- **WHEN** 管理员在表单中填写新配置并测试
- **THEN** 已保存的存储配置保持不变，现有媒体仍使用原存储

#### Scenario: Invalid credentials fail with a readable message
- **WHEN** 管理员填写错误的 AccessKey/SecretKey 并测试
- **THEN** 系统返回失败与可读错误，不暴露密钥内容

#### Scenario: Blank key fields use saved credentials
- **WHEN** 管理员只修改 Endpoint 或 Bucket、密钥留空并测试
- **THEN** 系统使用已保存的密钥进行测试

#### Scenario: Alibaba OSS uses virtual-hosted style
- **WHEN** 管理员测试阿里云 OSS 且 Path Style 关闭
- **THEN** 测试通过；页面提示 OSS 仅支持虚拟托管风格
