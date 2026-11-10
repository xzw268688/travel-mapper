## 1. 依赖与在线用户

- [x] 1.1 `pom.xml` 增加 OSHI 依赖，验证方式：`mvn -DskipTests compile` 通过且 `SystemInfo` 可实例化
- [x] 1.2 新增 `OnlineUserRegistry`：Redis Hash + ZSet、5 分钟窗口、60 秒节流，验证方式：单元/集成测试覆盖登记与过期
- [x] 1.3 `RequestIdentityFilter` 在鉴权成功后登记在线状态，验证方式：登录后调用接口可在 Redis 看到登记
- [x] 1.4 新增 `GET /api/admin/monitor/online-users`（用户名/角色/IP 筛选、分页）与 `DELETE /online-users/{userId}` 踢下线，验证方式：集成测试断言筛选、踢下线后旧 JWT 返回 `1006`、审计 `USER_KICK`

## 2. 服务与数据库监控

- [x] 2.1 新增 `GET /api/admin/monitor/server`：OSHI 的 CPU/内存/磁盘/OS + JVM 堆/非堆/线程/GC/运行时长，验证方式：集成测试返回关键字段；OSHI 异常时标记 partial
- [x] 2.2 新增 `GET /api/admin/monitor/database`：Hikari 活跃/空闲/总连接、等待线程、最大连接与获取延迟，验证方式：集成测试返回非空池指标

## 3. 缓存与存储监控

- [x] 3.1 新增 `GET /api/admin/monitor/cache`：Redis INFO 概览 + 白名单分组数量/TTL 概况，验证方式：集成测试断言分组与 key 数量
- [x] 3.2 新增 `DELETE /api/admin/monitor/cache/{group}`：仅安全分组可清理，SCAN+UNLINK 分批，写审计 `CACHE_CLEAR`，验证方式：清理后 key 减少、受保护分组返回 `2001`
- [x] 3.3 扩展 `StorageProvider` 的 `probe()` 与 `stats(maxObjects)` 并在两个实现中落地，验证方式：S3Mock 下探测成功、未配置时返回不可用状态
- [x] 3.4 新增 `GET /api/admin/monitor/storage` 与 `/storage/stats`：配置/健康/延迟；统计带上限与 5 分钟缓存，验证方式：集成测试断言 truncated 标记与缓存命中

## 4. 接口文档与权限

- [x] 4.1 新增 `GET /api/admin/monitor/api-docs`：读取 springdoc OpenAPI 并缓存 60 秒，验证方式：集成测试返回包含 paths 的 JSON
- [x] 4.2 所有监控接口仅 ADMIN 可访问，不开放 Actuator metrics，验证方式：普通用户与匿名返回 `4002`

## 5. 前端监控中心

- [x] 5.1 新增 `AdminMonitorView` 与六个面板（在线用户、服务、数据库、缓存、存储、接口文档），支持路由切换；`npm run build` 通过
- [x] 5.2 新增 `Sparkline` 组件，页面可见时 15 秒轮询、隐藏时暂停、手动刷新，验证方式：构建通过且切换页签后定时器停止
- [x] 5.3 在线用户支持筛选与踢下线确认；缓存支持安全分组清理确认；存储支持按需刷新统计
- [x] 5.4 `App.vue` 新增「监控」菜单、路由与 `api/admin.ts` 接口，验证方式：管理员可见并可进入六个页面

## 6. 测试与文档

- [x] 6.1 新增后端监控集成测试：在线登记/筛选/踢下线、服务/数据库字段、缓存分组与清理、存储探测/统计、接口文档、权限
- [x] 6.2 更新 `TravelMapper API  V1.0.md`：新增监控接口、参数与响应；更新 `docs/ops/local-development.md` 的菜单说明
- [x] 6.3 后端全量测试与前端构建通过，验证方式：`mvn test`（或项目等价命令）与 `npm run build` 退出码为 0