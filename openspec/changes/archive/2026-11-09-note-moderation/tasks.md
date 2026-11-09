## 1. 数据层

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V20__note_report.sql`，按 design.md 创建 `note_report` 表与三个索引；在本地数据库启动后端确认 Flyway 迁移成功，并用 `SHOW CREATE TABLE note_report` 核对
- [x] 1.2 新增 `NoteReport` 实体、`ReportReason` / `ReportStatus` 枚举与 `NoteReportMapper`；运行 `mvn -q -DskipTests compile` 编译通过
- [x] 1.3 在 `ErrorCode` 增加 `REPORT_NOT_FOUND(2032, "举报不存在", 404)`；运行 `ErrorCodeTest` 通过

## 2. 管理员查看游记内容

- [x] 2.1 在 `TravelNoteMapper` 增加包含软删除记录的查询方法，新增管理员游记详情 DTO 与 `NoteService.adminDetail`：返回正文、600 秒媒体签名 URL、作者、可见性、分享状态与下架信息；由集成测试覆盖私密、已下架、软删除三种情况
- [x] 2.2 在 `AdminNoteController` 暴露 `GET /api/admin/notes/{id}`；由集成测试确认普通用户返回 `4002`、不存在返回 `2004`
- [x] 2.3 在 `NoteDtos.Response` 增加 `takedownReason` 与 `takedownAt`，作者详情返回；由集成测试覆盖有原因与无原因两种情况
- [x] 2.4 创建与编辑游记时对标题执行敏感词替换并在超过 50 字时截断；由集成测试覆盖标题命中替换与长度边界

## 3. 举报提交

- [x] 3.1 新增 `controller` 举报 DTO 与 `ReportService.submit`：校验原因/说明、拒绝作者自举报、保存标题/纯文本/版本时间快照、记录登录举报人；由集成测试覆盖成功、`OTHER` 缺说明、作者自举报与失效令牌
- [x] 3.2 实现举报去重与限流：登录用户同游记一条 `PENDING`、匿名 IP+游记 24 小时去重、每 IP 每日 5 条；由集成测试覆盖重复提交、`duplicate=true` 与第 6 条返回 `5004`
- [x] 3.3 在 `ShareController` 增加 `POST /s/{token}/reports` 公开接口；由集成测试确认匿名与登录提交可用、响应仅含回执字段

## 4. 管理端举报处理

- [x] 4.1 实现管理员举报列表与统计：状态/原因/日期/关键词筛选、按时间倒序、注销举报人显示「匿名访客」、注销作者显示「已注销用户」；由集成测试覆盖筛选与匿名化
- [x] 4.2 实现举报详情：快照、举报人、游记当前状态与作者历史被采纳举报数，不存在返回 `2032`；由集成测试覆盖详情与 `2032`
- [x] 4.3 实现采纳举报：`noteReason` 必填、下架并撤销分享、把该游记全部 `PENDING` 举报置为 `ACCEPTED`、已删除游记只关闭举报、写 `REPORT_ACCEPT` 与 `NOTE_TAKEDOWN`；由集成测试校验状态、分享失效与审计
- [x] 4.4 实现驳回举报：只把当前举报置为 `REJECTED`、不改游记与其他举报、写 `REPORT_REJECT`；由集成测试校验
- [x] 4.5 新增 `AdminReportController` 暴露列表、统计、详情、采纳、驳回；由集成测试确认普通用户返回 `4002`

## 5. 注销匿名化

- [x] 5.1 在 `AccountDeletionService` 中把举报人 `reporter_user_id` 置空，并清空该用户游记相关举报的标题与纯文本快照；更新 `AuthIntegrationTest` 断言举报匿名化

## 6. 前端

- [x] 6.1 新增 `frontend/src/api/reports.ts`，并在 `api/admin.ts` 追加管理员游记详情与举报管理类型/请求函数；`npm run build` 通过
- [x] 6.2 `AdminNotesView.vue` 增加「查看内容」抽屉，渲染正文、媒体、作者、分享状态与下架信息，抽屉内可下架/恢复；`npm run build` 通过并手测
- [x] 6.3 新增 `AdminReportsView.vue` 与 `/admin/reports` 路由，页面含统计卡片、筛选栏、分页表格与处理抽屉（采纳/驳回）；`App.vue`「管理 ▾」增加「举报处理」；`npm run build` 通过并手测
- [x] 6.4 `ShareView.vue` 增加举报入口与表单（原因、说明），提交成功提示；手测匿名与登录两种提交
- [x] 6.5 `NoteDetailView.vue` 展示下架原因与时间，并提供「去反馈」链接；`npm run build` 通过

## 7. 文档

- [x] 7.1 在 `TravelMapper API  V1.0.md` 新增管理员游记详情、举报提交与举报管理接口，登记错误码 `2032`、举报原因/状态枚举与速查表；对照实现核对字段
- [x] 7.2 在 `TravelMapper DB  V1.0.md` 新增 `note_report` 表、索引、保留策略与注销脱敏规则，并更新表清单数量（含 `feedback` 后为 21 张）；对照迁移核对
- [x] 7.3 运行 `openspec validate note-moderation --strict` 通过

## 8. 测试与验收

- [x] 8.1 新增 `NoteModerationIntegrationTest`，覆盖管理员查看内容、标题过滤、举报提交/去重/限流、管理员筛选/统计/详情/采纳/驳回、审计与越权；运行 `mvn test -Dtest=NoteModerationIntegrationTest` 通过
- [x] 8.2 运行注销相关测试，确认举报匿名化未破坏既有流程；`mvn test -Dtest=AuthIntegrationTest` 通过
- [x] 8.3 运行后端全量测试与前端 `npm run build`，并手工走查“匿名举报 → 管理员查看内容 → 采纳下架 → 分享页失效 → 作者看到原因”闭环
- [x] 8.4 运行 `openspec validate note-moderation --strict` 与 `git diff --check`，确认规格与实现一致、无格式问题