## Context

当前鉴权是无状态 JWT：`JwtService` 只签发 `sub`/`role`/`tv`/`exp`，`RequestIdentityFilter` 校验签名、用户状态与 `token_version`，`AuthService.logout()` 为空方法，前端从未调用 `/api/auth/logout`。`token_version` 只在封禁、注销、重置密码时自增，无法表达「只吊销当前这一个 Token」。Redis 已是项目强依赖（限流、状态缓存、在线登记），适合承载短期吊销名单。

公开文档方面，`SecurityPaths.isPublic` 无条件放行 `/v3/api-docs` 与 `/swagger-ui`，`application-prod.yml` 未关闭 springdoc；后台「监控 → 接口文档」已通过 `AdminApiDocsService` 从 HandlerMapping 生成清单，不依赖 springdoc。

## Goals / Non-Goals

**Goals：**
- 退出登录后，当前 Token 立即不可用；其他设备不受影响。
- 提供账号级「退出所有设备」。
- 生产环境不公开 Swagger UI / OpenAPI 文档，且不破坏后台接口文档页。
- 保持升级平滑：旧 Token 在有效期内继续可用。

**Non-Goals：**
- 不做刷新令牌、短期 Access Token 或会话轮换。
- 不做登录设备列表、单设备远程下线。
- 不改变 JWT 有效期（7 天），不改变 `token_version` 在封禁/注销/改密场景的既有语义。
- 不为 Swagger 增加管理员鉴权或在线调试能力。

## Decisions

### 1. 逐 Token 吊销：`jti` + Redis 黑名单

JWT 增加 `jti`（UUID），签发时写入。登出时按 `jti` 写入 Redis key `auth:jti:<jti>`，TTL 取 `exp - now`；过滤器在完成签名、用户状态与 `token_version` 校验后检查黑名单，命中返回 `1006`。

- 为什么不复用 `token_version`：`token_version +1` 会让该用户所有设备同时下线，与「只退出当前设备」的用户预期冲突；`token_version` 继续保留给封禁、注销、改密、退出所有设备。
- 为什么用 Redis 而不是数据库表：吊销记录天然短期，TTL 自动清理，无需迁移与定时任务。
- 黑名单查询失败（Redis 不可用）时降级放行并记录 `WARN`，不阻断全部已登录请求；残余风险是已吊销 Token 在 Redis 恢复或过期前仍可用，由告警覆盖。

### 2. 旧 Token 兼容

不含 `jti` 的 Token 视为旧版，继续通过现有校验，最长 7 天后自然淘汰。用旧 Token 调用登出只能清除客户端，无法单独吊销；这是升级窗口的已知限制，不引入「全量踢下线」的迁移动作。

### 3. 退出所有设备

新增 `POST /api/auth/logout-all`：`token_version +1`、驱逐 `UserStatusCache`、清理在线登记，并写审计日志。前端在个人中心「账号安全」区块触发，二次确认后清本地并回登录页。该接口本身用当前 Token 鉴权，执行后当前 Token 同样失效。

### 4. 前端登出接线

`stores/auth.ts` 的 `logout()` 改为异步：先调用 `/api/auth/logout`，`finally` 中清除本地 Token 与用户状态；`App.vue` 等待完成后再跳转。接口失败不阻止本地退出，避免网络问题导致用户被困在已登录界面。

### 5. 生产关闭公开文档

`application-prod.yml` 设置 `springdoc.api-docs.enabled=false`、`springdoc.swagger-ui.enabled=false`，端点无处理器时自然返回 404。`SecurityPaths` 的白名单不再承担「公开文档」语义，但保留放行规则不影响 404 结果。

后台接口清单保持由 `AdminApiDocsService` 生成；`AdminApiDocsService` 在返回的 OpenAPI 根节点附加 `x-swagger-ui-enabled` 标志（读取 springdoc 配置），`ApiDocsPanel.vue` 仅在标志为真时展示 Swagger UI 链接。这样不依赖前端构建模式，也不会在演示环境误隐藏。

### 6. 错误码与响应

吊销后的 Token 继续使用现有 `1006 JWT_INVALID`（401），不新增错误码；登出与退出所有设备成功返回标准 `ApiResponse.ok()`。

## Risks / Trade-offs

- [Redis 不可用导致吊销失效] → 降级放行 + `WARN` 日志，沿用项目现有缓存降级策略；JWT 最长 7 天自然过期，风险窗口有上界。
- [黑名单 key 随登出增长] → 每个 key 的 TTL 等于对应 Token 剩余寿命，自动清理，无需额外任务。
- [旧 Token 无法单独吊销] → 仅存在于升级后的 7 天窗口；新 Token 全部可吊销。
- [生产关闭 Swagger 后管理员想在线调试] → 后台接口清单仍可按方法/路径/摘要检索；需要调试时在本地环境完成。
- [前端登出接口失败] → 本地仍清除 Token；服务端 Token 会在过期前保持有效，用户可再登录后使用「退出所有设备」兜底。

## Migration Plan

1. 先部署后端：新签发 Token 携带 `jti`，旧 Token 继续可用；生产配置生效后公开文档立即不可用。
2. 再部署前端：登出开始调用后端接口，接口文档页按标志隐藏 Swagger 入口。
3. 回滚：回滚代码即可；Redis 黑名单 key 会自动过期，无需数据清理。回滚后新签发 Token 的 `jti` 被忽略，不影响鉴权。

无数据库迁移。