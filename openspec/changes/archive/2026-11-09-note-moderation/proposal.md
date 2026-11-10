Triage: ready-for-agent

## Why

管理端「内容管理」只能看到标题与元信息，管理员无法查看游记正文，下架只能凭标题盲操作；同时平台没有用户举报入口，不当内容只能靠管理员偶然发现。需要补齐“可查看、可举报、可处理、可追溯”的内容治理闭环。

## What Changes

- **管理员查看完整内容**：新增管理员游记详情接口，可查看任意游记（含私密、已分享、已下架、用户软删除）的完整正文、图片/视频签名 URL、作者、可见性、分享状态与下架信息；内容管理列表增加「查看内容」抽屉，抽屉内可直接下架/恢复。
- **分享页举报**：`/s/{token}` 增加匿名与登录用户都可用的「举报」入口，提交原因分类（垃圾广告 / 辱骂攻击 / 色情低俗 / 违法信息 / 侵犯隐私 / 其他）与可选说明；`OTHER` 必须填写说明。
- **举报证据快照**：举报时保存标题、纯文本正文摘要与该时刻的 `note_updated_at`，作者事后改稿或删稿仍可追溯。
- **举报防刷**：登录用户对同一游记只保留一条待处理举报；匿名按「IP + 游记」24 小时去重，每 IP 每天最多 5 条；IP 只用于 Redis 限流，不落库。
- **管理端举报处理**：新增 `/admin/reports` 页面与举报列表、统计、详情、采纳、驳回接口；采纳时下架游记并把该游记全部待处理举报置为已采纳，驳回只处理当前条；写 `REPORT_ACCEPT` / `REPORT_REJECT` 审计。
- **内容治理补缺**：标题与正文统一经过敏感词替换后再校验 50 字；游记详情向作者返回下架原因与下架时间，并提示通过「意见反馈」申诉。
- **新增 `note_report` 表**与 Flyway 迁移；账号注销时把举报人置空，并清空该用户游记相关的举报快照内容。
- 更新 API/DB 文档，新增后端集成测试与分享页举报 E2E。

不做：敏感词命中自动审核队列、自动封禁、独立申诉工单、举报结果通知举报人、举报人可见的处理进度。

## Capabilities

### New Capabilities

- `notes/reporting`: 分享页匿名/登录举报的提交、原因与说明校验、证据快照、去重与限流。
- `admin/reports`: 管理员举报列表、统计、详情、采纳/驳回处理、与下架联动及审计。

### Modified Capabilities

- `admin/moderation`: 新增管理员查看任意游记完整内容（含私密、已下架、软删除）的能力。
- `notes/authoring`: 标题纳入敏感词过滤；作者可见下架原因与下架时间。
- `account/lifecycle`: 注销时匿名化举报人，并清空该用户游记对应的举报快照。
- `platform/persistence`: 业务表清单新增 `note_report`，并声明其不依赖 `deleted`。

> 归档顺序：`user-feedback` 必须先于 `note-moderation` 归档。本变更的 `account/lifecycle` 与 `platform/persistence` 增量基于 `user-feedback` 归档后的状态（已包含 `feedback` 表与反馈匿名化）。

## Impact

- 后端：新增 `repository/entity/NoteReport`、`repository/mapper/NoteReportMapper`、`repository/enums/ReportReason`/`ReportStatus`、`service/report/ReportService`、`controller/admin/AdminReportController`；分享控制器增加举报接口；`NoteService` 增加管理员详情、标题过滤与作者可见下架原因；`AccountDeletionService` 增加举报匿名化；Flyway `V20__note_report.sql`。
- API：新增 `GET /api/admin/notes/{id}`、`POST /api/s/{token}/reports`、`GET /api/admin/reports`、`GET /api/admin/reports/stats`、`GET /api/admin/reports/{id}`、`PUT /api/admin/reports/{id}/accept`、`PUT /api/admin/reports/{id}/reject`。
- 前端：修改 `AdminNotesView.vue`（查看内容抽屉）、`ShareView.vue`（举报入口与表单）、`NoteDetailView.vue`（下架原因）；新增 `AdminReportsView.vue`、`api/reports.ts`，并更新路由与「管理 ▾」菜单。
- 文档：`TravelMapper API  V1.0.md` 新增举报与管理员内容详情章节，`TravelMapper DB  V1.0.md` 新增 `note_report` 表与注销脱敏规则。
- 测试：新增 `NoteModerationIntegrationTest` 与分享页举报 E2E；更新注销测试断言举报匿名化。