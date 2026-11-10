## Context

见 `proposal.md`。当前实现约束：

- 所有时间列都是无时区 `DATETIME` 或 `DATE`；MyBatis-Plus 默认 TypeHandler 按字面值读写，不做换算。
- 时间写入存在两套时钟：数据库 `CURRENT_TIMESTAMP` / `NOW()` 与应用 `LocalDateTime.now()`。
- 本地/测试 MySQL 容器默认 UTC，生产 MySQL、JVM、JDBC 均为 +08；`serverTimezone=Asia/Shanghai` 只影响 `java.sql.Timestamp` 转换。
- 约 20 处 `LocalDateTime.now()` / `LocalDate.now()` 与多处 `ZoneId.systemDefault()` 未固定时区。
- 运行时 SQL 中仍有 `NOW()`、`CURDATE()`、`DATE(created_at)`，其中 `NOW()`/`CURDATE()` 依赖数据库会话时区；`DATE(created_at)` 依赖存储值本身。
- 前端无日期库，17 处各自用字符串切片；`PlanView` 用 `toISOString()` 计算默认日期。
- 已有 268 个后端测试、无前端单测；CI 基座由前置 change 提供。

## Goals / Non-Goals

**Goals:**

- 任意 JVM/DB 默认时区下，读写、按天切分、每日重置与展示都得到同一北京时间结果。
- 让“当前时间”成为可注入、可测试的单一来源。
- 用测试锁定北京时间 00:00–08:00 的边界行为。

**Non-Goals:**

- 不改为 UTC 存储，不做历史数据迁移（生产数据本来就是北京时间墙钟）。
- 不改变 API 字段格式、路径或数据库 schema。
- 不处理非中国时区用户的个性化时区显示（产品口径固定北京时间）。
- 不重写历史 Flyway 迁移（`V15__trip_status_auto.sql` 为一次性迁移，保留原样）。

## Decisions

### 1. 固定 Clock + TimeProvider 作为唯一时间来源

- 新增 `common/time/TimeConfig`：注册 `Clock.system(ZoneId.of("Asia/Shanghai"))`；新增 `TimeProvider`（`now()`、`today()`、`zone()`）注入到 service。
- 所有 `LocalDate.now()` / `LocalDateTime.now()` / `ZoneId.systemDefault()` 替换为 `timeProvider`；静态工具（如 `ObjectKeyGenerator`）改为实例 Bean 或显式接收 `LocalDate` 参数。
- 单元测试直接构造 `TimeProvider(Clock.fixed(...))`；需要固定时间的集成测试使用 `@TestConfiguration` 提供 `@Primary Clock`。
- 替代方案：只抽 `ZoneId BEIJING` 常量。实现更轻，但无法在测试中把“现在”固定在跨日边界，因此不选。

### 2. 业务写入不依赖 DB 默认时钟

- 新增 MyBatis-Plus `MetaObjectHandler`：插入时填充 `createdAt`/`updatedAt`（字段存在且为 null 时），更新时填充 `updatedAt`，值来自 `TimeProvider`。
- 使用 `JdbcTemplate` 或手写 SQL 的写入显式传时间参数；所有运行时 `NOW()` 调用移除。
- 保留 schema 的 `DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE CURRENT_TIMESTAMP` 作为兜底，但业务逻辑不再依赖；配合 JDBC 会话时区，兜底值同样是北京时间。
- 替代方案：删除默认值并迁移 schema。改动大、收益低，且对遗漏的 SQL 更危险，因此不选。

### 3. SQL 按天切分改为参数化

- `CURDATE()`：在 Java 用 `timeProvider.today()` 计算后作为参数传入（如 `MapService.stats` 的未过期判断）。
- `NOW()` / `DATE_ADD(NOW(), ...)`：统一改为传入 `timeProvider.now()` 与其计算结果（保留期清理、额度过期、卡密核销、清单更新时间、会话吊销）。
- `DATE(created_at)` 用于取日期的地方改为选择 `MIN/MAX(created_at)` 为 `LocalDateTime`，在 Java 转 `LocalDate`；`ORDER BY` 中改为直接比较 `created_at`。
- `YYYY-MM-DD` 过滤继续用 `from.atStartOfDay()` / `to.plusDays(1).atStartOfDay()` 生成边界；因为存储是北京时间字面值，比较正确且不依赖会话时区。
- 不修改历史迁移 `V15__trip_status_auto.sql`。

### 4. JDBC 会话时区作为兜底

