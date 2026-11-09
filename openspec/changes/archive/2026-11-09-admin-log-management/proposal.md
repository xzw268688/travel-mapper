Triage: ready-for-agent

## Why

管理端现有的「审计日志」只能看到操作留痕，无法回答「谁在什么时候、从哪里登录，失败了多少次」。账号盗用、撞库与管理员登录异常缺少可追溯记录。需要把后台日志能力升级为「日志管理」，将操作日志与登录日志分为两类集中查看。

## What Changes

- 新增 `login_log` 表（Flyway `V21__login_log.sql`）：记录每次登录尝试的账号、用户 ID、角色、成功/失败、失败原因、IP、User-Agent 与时间；只追加，不提供修改/删除接口。
- 登录接口记录登录结果：成功记 `SUCCESS`；失败按 `USER_NOT_FOUND` / `BAD_CREDENTIALS` / `ACCOUNT_BANNED` / `ACCOUNT_DELETED` / `EMAIL_NOT_VERIFIED` / `RATE_LIMITED` 分类。登录日志写入采用尽力而为策略，写入失败不得影响登录本身。
- 新增只读接口 `GET /api/admin/login-logs`：分页、按时间倒序，支持账号模糊匹配、用户 ID、角色、成功/失败与时间区间筛选。
- 新增用户操作留痕：旅行、行程、游记、消费、清单、资料、反馈等关键业务写操作写入 `audit_log`；按用户语义聚合（天/景点变更并入行程、清单只记批量保存），只记成功操作，`detail` 仅保留对象 ID、变更字段名与数量，不存标题、正文、金额或备注原文。
- `audit_log` 增加 `actor_role` 快照列（`ADMIN` / `USER` / `SYSTEM` / `ANONYMOUS`）；历史行按用户当前角色回填，无用户回填 `SYSTEM`。
- 操作日志接口与页面增加「操作人角色」筛选与角色展示；日志写入失败只告警，不影响业务。
- 后台「审计日志」升级为「日志管理」页面，包含两个页签：「操作日志」（复用现有 `GET /api/admin/audit`）与「登录日志」（新接口）；登录日志展示结果、失败原因、IP 与 User-Agent。
- 路由 `/admin/audit` 重定向到 `/admin/logs`；顶栏「管理」菜单入口由「审计日志」改为「日志管理」。
- 更新 API 与 DB 文档；补充后端集成测试覆盖登录成功/失败留痕、用户操作留痕、角色筛选与普通用户拒绝。

非目标：异常登录告警、日志导出、按 IP 封禁、登录设备管理，以及清理定时任务的具体实现（仅约定保留期）。

## Capabilities

### New Capabilities

- `admin/login-log`: 登录事件的记录与管理员只读查询能力（成功/失败分类、筛选、分页）。
- `admin/user-activity-log`: 用户关键业务操作留痕、操作人角色快照与按角色筛选操作日志的能力。

### Modified Capabilities

- `ui/branding`: 管理菜单将「审计日志」入口调整为「日志管理」，页面按操作日志与登录日志两类展示，并在操作日志中提供角色筛选。

## Impact

- 数据库：新增 `V21__login_log.sql` 与 `V22__audit_actor_role.sql`（`actor_role` 列、索引与历史回填）。
- 后端：新增 `LoginLog` 实体、`LoginLogMapper`、`LoginLogService`、`AdminLoginLogController`、`AdminLoginLogService`、`AdminLoginLogView`；修改 `AuthService`/`AuthController` 记录登录结果；`AuditLogService` 自动补角色与 IP 并吞掉写入异常；在 `TripService`、`ItineraryService`、`ItineraryGenerationService`、`NoteService`、`ReportService`、`ExpenseService`、`ChecklistService`、`AuthService`、`FeedbackService` 中增加用户操作埋点；`AdminAuditService` 支持 `actorRole` 筛选。
- 前端：新增 `AdminLogsView.vue`，修改 `api/admin.ts`、路由与 `App.vue`；操作日志增加角色筛选与展示；移除被替换的 `AdminAuditView.vue`。
- 文档：`TravelMapper API  V1.0.md`、`TravelMapper DB  V1.0.md`。
- 测试：新增后端 `LoginLogIntegrationTest` 与用户操作日志集成测试；现有管理端权限回归不受影响。