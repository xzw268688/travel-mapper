## Why

本地开发环境中 MySQL 容器默认使用 UTC，而后端 JVM 使用 +08，导致同一张表里 DB 默认时间戳与应用写入时间相差 8 小时；按天过滤、`DATE(created_at)`、`CURDATE()` 的边界也随之偏移。生产环境只是因为 MySQL、JVM、JDBC 三处恰好都配成 +08 才没有暴露问题，代码本身仍依赖这种偶然一致。另有约 20 处 `now()` 未指定时区、前端 17 处各自实现的日期切片，以及 `PlanView` 在北京时间 0–8 点会算错“明天”。这些问题在发布前必须收敛成一条明确、可测试的时间约定。

## What Changes

- 确立全局时间口径：展示与业务日期统一按 **Asia/Shanghai（北京时间）**；存储继续使用**北京时间墙钟 + 无时区 `DATETIME`**，业务日期继续使用 `DATE`，不迁移历史数据。
- 后端引入固定 `Asia/Shanghai` 的 Clock/TimeProvider，作为唯一“当前时间”来源，替换所有未指定时区的 `LocalDate.now()` / `LocalDateTime.now()`；测试可注入固定时钟。
- 业务日期切分不再依赖数据库会话时区：SQL 中用于切日的 `NOW()` / `CURDATE()` / `DATE(created_at)` 改为由 Java 计算边界后传参；手工 SQL 的 `getTimestamp()` 读取改为 `LocalDateTime` 读取。
- 统一本地、测试、生产的数据库会话时区与 JVM 时区；JDBC 强制会话时区作为兜底，避免容器默认 UTC 时再次混用时钟。
- API 契约保持 `yyyy-MM-dd'T'HH:mm:ss`（无时区）与 `yyyy-MM-dd`，明确语义为北京时间；不标记为 BREAKING。
- 前端新增统一的 `utils/datetime.ts`（`formatDate` / `formatDateTime` / `todayInBeijing` / `addDays`），替换 17 处重复切片；修复 `PlanView` 默认“明天”与 `addDays` 的跨日问题；后台日期时间输入统一标注“北京时间”。
- 增加时区回归测试：固定 Clock 的边界测试 + 在 JVM/DB 均为 UTC 的环境跑集成测试，证明行为不依赖环境默认时区。

## Capabilities

### New Capabilities

- `platform/timekeeping`: 定义全系统的时间存储、传输、展示、按天切分与每日重置口径，确保所有能力共享同一套北京时间语义。

### Modified Capabilities

- `feedback/submission`: 每日 5 条反馈的限流窗口由“服务器本地时区自然日”改为“北京时间自然日”，与额度、验证码等其他日限流口径一致。

## Impact

- 后端：新增时间提供者与配置；修改约 20 个 service/工具类中的 `now()` 调用；修改 `TripService`、`MapService`、`RetentionCleanupService` 等处的日期切分 SQL；修改手工 JDBC 读取；更新 `application.yml`、`.env.example`、`docker-compose.yml`、测试基础设施与部署文档。
- 前端：新增 `src/utils/datetime.ts` 与对应单测；修改 17 个页面的格式化与日期计算；后台日期选择器增加“北京时间”说明。
- 数据库：无 schema 变更、无数据迁移；生产已有数据按北京时间墙钟解释，保持不变。
- API：字段格式与路径不变；仅修正返回值的正确性。
- 测试：新增时区边界单元/集成测试，并增加 UTC 环境的集成测试矩阵（依赖上一个 change 建立的 CI 基座）。
- 运维：本地与测试环境的 MySQL 时区配置需要调整；开发库中已有的混时区旧数据不修复，验收使用新数据。