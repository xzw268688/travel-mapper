## 1. 数据库与实体

- [x] 1.1 新增 `V21__login_log.sql`：登录日志表（账号、用户 ID、角色、成功、原因、IP、User-Agent、时间）与查询索引，验证方式：启动后 Flyway 迁移成功且包含预期索引
- [x] 1.2 新增 `LoginLog` 实体与 `LoginLogMapper`，验证方式：后端编译通过

## 2. 登录留痕

- [x] 2.1 新增 `LoginLogService`：尽力而为写入，任何异常只记录 `warn` 不抛出，验证方式：单元/集成测试注入失败场景时登录不受影响
- [x] 2.2 `AuthService.login` 接收客户端 IP 与 User-Agent，成功记 `SUCCESS`，失败按 `USER_NOT_FOUND` / `BAD_CREDENTIALS` / `ACCOUNT_BANNED` / `ACCOUNT_DELETED` / `EMAIL_NOT_VERIFIED` / `RATE_LIMITED` 记录，验证方式：集成测试逐类断言 `login_log.reason`
- [x] 2.3 `AuthController.login` 解析 `X-Forwarded-For` 首个地址与 User-Agent（截断 255）并传入服务，验证方式：集成测试断言记录中的 IP 与 UA

## 3. 管理端接口

- [x] 3.1 新增 `GET /api/admin/login-logs`：按账号模糊、用户 ID、角色、成功/失败、时间区间分页查询，时间倒序，验证方式：集成测试覆盖各筛选条件
- [x] 3.2 新增 `AdminLoginLogView` 与 `AdminLoginLogService`（`JdbcTemplate` 实现，复用 `PageRequest`/`PageResult`），验证方式：响应字段与规格一致
- [x] 3.3 权限校验：普通用户与匿名访问返回 `4002`，验证方式：集成测试断言 HTTP 403 与 `code=4002`

## 4. 前端日志管理页

- [x] 4.1 新增 `AdminLogsView.vue`，以「操作日志 / 登录日志」两个页签承载；操作日志沿用现有筛选与展开能力，登录日志支持账号、用户 ID、角色、结果与日期筛选，验证方式：`npm run build` 通过且页面可切换
- [x] 4.2 `api/admin.ts` 增加 `AdminLoginLogView` 与 `listLoginLogs`，验证方式：TypeScript 编译通过
- [x] 4.3 路由新增 `/admin/logs`，`/admin/audit` 重定向到 `/admin/logs`；`App.vue`「管理」菜单入口改为「日志管理」，验证方式：旧地址可跳转，管理员可见新入口
- [x] 4.4 移除被替换的 `AdminAuditView.vue`，验证方式：无残留引用且构建通过

## 5. 文档与回归

- [x] 5.1 更新 `TravelMapper API  V1.0.md`：新增登录日志接口说明、查询参数、响应示例与速查表条目，验证方式：字段与实现一致
- [x] 5.2 更新 `TravelMapper DB  V1.0.md`：新增 `login_log` 表清单、DDL、保留期与清理策略，验证方式：与迁移文件一致
- [x] 5.3 后端全量测试与前端构建通过，验证方式：`mvn test`（或项目等价命令）与 `npm run build` 退出码为 0

## 6. 操作日志角色快照

- [x] 6.1 新增 `V22__audit_actor_role.sql`：`audit_log` 增加 `actor_role` 与 `(actor_role, created_at)` 索引，历史行按 `user.role` 回填、无用户回填 `SYSTEM`，验证方式：迁移后查询不存在 `actor_role IS NULL`
- [x] 6.2 `AuditLog` 增加 `actorRole`；`AuditLogService` 自动解析角色（`ADMIN` / `USER` / `SYSTEM` / `ANONYMOUS`）并补 IP，写入异常只 `warn`，验证方式：单元测试覆盖四类角色与失败不抛出
- [x] 6.3 `AdminAuditView`、`AdminAuditService`、`AdminAuditController` 增加 `actorRole` 返回与筛选，验证方式：集成测试按角色过滤结果正确

## 7. 用户操作埋点

- [x] 7.1 旅行与行程：`TRIP_UPDATE` / `TRIP_STATUS` / `TRIP_DELETE`、`ITINERARY_CREATE` / `ITINERARY_UPDATE` / `ITINERARY_DELETE` / `ITINERARY_GENERATE`，天/景点变更聚合，验证方式：集成测试断言动作与 `detail` 计数
- [x] 7.2 游记与分享：`NOTE_CREATE` / `NOTE_UPDATE` / `NOTE_DELETE` / `NOTE_SHARE` / `NOTE_UNSHARE` / `NOTE_REPORT`，匿名举报角色为 `ANONYMOUS`，验证方式：集成测试断言动作、角色与 `detail` 不含标题/正文
- [x] 7.3 消费与清单：`EXPENSE_CREATE` / `EXPENSE_UPDATE` / `EXPENSE_DELETE`、`CHECKLIST_SAVE`，验证方式：集成测试断言动作与 `detail` 不含量额/备注
- [x] 7.4 账号与反馈：`USER_PROFILE_UPDATE`、`FEEDBACK_CREATE`，验证方式：集成测试断言动作与变更字段名
- [x] 7.5 失败请求不写日志，日志写入异常不影响业务，验证方式：集成/单元测试覆盖

## 8. 前端与文档

- [x] 8.1 `AdminLogsView` 操作日志增加「操作人角色」筛选与角色列，默认全部，验证方式：`npm run build` 通过且筛选请求参数正确
- [x] 8.2 更新 `TravelMapper API  V1.0.md`：`GET /admin/audit` 增加 `actorRole` 与响应字段，补充用户动作清单与 detail 规则，验证方式：字段与实现一致
- [x] 8.3 更新 `TravelMapper DB  V1.0.md`：`audit_log` DDL 增加 `actor_role`、索引与回填说明，验证方式：与 `V22` 一致
- [x] 8.4 后端全量测试与前端构建通过，验证方式：`mvn test`（或项目等价命令）与 `npm run build` 退出码为 0