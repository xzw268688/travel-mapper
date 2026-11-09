Triage: ready-for-agent

## Why

管理员想验证阿里云 OSS 或其他 S3 兼容存储是否能正常使用，但当前只能保存配置后由「存储监控」探测；保存会立即切换全局存储，导致 SeaweedFS/MinIO 中的旧图片与视频无法访问。需要一个只测试、不切换的连通性检查。

## What Changes

- 对象存储配置页新增「测试连接」按钮：使用当前表单值（未填字段回退到已保存配置）创建临时存储客户端，不修改已保存配置。
- 测试内容：`HeadBucket` 探测 + 在 `healthcheck/` 前缀上传一个很小的临时对象、确认存在、再删除；验证连通性、凭证、写入/读取/删除权限与签名。
- 返回是否成功、耗时与可读错误；错误信息不得包含 AccessKey/SecretKey。
- 页面提示阿里云 OSS 必须关闭 Path Style（虚拟托管风格），并给出 Endpoint/Region 示例。
- 补充 OSS 配置与 CORS 说明文档。

非目标：自动迁移已有对象、双存储并行、对象复制/同步。

## Capabilities

### Modified Capabilities

- `platform/storage`: 新增「存储连接测试」能力，允许在不切换全局配置的情况下验证 S3 兼容存储。

## Impact

- 后端：新增 `StorageConnectionTestService`、`StorageTestResult`、`POST /api/admin/config/oss/test`。
- 前端：`AdminSettingsView` 对象存储页签增加「测试连接」按钮；`api/admin.ts` 增加测试接口。
- 文档：`docs/ops/storage-media.md` 补充阿里云 OSS 配置、Path Style、CORS 与测试步骤。
- 测试：S3Mock 集成测试覆盖测试成功与临时对象清理；错误路径覆盖未配置。