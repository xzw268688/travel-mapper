## Context

后端使用 Spring Boot 3.3.5 + HikariCP；Actuator 只暴露 `health,info`。JWT 无状态，`RequestIdentityFilter` 已在每个请求解析身份。Redis 直接用于限流、幂等、锁、验证码、用户状态缓存等，没有 Spring Cache 抽象。对象存储是 S3 兼容实现（MinIO/S3Mock/AWS S3），配置可热更新。Swagger 由 springdoc 暴露在 `/swagger-ui.html` 与 `/v3/api-docs`。参见 `proposal.md` 与 `specs/admin/monitoring/spec.md`。

## Goals / Non-Goals

**Goals**

- 一个仅管理员的监控 API 与后台页面，覆盖在线用户、服务、数据库、缓存、存储与接口文档。
- 不替换数据源、不开放 Actuator、不暴露缓存原始值。
- 运行开销可控：节流登记、SCAN 限量、存储统计按需与缓存。

**Non-Goals**

- 不做 Druid/SQL 统计、任意 key 浏览、MinIO Admin API、后端历史采样、SSE、告警。

## Decisions

### 保留 HikariCP

注入 `HikariDataSource` 读取 `HikariPoolMXBean` 的 active/idle/total/waiting 与配置的最大连接数；获取延迟从 Micrometer（如可用）补充。替代方案 Druid 会改变连接池行为并引入 SQL 拦截，收益与风险不匹配。

### OSHI 采集服务器信息

新增 `oshi-core`（6.10.0），读取操作系统、物理内存、磁盘与 CPU 核数；JVM 指标优先用 `ManagementFactory`，避免额外依赖。OSHI 异常时返回可用字段并标记 `partial=true`，不让监控页整体失败。

### 在线用户使用 Redis 两级结构

- `online:user:{userId}` Hash：role、ip、ua、lastSeen，TTL 5 分钟。
- `online:users` ZSet：member=userId，score=epoch 秒。
- `RequestIdentityFilter` 鉴权成功后调用 `OnlineUserRegistry.touch`；用 `online:touch:{userId}`（TTL 60 秒）节流，避免每请求写。
- 列表查询取 ZSet 最近 5 分钟的成员（顺带清理过期 score），再用 `UserMapper.selectBatchIds` 补充用户名/昵称，在内存中按用户名/角色/IP 过滤与分页；在线人数很小，内存过滤可接受。
- 踢下线：`tokenVersion+1`、`UserStatusCache.evict`、删除在线 key，写审计 `USER_KICK`。

### 缓存分组用白名单，不开放任意 key

分组前缀在代码中白名单定义。计数使用 `SCAN MATCH prefix* COUNT 500` 并设总扫描上限（如 20,000）；TTL 只抽样少量 key。清理仅对安全分组开放，使用 `SCAN` + `UNLINK` 分批删除，返回删除数量。额度、用户状态、锁不提供清理入口。

### 存储监控扩展 Provider 能力

`StorageProvider` 增加 `probe()` 与 `stats(int maxObjects)`：
- `probe`：`headBucket` 并计时；未配置/不可用时返回状态与错误。
- `stats`：`listObjectsV2Paginator` 累加数量与大小，达到上限即停止并标记 `truncated=true`。
结果在服务端缓存 5 分钟；配置热更新后缓存失效。

### 接口文档由前端渲染

后端新增 `GET /api/admin/monitor/api-docs`，从 Spring `RequestMappingHandlerMapping` 生成 OpenAPI 兼容清单（`paths` + 每个方法的 `tags` / `summary`），缓存 60 秒；前端按 tag 分组展示 method/path/summary，另提供 `/swagger-ui.html` 外链。不改变 `SecurityPaths` 的公开策略。之所以不直接注入 springdoc 分组资源：分组配置下资源 bean 的 `apiDocsUrl` 可能为空，且默认文档构建依赖资源初始化时机；从映射生成更稳定。

### 前端轮询与趋势

`AdminMonitorView` 按路由 `section` 切换面板；`setInterval` 15 秒轮询，`document.visibilityState === 'hidden'` 时暂停。服务/数据库/缓存指标在浏览器保留最近 20 个采样点，用自研 SVG `Sparkline` 展示，不落库。

## Risks / Trade-offs

- [Redis 在线登记增加写放大] → 60 秒节流 + 5 分钟 TTL，写量可控。
- [`SCAN` 大 key 空间阻塞] → 总扫描上限、`UNLINK` 分批、只读计数。
- [S3 列举成本与延迟] → 按需触发、上限与 5 分钟缓存，明确标记不完整。
- [踢下线使该用户所有设备失效] → 这是预期行为；操作写审计。
- [OSHI 在部分平台指标缺失] → 返回可用字段，前端不因单字段缺失报错。
- [监控接口泄露运行信息] → 仅 `/api/admin/monitor/**`、ADMIN 校验、不返回敏感配置。

## Migration Plan

1. 增加 OSHI 依赖并部署后端：开始登记在线用户、提供监控 API。
2. 部署前端：新增「监控」菜单与六个页面。
3. 无数据库迁移；回滚时前端隐藏菜单即可，在线登记 key 会自然过期。

## Open Questions

无。