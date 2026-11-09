## Purpose

为管理员提供运行时可观测与在线处置能力：查看在线用户、服务器与 JVM、数据库连接池、Redis 缓存、对象存储健康状态，并在后台浏览接口文档。

## ADDED Requirements

### Requirement: Online user presence

系统 MUST 在鉴权成功后的请求中登记在线状态，包含用户 ID、角色、IP、User-Agent 与最近活跃时间。最近 5 分钟内有请求的用户 MUST 视为在线；登记 MUST 做节流，避免每次请求都写 Redis。管理员 MUST 能分页查看在线用户，并按用户名、角色与 IP 筛选。

#### Scenario: Authenticated request marks user online
- **WHEN** 已登录用户发起任意需要鉴权的请求
- **THEN** 系统在 Redis 中更新该用户的最近活跃时间与客户端信息

#### Scenario: Inactive user disappears
- **WHEN** 用户超过 5 分钟没有任何请求
- **THEN** 该用户不再出现在在线用户列表

#### Scenario: Filter online users
- **WHEN** 管理员按角色 `ADMIN` 或用户名关键字筛选
- **THEN** 只返回匹配的在线用户

### Requirement: Force logout

管理员 MUST 能对在线用户执行强制下线。强制下线 MUST 使该用户已签发的 JWT 立即失效（`tokenVersion+1` 并清除用户状态缓存），并 MUST 写入审计日志 `USER_KICK`。

#### Scenario: Kicked user is rejected on next request
- **WHEN** 管理员强制下线某在线用户
- **THEN** 该用户下一次请求返回 `1006`，并从在线列表移除

#### Scenario: Kick is audited
- **WHEN** 管理员执行强制下线
- **THEN** 审计日志新增 `USER_KICK`，记录目标用户与操作人

### Requirement: Server and JVM monitoring

管理员 MUST 能查看服务器与 JVM 当前快照：系统/进程 CPU、物理内存、磁盘、操作系统信息、JVM 堆与非堆内存、线程数、GC 与运行时长。数据 MUST 通过应用内受控接口返回，MUST NOT 直接开放 Actuator metrics。

#### Scenario: Admin opens server monitor
- **WHEN** 管理员打开服务监控页
- **THEN** 页面展示 CPU、内存、磁盘、操作系统与 JVM 指标

#### Scenario: Metrics are admin-only
- **WHEN** 普通用户或匿名请求访问服务监控接口
- **THEN** 系统返回 `4002`

### Requirement: Database pool monitoring

管理员 MUST 能查看 HikariCP 连接池的活跃连接、空闲连接、总连接、等待线程、最大连接数与获取延迟。系统 MUST NOT 为此替换数据源或启用 SQL 拦截。

#### Scenario: Pool statistics are returned
- **WHEN** 管理员打开数据库监控页
- **THEN** 页面展示当前连接池统计与获取延迟

### Requirement: Cache monitoring and safe clearing

管理员 MUST 能查看 Redis 概览（内存、客户端数、命中率、keyspace）与已知业务缓存分组的 key 数量与 TTL 概况。系统 MUST NOT 提供任意 key 浏览或原始 value 查看。MUST 仅允许清理安全分组（验证码、登录失败、限流、幂等、搜索、AI 取消）；额度、用户状态与锁 MUST NOT 提供清理。清理 MUST 二次确认并写入审计 `CACHE_CLEAR`。

#### Scenario: Cache groups are listed
- **WHEN** 管理员打开缓存监控页
- **THEN** 页面展示 Redis 概览与各已知分组的数量、TTL 概况

#### Scenario: Safe group is cleared
- **WHEN** 管理员确认清理「登录失败」分组
- **THEN** 对应 key 被删除，并新增一条带分组与数量的 `CACHE_CLEAR` 审计

#### Scenario: Protected group cannot be cleared
- **WHEN** 管理员尝试清理额度或用户状态分组
- **THEN** 系统拒绝该操作并返回 `2001`

### Requirement: Storage monitoring

管理员 MUST 能查看当前 S3 兼容对象存储的配置来源、Endpoint、Bucket、连通状态与探测延迟。MUST 支持按需统计对象数与总大小，并 MUST 设置扫描上限与结果缓存；超出上限时 MUST 明确提示统计不完整。

#### Scenario: Storage health probe
- **WHEN** 管理员打开存储监控页
- **THEN** 系统探测 Bucket 可访问性并返回状态与延迟

#### Scenario: On-demand object statistics
- **WHEN** 管理员点击「刷新统计」
- **THEN** 系统在扫描上限内返回对象数与总大小，超过上限时标记结果不完整

### Requirement: API documentation view

管理员 MUST 能在后台查看接口文档：按分组展示 HTTP 方法、路径与摘要，并支持搜索。页面 MUST 提供打开 Swagger UI 的入口。该能力 MUST NOT 改变现有 Swagger/OpenAPI 的公开访问策略。

#### Scenario: Admin browses API list
- **WHEN** 管理员打开接口文档页
- **THEN** 页面按分组展示接口的方法、路径与摘要

#### Scenario: Search API paths
- **WHEN** 管理员输入路径关键字
- **THEN** 列表只保留匹配的接口

### Requirement: Monitoring access control and refresh

所有监控接口 MUST 仅对 `ADMIN` 开放。监控页 MUST 在可见时按 15–30 秒轮询、在页面不可见时暂停，并 MUST 提供手动刷新。监控读取 MUST NOT 写审计；只有强制下线与缓存清理 MUST 写审计。

#### Scenario: Normal user is denied
- **WHEN** 普通用户调用任意监控接口
- **THEN** 系统返回 `4002`

#### Scenario: Visible page polls
- **WHEN** 管理员停留在服务监控页且页面可见
- **THEN** 页面按轮询间隔刷新指标，切换到其他页签后暂停