Triage: ready-for-agent

## Why

当前退出登录只是前端清除本地 Token：`AuthService.logout()` 是空实现，前端也从未调用 `/api/auth/logout`。JWT 固定 7 天有效，任何被复制或截获的旧 Token 在退出后仍可继续使用。同时生产环境无条件公开 `/swagger-ui.html` 与 `/v3/api-docs`，向公网暴露全部接口清单。两者都是发布前必须修复的安全回归。

## What Changes

- JWT 载荷新增 `jti`（Token 唯一标识），新签发的 Token 均携带。
- 退出登录真正生效：后端将当前 Token 的 `jti` 写入 Redis 黑名单，TTL 取该 Token 的剩余有效期；鉴权过滤器拒绝命中黑名单的 Token。Redis 不可用时降级放行并记录告警日志，不阻断正常请求。
- 前端退出登录改为先调用 `/api/auth/logout`，接口失败也必须清除本地 Token 并跳转登录页。
- 新增「退出所有设备」：将该用户 `token_version` 自增并驱逐状态缓存，使该用户全部已签发 Token 立即失效；个人中心「账号安全」区块提供入口。
- 生产环境禁用 springdoc：`/swagger-ui.html`、`/swagger-ui/**`、`/v3/api-docs/**` 在生产 profile 下直接不可用（404）。后台「监控 → 接口文档」基于 HandlerMapping 生成、需管理员鉴权，保持可用，并移除 Nginx 反代公开 Swagger 的说明。
- 兼容策略：不含 `jti` 的旧 Token 继续按现有逻辑放行，最长 7 天内自然淘汰；从本版本起的 Token 支持逐 Token 吊销。
- 更新 OpenSpec 规格、根目录 API/SRS 文档与部署说明；补充集成测试覆盖登出吊销、退出所有设备与生产文档关闭。

不做：缩短 JWT 有效期、刷新令牌 / 短期 Access Token、设备会话列表与单设备远程下线、修改 Swagger 为管理员登录后在线调试。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `identity/authentication`: JWT 载荷增加 `jti`；退出登录 MUST 使当前 Token 立即失效，新增退出所有设备语义；澄清旧 Token 兼容窗口与 Redis 降级行为。
- `admin/monitoring`: 接口文档入口 MUST 在公开 Swagger 关闭后仍可用；移除「不改变 Swagger 公开访问策略」的约束，改为生产环境不公开 OpenAPI 文档。

## Impact

- 后端：`JwtService`（签发/解析 `jti`）、`RequestIdentityFilter`（黑名单校验）、`AuthService`（登出、退出所有设备）、`AuthController`、`JwtPayload`；新增 Token 黑名单服务；`SecurityPaths` 维持 `/api/auth/logout` 需鉴权；`application-prod.yml` 关闭 springdoc。
- 前端：`stores/auth.ts`（异步登出并调用后端）、`App.vue`、`ProfileView.vue`（账号安全区块与退出所有设备）、`api/auth.ts`、`api/user.ts`。
- API：`POST /api/auth/logout` 从空操作变为真实吊销；新增 `POST /api/auth/logout-all`。
- 文档：`TravelMapper API  V1.0.md`、`TravelMapper SRS V1.0.md`、`docs/ops/nginx-sse.conf`、`docs/ops/deployment.md`。
- 测试：新增登出吊销与退出所有设备集成测试；补充生产 profile 下 OpenAPI 端点不可用的断言。