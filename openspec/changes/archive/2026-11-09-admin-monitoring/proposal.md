Triage: ready-for-agent

## Why

管理端目前只能看到业务数据，缺少运行时可观测能力：不知道谁正在使用系统、看不到服务器与 JVM 资源、无法快速判断数据库连接池、Redis 与对象存储是否健康，也没有在后台查看接口文档的入口。出现登录锁死、缓存异常或存储不可用时，只能登服务器排查。

## What Changes

- 顶栏新增管理员「监控」菜单，包含：在线用户、服务监控、数据库监控、缓存监控、存储监控、接口文档。
- **在线用户**：鉴权成功后节流写入 Redis 活跃登记（用户 ID、角色、IP、UA、最近活跃）；5 分钟内有请求视为在线。支持按用户名、角色、IP 筛选与强制下线（`tokenVersion+1`）。
- **服务监控**：基于 Actuator/Micrometer + OSHI 展示系统 CPU、物理内存、磁盘、操作系统信息，以及 JVM 堆/非堆、线程、GC 与运行时长。
- **数据库监控**：保留 HikariCP，展示活跃/空闲/总连接、等待线程、获取延迟与最大连接数；不引入 Druid、不拦截 SQL。
- **缓存监控**：Redis INFO 概览（内存、客户端、命中率、keyspace）与已知业务缓存分组（验证码、登录失败、限流、幂等、搜索、AI 取消等）的数量与 TTL；仅允许清理安全分组并写审计。
- **存储监控**：展示当前对象存储配置、连通状态与探测延迟；按需统计对象数与总大小（设扫描上限与缓存，超出提示不完整）。兼容 MinIO、S3Mock 与 AWS S3。
- **接口文档**：前端读取 OpenAPI 文档，按分组展示方法、路径与摘要，支持搜索，并提供打开 Swagger UI 调试的链接；不改变现有公开访问策略。
- 监控接口统一为 `/api/admin/monitor/**`，仅 `ADMIN` 可访问；不开放 Actuator metrics。
- 页面可见时轮询（15–30 秒），监控只返回当前快照，趋势图由前端保留最近采样点。
- 新增 OSHI 依赖；监控写操作（踢下线、清理缓存）写审计日志。

非目标：Druid / SQL 统计、任意 Redis key 浏览与原始 value、MinIO Admin API 容量指标、后端历史采样、SSE 实时推送、Actuator 端点对外开放。

## Capabilities

### New Capabilities

- `admin/monitoring`: 管理员查看在线用户、服务/JVM、数据库连接池、Redis 缓存、对象存储与接口文档，并执行踢下线与安全缓存清理的能力。

### Modified Capabilities

- `ui/branding`: 顶栏新增「监控」管理员菜单；「管理」菜单包含通知公告。

## Impact

- 后端：新增 `AdminMonitorController`、`AdminMonitorService`、`OnlineUserRegistry`；`RequestIdentityFilter` 接入活跃登记；`StorageProvider` 增加探测与统计能力；`pom.xml` 增加 OSHI。
- 前端：新增 `AdminMonitorView` 与监控面板组件、`Sparkline` 组件；`App.vue` 菜单、路由、`api/admin.ts`。
- 数据库：无新表（在线用户仅 Redis）。
- 文档：API 与本地开发文档补充监控接口。
- 测试：在线登记/踢下线、监控接口权限、缓存分组、存储探测与接口文档的集成测试。