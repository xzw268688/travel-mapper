# 对象存储与游记媒体

本文档说明游记图片/视频的对象布局、签名有效期、保存校验与孤儿清理规则。新增媒体类型时必须同步更新本文档、上传白名单、数据库字段与清理引用。

## 对象 Key 布局

```
note/{userId}/{yyyy}/{MM}/{uuid}.{ext}
note/{userId}/{yyyy}/{MM}/{uuid}.{ext}.thumb.jpg
```

- `userId`：上传者 ID；保存游记时后端强制要求 objectKey 以此前缀开头。
- `yyyy/MM`：上传日期（北京时间）。
- `{uuid}`：服务端生成，禁止使用用户原始文件名。
- `.thumb.jpg`：客户端为图片生成的缩略图，Key 固定派生自原图，不单独入库。

媒体类型与扩展名：

| 媒体 | bizType | contentType | 扩展名 | 单文件上限 | 上传签名 TTL |
|---|---|---|---|---|---|
| 图片 | `NOTE_IMAGE` | `image/jpeg` / `image/png` / `image/webp` | jpg / png / webp | 5MB | 300 秒 |
| 图片缩略图 | `NOTE_IMAGE` + `thumbOf` | `image/jpeg` | `.thumb.jpg` | 512KB | 300 秒 |
| 视频 | `NOTE_VIDEO` | `video/mp4` / `video/webm` / `video/quicktime` | mp4 / webm / mov | 100MB | 1800 秒 |

- 每篇游记最多 20 张图片、3 个视频。
- 缩略图由前端在压缩后生成（480px、JPEG 0.8），通过 `thumbOf={原图 Key}` 申请签名；服务端只接受当前用户前缀内的原图 Key。
- 不做视频转码；MOV 在部分 Android/Chrome 设备可能无法播放，前端选择 MOV 时必须弹确认提示，取消则不上传。
- EXIF GPS 剥离由前端 canvas 压缩完成，仅对图片有效。

## 数据库字段

| 字段 | 说明 |
|---|---|
| `travel_note.images_json` | 图片 objectKey 数组（≤20） |
| `travel_note.videos_json` | 视频 objectKey 数组（≤3，V8 迁移新增） |
| `travel_note.cover_image` | 必须来自 `images_json`，不能是视频 |
| `travel_note.content` | 正文 HTML，媒体以 `/objects/{objectKey}` 相对路径嵌入 |
| `travel_note.media_reviewed_at` | 人工媒体审核时间；为空表示待审核 |
| `travel_note.media_reviewed_by` | 审核管理员 ID |

`videos_json` 与正文中的 `<video>` 节点同时写入：前者用于详情列表、封面校验和清理引用，后者用于渲染顺序。

## 签名与访问

- 上传：客户端拿预签名 URL 直接 PUT 到对象存储，不经过后端。
- 普通读取：图片/视频均为 600 秒预签名 GET URL；列表、详情网格与分享页优先使用 `.thumb.jpg` 的签名 URL，加载失败时前端回退原图。
- 分享页：只展示标题、摘要、封面与最多 6 张图片（均优先缩略图）；**不展示视频**。
- 存储桶必须私有，禁止匿名读。

## 保存时校验

创建/编辑游记时，`NoteMediaValidator` 对 `images` 与 `videos` 逐个校验：

1. objectKey 必须以 `note/{当前userId}/` 开头，且不能包含 `..`；
2. 调用存储 HEAD（`StorageProvider.exists`）确认对象存在；
3. 任一不满足 → `2027`；存储未配置/HEAD 失败 → `5003`；
4. 封面只允许来自图片列表。

缩略图是派生对象，不参与保存校验；历史游记没有缩略图时，读取接口仍返回 `.thumb.jpg` 签名 URL，由前端加载失败后回退原图。

历史游记如果引用了已被清理的对象，再次编辑保存会被拒绝；这是有意的数据完整性保护，需要恢复对象或从列表中移除该 key。

## 人工媒体审核

- 采用「先发后审」：含图片或视频的游记创建后立即按原可见性发布，同时进入后台待审核列表。
- 管理员在「内容管理 → 媒体审核」筛选 `PENDING`，打开详情核对图片/视频后点击「标记已审核」；操作写审计日志 `NOTE_MEDIA_REVIEW`。
- 编辑游记导致 `images` 或 `videos` 实际变化时，审核状态重置为待审核。
- 不含媒体的游记不进入待审核列表；审核本身不改变可见性，违规内容仍通过下架/举报流程处理。

