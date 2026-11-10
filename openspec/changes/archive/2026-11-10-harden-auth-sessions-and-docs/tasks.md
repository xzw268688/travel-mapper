## 1. JWT 与吊销基础设施

- [x] 1.1 `JwtPayload` 增加 `jti` 字段，`JwtService.issue` 每次生成 UUID 并在载荷写入 `jti`，`parse` 读取该字段；扩展 `JwtServiceTest` 断言同一用户两次签发的 `jti` 不同且可解析，运行 `mvn -q -Dtest=JwtServiceTest test` 通过。
- [x] 1.2 新增 Redis Token 黑名单服务：`revoke(jti, ttlSeconds)` 写入 `auth:jti:<jti>` 并设置 TTL，`isRevoked(jti)` 查询；Redis 读写异常时记录 WARN 并返回未命中（降级放行）。补充单元测试覆盖写入 TTL、命中与异常降级，运行对应测试通过。
- [x] 1.3 `RequestIdentityFilter` 在签名、用户状态与 `token_version` 校验之后检查黑名单，命中时返回 `1006`；在 `AuthIntegrationTest` 中断言登出后携带旧 Token 的请求返回 401 且业务码为 `1006`。

## 2. 登出与退出所有设备

- [x] 2.1 `AuthService.logout(userId, token)` 解析当前 Token 的 `jti` 并写入黑名单；无 `jti` 的旧 Token 只记录 debug 并正常返回。`AuthController` 从 `Authorization` 头取 Token 传入。运行 `AuthIntegrationTest` 断言当前设备 Token 失效、另一设备 Token 仍可用。
- [x] 2.2 新增 `AuthService.logoutAll(userId)`：`token_version + 1`、驱逐 `UserStatusCache`、清理 `OnlineUserRegistry`、写审计日志；新增 `POST /api/auth/logout-all`。集成测试断言调用后该用户所有旧 Token 返回 `1006`。
- [x] 2.3 补充兼容性测试：构造不含 `jti` 的旧格式 Token，断言仍可通过鉴权访问业务接口，且登出接口不报错。

## 3. 生产环境关闭公开文档

- [x] 3.1 在 `application-prod.yml` 设置 `springdoc.api-docs.enabled=false` 与 `springdoc.swagger-ui.enabled=false`；新增使用 `prod` profile 与测试密钥的集成测试，断言 `GET /swagger-ui.html` 与 `GET /v3/api-docs` 返回 404。
- [x] 3.2 `AdminApiDocsService` 在生成的 OpenAPI 根节点附加 `x-swagger-ui-enabled` 标志（读取 springdoc 配置）；扩展 `MonitoringIntegrationTest` 断言管理员接口文档响应包含该字段且清单仍可用。
- [x] 3.3 `ApiDocsPanel.vue` 仅在 `x-swagger-ui-enabled` 为真时展示「打开 Swagger UI」链接；运行 `npm run build` 通过。

## 4. 前端登出接线与账号安全入口

- [x] 4.1 `api/auth.ts` 新增 `logout()` 与 `logoutAll()`；`stores/auth.ts` 的 `logout()` 改为异步，先调用后端、在 `finally` 中清除本地 Token 与用户状态；`App.vue` 等待登出完成再跳转。运行 `npm run build` 通过。
- [x] 4.2 `ProfileView.vue` 新增「账号安全」区块与「退出所有设备」按钮（二次确认），成功后清除本地登录态并回到登录页；手动验证按钮文案、确认框与跳转行为。

## 5. 文档同步与整体验收

- [x] 5.1 更新 `TravelMapper API  V1.0.md`：登录响应说明 `jti`、`POST /api/auth/logout` 的吊销语义、新增 `POST /api/auth/logout-all`、生产环境文档端点不可用。
- [x] 5.2 更新 `TravelMapper SRS V1.0.md`：会话失效需求补充逐 Token 吊销、退出所有设备、旧 Token 兼容窗口与 Redis 降级策略。
- [x] 5.3 更新 `docs/ops/nginx-sse.conf` 与 `docs/ops/deployment.md`，删除公开 Swagger/OpenAPI 的反代说明，注明生产默认关闭、调试在本地进行。
- [x] 5.4 运行后端相关测试（至少 `JwtServiceTest`、`AuthIntegrationTest`、`MonitoringIntegrationTest`）、前端 `npm run build`，并执行 `openspec validate harden-auth-sessions-and-docs --strict`，全部通过。