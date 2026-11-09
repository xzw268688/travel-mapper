## Context

`StorageProviderHolder` 保存当前生效的 `StorageConfig`；`S3CompatibleStorageProvider` 可直接用配置构造临时客户端。现有存储监控的 `probe()` 只做 `HeadBucket`，无法验证写权限与签名上传。直接保存 OSS 配置会切换全局存储，导致旧对象不可访问。

## Goals / Non-Goals

**Goals**

- 在不保存、不切换的前提下验证 OSS/MinIO/S3 的连通、凭证、签名与读写权限。
- 对阿里云 OSS 的虚拟托管风格给出明确提示。

**Non-Goals**

- 对象迁移、双存储并行、自动切换、CORS 自动配置。

## Decisions

- **临时构造 `S3CompatibleStorageProvider`**：用表单值与已保存配置合并出 `StorageConfig`，在 `healthcheck/` 前缀做 probe → presigned PUT → HEAD → DELETE，完成后关闭临时客户端。替代方案是保存后探测，但会切换存储并破坏旧媒体。
- **合并规则**：表单字段非空则使用表单值，空则使用已保存配置；密钥留空时沿用已保存密钥。这样修改 Endpoint/Bucket 做测试时不必重复输入密钥。
- **服务端执行业务写入**：用服务端 HTTP PUT 访问预签名 URL，不依赖浏览器 CORS；真实用户上传仍需在 OSS 配置 CORS。
- **错误信息脱敏**：只返回 SDK 的异常消息（截断），不拼接 AccessKey/SecretKey；测试接口不加 `@Auditable`，避免审计切面序列化请求体中的密钥。
- **前端提示 OSS 差异**：阿里云 OSS 仅支持虚拟托管风格，`pathStyle` 必须为 false；Endpoint 形如 `https://oss-cn-hangzhou.aliyuncs.com`，Region 为 `cn-hangzhou`。

## Risks / Trade-offs

- [测试对象残留] → 成功路径删除；失败时对象可能残留，`healthcheck/` 前缀可由存储清理任务或人工清理。
- [需要写权限] → 测试会真实写入一个小对象；只读凭证会返回失败，这是预期。
- [OSS 与 S3 兼容差异] → 若签名/接口不兼容，测试会返回错误；后续可增加阿里云 OSS 专用 Provider。
- [密钥随请求体传输] → 仅管理员、仅在 HTTPS 生产环境使用；页面不落库、不记录日志。

## Migration Plan

1. 部署后端与前端：配置页出现「测试连接」。
2. 管理员填写 OSS 配置并测试，不保存；确认通过后再决定是否切换与迁移旧对象。
3. 回滚无需数据迁移。

## Open Questions

无。