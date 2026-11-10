## 1. 时间基础设施

- [x] 1.1 新增 `common/time/TimeConfig` 与 `TimeProvider`，注册固定 `Asia/Shanghai` 的 `Clock`，提供 `now()`/`today()`/`zone()`；验证固定时钟单测通过
- [x] 1.2 新增 MyBatis-Plus `MetaObjectHandler`，插入时填充 `createdAt`/`updatedAt`、更新时填充 `updatedAt`（仅字段为 null 时）；验证“显式设置的时间不被覆盖”测试通过
- [x] 1.3 为 `TimeProvider` 与 `MetaObjectHandler` 补边界单测（北京 00:30、显式值、无字段实体）；验证 `mvn test` 通过

## 2. 后端时间调用替换

- [x] 2.1 替换 `service/**` 中所有未指定时区的 `LocalDate.now()` / `LocalDateTime.now()`（Trip、Map、Note、Auth、Feedback、Report、Card、Quota、Expense、Checklist、Monitor 等）为注入的 `TimeProvider`；验证 `grep` 无残留且 `mvn test` 通过
- [x] 2.2 替换 `ZoneId.systemDefault()`（`AdminMonitorService`）为 `TimeProvider.zone()`；验证监控接口返回北京时间测试通过
- [x] 2.3 `ObjectKeyGenerator` 改为实例 Bean 或显式接收日期参数，确保存储路径年月按北京时间；验证跨月边界测试通过
- [x] 2.4 移除运行时 SQL 中的 `NOW()` / `CURDATE()` / `DATE_ADD(NOW())`（`QuotaService`、`QuotaPackMapper`、`CardKeyMapper`、`TripChecklistMapper`、`RetentionCleanupService`、`AccountDeletionService`、`MapService`），改传 `TimeProvider` 计算的参数；验证相关集成测试通过
- [x] 2.5 将 `DATE(created_at)` 切日改为选取 `MIN/MAX(created_at)` 后在 Java 转日期（`TripService` 的 timeline/list/snapshot/未命名旅行、`MapService` 国家抽屉），`ORDER BY` 改用 `created_at`；验证旅行时间线、归组、状态推导测试通过
- [x] 2.6 手工 JDBC 读取由 `getTimestamp().toLocalDateTime()` 改为 `getObject(..., LocalDateTime.class)`（Admin 日志、Map、CardKey、Note、Announcement 等）；验证对应集成测试通过
- [x] 2.7 检查所有手写 `UPDATE`，确保 `updated_at` 显式赋值或由 `MetaObjectHandler` 覆盖；验证更新时间断言测试通过

## 3. 环境与连接配置

- [x] 3.1 本地 `docker-compose.yml` 的 MySQL 增加 `TZ=Asia/Shanghai` 与 `--default-time-zone=+08:00`；验证容器内 `SELECT @@session.time_zone` 为 +08:00
- [x] 3.2 更新 JDBC URL（`application.yml`、`.env.example`、`TestInfrastructure`、部署示例）为 `connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true`；验证应用启动后会话时区为 +08:00
- [x] 3.3 `TestInfrastructure` 的 MySQL 容器默认 +08:00，并支持系统属性覆盖为 UTC；验证两种配置都能启动测试
- [x] 3.4 更新 `docs/ops/local-development.md` 的外部容器命令、JVM 参数与测试说明；验证按文档命令启动后时区一致

## 4. 前端日期收口

- [x] 4.1 新增 `src/utils/datetime.ts`（`formatDate`、`formatDateTime`、`formatDateTimeSeconds`、`todayInBeijing`、`addDays`、`currentBeijingMonth`、`monthRange`）与单测，固定系统时间验证北京 00:30 跨日行为；验证 `npm run test:unit` 通过
- [x] 4.2 替换 17 处页面内重复的日期切片格式化（管理后台、游记、海报、分享、个人中心、记账等）；验证页面无 `replace('T'` / `slice(0,16)` 类重复实现残留
- [x] 4.3 修复 `PlanView` 默认开始日期与 `ItineraryEditView` / `ExpensesView` 的日期计算，统一使用 `todayInBeijing()` / `addDays()`；验证跨日单测与手工检查通过
- [x] 4.4 后台日期时间选择器（卡密、公告、用户到期）增加“北京时间”标注；验证文案与表单行为一致

## 5. 时区回归测试

- [x] 5.1 新增 `TimezoneIntegrationTest`：注入固定 Clock，覆盖按天过滤、游记归组、行程过期、分享过期、清理任务在北京 00:30 的边界；验证测试通过
- [x] 5.2 改写 `FeedbackIntegrationTest` 的日期过滤/限流用例，插入边界前后数据，确保能捕获 8 小时偏移；验证旧实现下会失败、新实现下通过
- [x] 5.3 在 CI 增加 UTC 矩阵 job：`TZ=UTC`、`-Duser.timezone=UTC`、MySQL 默认 UTC，运行时区相关集成测试；验证结果仍为北京时间
- [x] 5.4 补充前端日期工具与后端固定时钟的单测覆盖，确保 `npm run test:unit` 与 `mvn test` 全绿

## 6. 文档与验收

- [x] 6.1 更新 `TravelMapper DB V1.0.md`、`TravelMapper API V1.0.md` 的时间口径说明（北京时间墙钟、无时区格式、按天边界）；验证与实现一致
- [x] 6.2 在 `docs/ops/local-development.md` 记录开发库旧数据不迁移、可重置的说明；验证文档命令可执行
- [x] 6.3 运行 `openspec validate unify-beijing-timezone-handling --strict` 通过
- [ ] 6.4 运行后端单测、外部容器模式集成测试、UTC 矩阵与前端单测/构建，确认全绿并在 PR 中记录结果