## 孤儿清理

`StorageCleanupService` 每日执行（默认 `0 30 3 * * *`），仅删除同时满足以下条件的对象：

- 位于 `note/` 前缀；
- 最后修改时间早于当前时间 24 小时；
- 未被任何未删除游记的以下任一来源引用：
  - `images_json`
  - `videos_json`
  - 正文中的 `/objects/{objectKey}` 引用

- `{base}.thumb.jpg` 在原图 `{base}` 仍被引用时一并保留；原图成为孤儿时缩略图才被删除。

清理任务必须可重复执行；删除失败不应影响其他对象。

## 版本化约定

- **迁移只增不删**：媒体字段变更通过新增 Flyway 迁移完成，开发环境可按需回滚，生产环境优先保留列。
- **清理顺序**：先发布扩展了引用来源的新代码，再让清理任务运行；缩小引用范围前，先确认没有旧版本仍写旧字段。
- **新增媒体类型**：同步更新 `ObjectKeyGenerator`、`UploadController` 白名单、`StorageProvider` 扩展名、`NoteMediaValidator`、正文清洗白名单、清理引用与本文档。
- **Key 布局变更**：必须提升前缀版本（例如 `note-v2/`）并保留旧前缀清理逻辑，避免误删历史对象。
## 阿里云 OSS 配置与测试

项目使用 AWS SDK 的 S3 兼容接口访问 OSS。阿里云 OSS 的 S3 兼容 API 仅支持**虚拟托管风格**，因此必须关闭 Path Style。

### 配置项

| 配置 | 示例 | 说明 |
|---|---|---|
| Provider | `oss` | 仅用于展示，不参与路由 |
| Endpoint | `https://s3.oss-cn-hangzhou.aliyuncs.com` | 按 Bucket 地域替换；同地域内网可用 `-internal` 端点 |
| Region | `cn-hangzhou` | 必须与 Endpoint 地域一致 |
| Bucket | `your-bucket` | 私有读写 |
| Access Key / Secret Key | RAM 用户 AK/SK | 遵循最小权限，不要使用主账号密钥 |
| Path Style | **关闭** | OSS 仅支持虚拟托管风格 |
| 公开域名 | 可留空 | 用于 CDN/自定义域名时填写 |

### 测试连接（不切换存储）

进入后台「配置 → 对象存储」：

1. 填写 Endpoint、Region、Bucket、AccessKey、SecretKey，关闭 Path Style；
2. 点击「**测试连接**」；
3. 测试会临时创建存储客户端，执行 `HeadBucket`，并在 `healthcheck/` 前缀上传、确认、删除一个小对象；
4. 测试**不会保存配置**，也不会切换当前存储；现有 SeaweedFS/MinIO 媒体不受影响。

测试失败时页面会显示可读错误，例如签名不匹配、Bucket 不存在、权限不足或 Endpoint/Region 不一致。

### Bucket CORS（真实上传需要）

浏览器直传使用预签名 PUT，必须在 OSS Bucket 配置跨域规则：

- 来源：`http://localhost:5173`、`http://192.168.1.199:5173` 以及生产前端域名；
- 允许方法：`GET`、`PUT`、`POST`、`HEAD`；
- 允许 Headers：`*`；
- 暴露 Headers：`ETag`；
- 缓存时间：600 秒。

### RAM 最小权限

测试与日常使用至少需要：

```json
{
  "Effect": "Allow",
  "Action": [
    "oss:GetBucketInfo",
    "oss:ListObjects",
    "oss:PutObject",
    "oss:GetObject",
    "oss:DeleteObject"
  ],
  "Resource": [
    "acs:oss:*:*:your-bucket",
    "acs:oss:*:*:your-bucket/*"
  ]
}
```

### 切换前注意

- **不要只保存 OSS 配置就切换**：已有图片/视频的 objectKey 仍在原存储，切换后旧媒体会 404。
- 测试通过后，先把原 Bucket 的 `note/` 对象迁移到 OSS，再保存并切换；或在新环境使用全新 Bucket。
- 阿里云 OSS 与 S3 存在少量兼容差异；若测试返回签名/接口错误，先核对 Endpoint、Region 与 Path Style，再考虑使用阿里云 OSS 专用 SDK。
