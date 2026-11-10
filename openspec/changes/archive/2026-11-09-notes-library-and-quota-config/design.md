## Context

见 `proposal.md`。已核实的现状：`travel_note` 有 `content_text` 摘要列与 `idx_user_created` 索引；MyBatis-Plus 已配置 `PaginationInnerInterceptor`；签到式配置由 `ConfigService` 统一读写并支持热加载；`Get /notes/{id}` 已有按 600 秒签名图片 URL 的成熟实现。

## Goals / Non-Goals

**Goals**
- 游记列表与搜索一次查询完成，不额外下载正文 HTML。
- 额度修改即时生效且可审计，复用现有配置机制。
- 记账只移除无效 UI，不影响旧客户端的接口兼容。

**Non-Goals**
- 不做全文检索（不引入 ES）；关键字仅 `LIKE` 标题与 `content_text`。
- 不改变游记创建、分享、下架与地图点亮规则。
- 不在本次变更内做媒体迁移工具；已丢失文件需重新上传。

## Decisions

### D1：列表查询用 MyBatis-Plus 分页 + `LIKE`
- `LambdaQueryWrapper` 对 `user_id` 过滤，`(title LIKE ? OR content_text LIKE ?)`，`created_at` 区间用 `>= from 00:00` 与 `< to+1day 00:00`，按 `created_at DESC, id DESC` 排序。
- 直接复用分页插件与 `PageResult`，不新增自定义 SQL。
- 摘要取 `content_text` 前 80 字，封面用 600 秒签名 URL；签名失败返回 `null`，前端降级为占位。

### D2：额度配置独立端点
- 不复用 `LlmConfigDto`，避免「额度」与「模型」耦合；新增 `QuotaConfigDto { freeLimit }` 与 `/admin/config/quota`。
- 复用 `ConfigService.updateGroup` + `ConfigReloadService` 与 `@Auditable`，与现有配置一致。
- 校验 0–100；服务端已有 `QUOTA_FREE_LIMIT` 键，直接读写。

### D3：游记页结构
- 「最近游记」卡片在发布表单之前，列表即入口；搜索条件变化需点「查询」，避免每输入一个字就请求。
- 删除操作使用二次确认，成功后刷新当前页。
- 缩略图 `@error` 时记录失败 ID 并显示「无封面」，兼容已丢失的历史媒体。

### D4：记账筛选
- 仅移除前端展示与请求参数；后端保留 `ungrouped` 以兼容旧客户端，避免契约破坏。

### D5：本地媒体持久化
- S3Mock 无数据卷，改为 SeaweedFS（端口 19091，私有桶，带命名卷）。
- 已验证：预签名上传 200、签名下载 200、重启容器后仍 200、CORS 回显来源域名。
- 已丢失对象无法恢复，文档与页面需说明。

## Risks / Trade-offs

- [LIKE 查询在数据量大时变慢] → 已有 `idx_user_created`；关键字查询限定本人数据，V1 规模可接受，后续可升级全文索引。
- [列表为每页生成签名 URL 增加延迟] → 仅对当前页（默认 10 条）签名，且签名是本地计算。
- [旧客户端仍可传 `ungrouped`] → 保留参数，行为与之前一致，不影响兼容。
- [SeaweedFS 私有桶与海报截图] → 已确认签名 GET 返回 CORS 头；图片与海报链路可用。

## Migration Plan

1. 后端发布：新增接口无破坏性变更。
2. 前端发布：游记页与后台配置页更新。
3. 运维：开发环境存储端点切到 SeaweedFS；已有媒体若丢失需重新上传。
4. 回滚：接口与 UI 可独立回退；配置键向后兼容。

## Open Questions

无。