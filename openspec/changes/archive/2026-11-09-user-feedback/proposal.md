Triage: ready-for-agent

## Why

TravelMapper 目前没有任何产品内反馈渠道，用户的问题与建议只能走站外，管理员也无法在后台集中查看和处理。V1.0 发布前需要建立最低成本的反馈闭环：用户能提交并看到处理进度，管理员能集中筛选、处理和留痕。

## What Changes

- 新增登录用户提交反馈：顶栏用户下拉「意见反馈」打开对话框，填写分类（功能建议 / 问题反馈 / 体验问题 / 其他）、标题（≤50 字）、正文（10~1000 字）和可选联系方式；自动附带当前页面路径、浏览器 UA 与前端版本号。
- 新增「我的反馈」：个人中心以卡片 + 详情抽屉展示本人反馈历史与状态（`NEW` / `PROCESSING` / `RESOLVED`）；用户不可编辑、撤回或删除，也看不到管理员内部备注。
- 新增管理端 `/admin/feedback`：三态计数、按类型/状态/关键词/日期筛选、分页列表、详情与处理抽屉；管理员可更新状态和内部备注，每次操作写审计日志 `FEEDBACK_UPDATE`。
- 新增 `feedback` 表与 Flyway `V19__feedback.sql`；不存 IP，不做软删除。
- 防滥用：每用户每天最多提交 5 条（Redis 限流，复用 `RATE_LIMITED`）；标题与正文经过敏感词过滤。
- 账号注销时清空反馈的标题、正文与联系方式，保留类型、状态、时间等记录骨架。
- 前端构建通过 Vite `define` 注入 `__APP_VERSION__`（取自 `package.json`）。
- 更新根目录 API 与 DB 文档，新增后端集成测试覆盖提交、限流、行级隔离、管理员处理、审计与注销匿名化。

不做：管理员对用户可见的回复、邮件通知、顶栏未处理徽标、截图/附件、公开反馈墙、用户编辑/撤回/删除反馈。

## Capabilities

### New Capabilities

- `feedback/submission`: 登录用户提交反馈、查看本人反馈历史与状态的完整行为，包括字段校验、自动上下文采集、每日限流和敏感词过滤。
- `admin/feedback`: 管理员查看反馈计数与分页列表、按条件筛选、更新处理状态与内部备注，以及审计留痕。

### Modified Capabilities

- `account/lifecycle`: 注销匿名化清单新增 `feedback` 表，清空标题、正文与联系方式并保留记录骨架。
- `platform/persistence`: V1.0 业务表清单新增 `feedback`，并声明该表不依赖 `deleted` 标记。

## Impact

- 后端：新增 `controller/feedback`、`service/feedback`、`repository/entity/Feedback`、`repository/mapper/FeedbackMapper`、`repository/enums/FeedbackType`/`FeedbackStatus`，以及 `controller/admin/AdminFeedbackController`；`ErrorCode` 新增 `FEEDBACK_NOT_FOUND`；`AccountDeletionService` 增加反馈匿名化；Flyway `V19__feedback.sql`。
- API：新增 `POST /api/feedback`、`GET /api/feedback/mine`、`GET /api/admin/feedback`、`GET /api/admin/feedback/stats`、`GET /api/admin/feedback/{id}`、`PUT /api/admin/feedback/{id}`。
- 前端：新增 `api/feedback.ts`、`components/FeedbackDialog.vue`、`views/AdminFeedbackView.vue`；修改 `App.vue`、`ProfileView.vue`、`router/index.ts`、`api/admin.ts`、`vite.config.ts`。
- 文档：`TravelMapper API  V1.0.md` 新增反馈章节与错误码，`TravelMapper DB  V1.0.md` 新增 `feedback` 表。
- 测试：新增 `FeedbackIntegrationTest`；现有 `AuthIntegrationTest` 或注销相关测试补充反馈匿名化断言。