## Context

现有操作审计由 `audit_log` 承担：`AuditLogService` 负责写入，`AdminAuditService` 用 `JdbcTemplate` 提供只读分页查询，前端 `AdminAuditView` 展示。认证入口 `AuthService.login` 目前只更新 `user.last_login_at`，不保留任何登录历史。参见 `proposal.md` 与 `specs/admin/login-log/spec.md`。

## Goals / Non-Goals

**Goals**

- 登录成功与失败都留痕，且失败原因可区分。
- 用户侧关键业务写操作进入操作日志，可按角色区分管理员、用户、系统与匿名。
- 日志写入与业务主流程解耦：日志故障不影响登录或业务操作。
- 管理端在一个页面内切换操作日志与登录日志，操作日志支持角色筛选。

**Non-Goals**

- 不做实时告警、日志导出、按 IP 封禁或设备管理。
- 不记录查询、浏览、上传与 AI 润色等非关键动作。
- 不改写既有管理员操作的 `detail` 结构。
- 不在本次实现定时清理任务，仅在数据库文档中约定保留期。

## Decisions

### 独立 `login_log` 表，而不是复用 `audit_log`

`audit_log` 的 `user_id` 为空时无法区分「系统任务」与「登录失败的不存在账号」，且 `detail` JSON 不利于按账号、结果做索引筛选。登录日志需要 `username` 快照、`success`、`reason`、`user_agent` 等独立列与索引，因此新增 `login_log`。替代方案是向 `audit_log` 写 `action=LOGIN_FAIL` 并把账号塞进 `detail`，但查询与索引都会退化。

### 在 `AuthService` 内同步、尽力而为地写入

登录接口本身没有事务，单条 insert 可接受；用 `try/catch` 包裹整段日志写入并记录 `warn`，保证日志异常不改变登录结果。替代方案是发消息/异步线程，但对单机部署增加复杂度且可能丢日志。

### 失败原因使用稳定字符串码

原因码为 `SUCCESS` / `USER_NOT_FOUND` / `BAD_CREDENTIALS` / `ACCOUNT_BANNED` / `ACCOUNT_DELETED` / `EMAIL_NOT_VERIFIED` / `RATE_LIMITED`，与业务错误码解耦，前端可直接映射中文文案。成功记录的 `reason` 为空。

### 账号与客户端信息口径

账号沿用登录接口的归一化规则（邮箱转小写）；IP 取 `X-Forwarded-For` 首个地址，缺失时回退 `remoteAddr`；User-Agent 截断到 255 字符。`RATE_LIMITED` 发生在账号查询之前，因此该条记录只保留账号与客户端信息，`user_id` 为空。

### 管理端接口与页面结构

新增 `GET /api/admin/login-logs`，查询实现与 `AdminAuditService` 一致（`JdbcTemplate` + `PageRequest`/`PageResult`），返回 `AdminLoginLogView`。前端将 `AdminAuditView` 替换为 `AdminLogsView`，用 `el-tabs` 承载两类日志；操作日志继续调用 `/admin/audit`。保留 `/admin/audit` 路由并重定向到 `/admin/logs`，避免收藏链接失效。

### 用户操作复用 `audit_log` 并增加角色快照

用户操作与管理员操作最终都服务于同一份操作日志，复用 `audit_log` 可沿用现有查询、脱敏与页面。`V22` 增加 `actor_role VARCHAR(16) NULL` 与 `(actor_role, created_at)` 索引，并在迁移中按 `user.role` 回填历史行、无用户回填 `SYSTEM`。替代方案是查询时 join `user`，但角色变更后历史记录会失真。`ACTOR_ROLE` 取 `ADMIN` / `USER` / `SYSTEM` / `ANONYMOUS`。

### 服务层显式埋点，按用户语义聚合

在 `TripService`、`ItineraryService`、`ItineraryGenerationService`、`NoteService`、`ReportService`、`ExpenseService`、`ChecklistService`、`AuthService`、`FeedbackService` 的关键写方法成功后调用 `auditLogService.record`。`/itinerary/save` 固定为 `ITINERARY_CREATE`；生成草稿与重新生成都用 `ITINERARY_GENERATE`，以 `detail.mode` 区分。行程下的天/景点、清单单项不单独记录，聚合为父操作。替代方案是给用户侧方法加 `@Auditable` 由切面自动抓参数，但无法满足聚合与「detail 不含用户内容」的约束。

### 角色与 IP 在 `AuditLogService` 内统一解析

调用方无需改签名：`record` 内当 `userId` 非空且与当前请求身份一致时使用 `RequestContext.role`，否则按用户表角色回退；`userId` 为空时，有 HTTP 请求记为 `ANONYMOUS`，无请求记为 `SYSTEM`。IP 传入为空时从当前请求解析 `X-Forwarded-For` 首跳并回退 `remoteAddr`。写入异常在服务内捕获并 `warn`，与 `AuditAspect` 的「审计不得影响业务」原则一致。

### 只记成功操作，detail 最小化

埋点放在业务写方法成功返回前；异常路径不记录。`detail` 只放对象 ID、变更字段名、数量与枚举值，例如 `{"changed":["title","visibility"]}`、`{"dayCount":3,"spotCount":8}`；标题、正文、金额、备注与联系方式一律不写。保留期沿用 `audit_log` 的 30 天。

## Risks / Trade-offs

- [每次登录多一次数据库写入] → 单条 insert、字段精简，且失败不影响登录。
- [用户操作使 `audit_log` 写入量上升] → 按语义聚合、`detail` 精简、30 天保留，并新增 `(actor_role, created_at)` 索引。
- [日志含账号、IP 与用户行为等敏感信息] → 仅管理员可读；`detail` 不含标题、正文、金额与备注；约定保留期。
- [`X-Forwarded-For` 可被伪造] → 与现有发码接口口径一致，仅在可信反向代理后部署时生效。
- [日志写入失败导致业务回滚] → `AuditLogService` 捕获异常并 `warn`，业务继续。
- [恶意登录导致表膨胀] → 账号、结果、时间索引；文档约定定期清理。

## Migration Plan

1. 执行 `V21__login_log.sql` 新增登录日志表。
2. 执行 `V22__audit_actor_role.sql`：增加 `actor_role` 与索引，并回填历史记录。
3. 部署后端：登录与用户操作开始留痕，查询接口支持 `actorRole`。
4. 部署前端：切换到日志管理页并启用角色筛选。
5. 回滚时前端可退回旧页面；新表与新列均为增量，旧代码忽略 `actor_role` 仍可运行。

## Open Questions

无。