- 统一 JDBC URL 为 `connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true`（Connector/J 8.0.23+），替换现有 `serverTimezone=Asia/Shanghai`。
- 本地 `docker-compose.yml` 的 MySQL 增加 `TZ=Asia/Shanghai` 与 `--default-time-zone=+08:00`；`docs/ops/local-development.md` 的外部容器命令同步。
- `TestInfrastructure` 的 MySQL 容器默认 `--default-time-zone=+08:00`，并允许通过系统属性覆盖为 UTC 以运行回归矩阵。
- 生产部署示例同步更新；JVM 继续显式 `-Duser.timezone=Asia/Shanghai`。
- 替代方案：只改 compose 不改代码。能修当前现象，但换环境或遗漏 `NOW()` 时仍会复发，因此不选。

### 5. 手工 JDBC 读取统一为 LocalDateTime

- 将 `rs.getTimestamp(...).toLocalDateTime()` 改为 `rs.getObject(column, LocalDateTime.class)`，避免依赖 JVM 默认时区与 `java.sql.Timestamp` 转换。
- `DATE` 列继续用 `rs.getDate(...)` / `LocalDate`；`queryForMap` 的日期列按 `LocalDateTime` 或 `LocalDate` 读取后显式转换。
- 替代方案：保留 `getTimestamp` 并依赖 JVM 固定 +08。少改十几处，但无法通过 UTC 环境矩阵，因此不选。

### 6. 前端统一日期工具

- 新增 `src/utils/datetime.ts`：
  - `formatDate`（`YYYY-MM-DD`）、`formatDateTime`（`YYYY-MM-DD HH:mm`）、`formatDateTimeSeconds`（含秒），无值时返回 `''`，沿用现有展示效果；
  - `todayInBeijing()` 用 `Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', ... })`，不引入日期库；
  - `addDays(dateString, n)` 用 `Date.UTC` 做日期运算，避免本地时区/DST；
  - `currentBeijingMonth()` / `monthRange(month)` 供记账页复用。
- 替换 17 处重复切片；`PlanView` 的默认开始日期改用 `todayInBeijing()` + `addDays`。
- 后台 `datetime` 选择器旁增加“北京时间”说明；提交值保持 `YYYY-MM-DDTHH:mm:ss`，不做时区换算。
- 替代方案：引入 dayjs + timezone 插件。能提供更多 API，但为当前需求增加依赖与包体积，因此不选。

### 7. 测试策略

- 单元测试：`TimeProvider` 固定时钟下的“北京 00:30 = 新的一天”；日期过滤边界；反馈/举报/邮箱/额度的日重置；`ObjectKeyGenerator` 年月；前端 `todayInBeijing`/`addDays`/格式化。
- 集成测试：新增 `TimezoneIntegrationTest`，在应用内注入固定 Clock，验证按天过滤、游记归组、行程过期、分享过期、清理任务在边界时刻的行为。
- UTC 矩阵：CI 以 `TZ=UTC` + `-Duser.timezone=UTC` + MySQL `--default-time-zone=+00:00`（测试属性覆盖）运行集成测试，断言结果仍为北京时间。前置 change 的 CI 基座提供 job 入口。
- 现有 `FeedbackIntegrationTest` 的日期过滤测试改为插入北京边界前/后的时间，确保能捕获 8 小时偏移。

## Risks / Trade-offs

- [MetaObjectHandler 覆盖调用方显式设置的时间] → 仅当字段为 null 时填充；新增测试覆盖“显式值不被覆盖”。
- [手工 SQL 或 `JdbcTemplate` 更新遗漏 `updated_at`] → 审计所有 `UPDATE ... SET`，补参数或 `updated_at = ?`；集成测试校验更新时间。
- [移除 `NOW()` 后清理任务的边界语义变化] → 用 Clock 计算同一语义的截止时间（`now.minusDays(n)`），测试覆盖恰好边界。
- [UTC 矩阵暴露历史假设，测试数量增加] → 新增独立 job，不阻塞 PR 其他 job 的诊断；先跑核心时区测试，再扩展到全量集成。
- [开发库旧数据仍偏 8 小时] → 不迁移；验收使用新数据，文档说明可重置开发库。
- [前端 `Intl` 在旧浏览器行为差异] → 目标浏览器已支持 `Intl.DateTimeFormat` 与时区；单测在 happy-dom/Node 下运行，E2E 覆盖真实浏览器。

## Migration Plan

1. 先合入前置的 CI 基座，确保新增测试能自动运行。
2. 同步更新本地/测试/生产的时区配置（MySQL、JDBC、JVM），此步不改变存储语义。
3. 合入代码：TimeProvider、MetaObjectHandler、SQL 参数化、手工读取改造、前端日期工具。
4. 本地开发库可选择重置；生产无需数据迁移，回滚只需回退代码与配置。
5. 运行 UTC 矩阵与 E2E，确认北京时间展示、按天过滤、每日重置均正确。

## Open Questions

无。