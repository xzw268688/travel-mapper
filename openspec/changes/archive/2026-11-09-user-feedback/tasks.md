## 1. 数据层

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V19__feedback.sql`，按 design.md 创建 `feedback` 表与三个索引；在本地数据库启动后端确认 Flyway 迁移成功，并用 `SHOW CREATE TABLE feedback` 核对字段与索引
- [x] 1.2 新增 `Feedback` 实体、`FeedbackType` / `FeedbackStatus` 枚举与 `FeedbackMapper`；运行 `mvn -q -DskipTests compile` 确认编译通过
- [x] 1.3 在 `ErrorCode` 增加 `FEEDBACK_NOT_FOUND(2031, "反馈不存在", 404)`；运行 `ErrorCodeTest` 确认错误码唯一性与目录测试通过

## 2. 后端用户侧

- [x] 2.1 新增 `controller/feedback/dto/FeedbackDtos`，定义提交、本人列表与详情响应结构，包含字段长度与枚举校验注解；编译通过
- [x] 2.2 实现 `service/feedback/FeedbackService#submit`：校验分类/标题/正文/联系方式、过滤敏感词、调用 `RedisRateLimiter` 每日 5 条限制、写入 `NEW` 状态；由集成测试覆盖成功、非法参数、敏感词替换与第 6 条限流
- [x] 2.3 实现 `FeedbackService#mine` 分页查询，按 `user_id` 严格隔离并排除 `admin_note`/`handled_by`；由集成测试覆盖跨用户隔离与内部备注不可见
- [x] 2.4 新增 `FeedbackController` 暴露 `POST /api/feedback` 与 `GET /api/feedback/mine`；用集成测试或 Swagger 手测确认未登录返回 `1006`、合法提交返回 `id/status/createdAt`

## 3. 后端管理侧

- [x] 3.1 实现管理员分页列表与统计：`JdbcTemplate` 动态 WHERE 支持 type/status/keyword/from/to，`LEFT JOIN user` 并在注销用户时返回「已注销用户」；由集成测试覆盖各筛选组合、关键词与匿名化
- [x] 3.2 实现管理员详情查询，不存在时抛 `FEEDBACK_NOT_FOUND`；由集成测试覆盖存在与 `2031` 两种情况
- [x] 3.3 实现状态与内部备注更新：至少一项非空、备注 ≤1000 字、写入 `handled_by`/`handled_at`，并调用 `AuditLogService` 记录 `FEEDBACK_UPDATE`（详情仅含 ID 与状态变化）；由集成测试校验审计行
- [x] 3.4 新增 `AdminFeedbackController` 暴露列表、统计、详情、更新四个接口；由集成测试确认普通用户访问返回 `4002`

## 4. 注销匿名化

- [x] 4.1 在 `AccountDeletionService` 的事务中追加 `feedback` 匿名化：清空 `title`、`content`、`contact`、`admin_note`，保留 `type`/`status`/时间；由集成测试或现有注销测试断言反馈记录仍存在且内容为空

## 5. 前端

- [x] 5.1 新增 `frontend/src/api/feedback.ts`，并在 `api/admin.ts` 追加反馈管理类型与四个请求函数；`npm run build` 通过
- [x] 5.2 新增 `components/FeedbackDialog.vue`：分类、标题、正文、可选联系方式，前端校验与提交反馈提示；在 `App.vue` 用户下拉加入「意见反馈」入口，`npm run build` 通过并可手测提交成功
- [x] 5.3 在 `ProfileView.vue` 新增「我的反馈」卡片：分页列表、状态标签、详情抽屉，复用 `FeedbackDialog` 的提交入口；手测第 1 页数据与状态展示，确认无内部备注
- [x] 5.4 新增 `views/AdminFeedbackView.vue` 与 `/admin/feedback` 路由，页面包含三态计数、筛选栏、分页表格和处理抽屉；在 `App.vue`「管理 ▾」加入「用户反馈」；`npm run build` 通过并手测筛选、改状态、写备注
- [x] 5.5 在 `vite.config.ts` 读取 `package.json` 版本并 define `__APP_VERSION__`，在 `env.d.ts` 声明类型，提交反馈时携带该版本号；`npm run build` 后确认请求体包含当前版本号

## 6. 文档

- [x] 6.1 在 `TravelMapper API  V1.0.md` 新增用户反馈章节与管理员反馈接口，并在错误码表登记 `2031`；对照实现核对请求/响应字段与状态枚举
- [x] 6.2 在 `TravelMapper DB  V1.0.md` 新增 `feedback` 表定义、索引说明，并更新“业务表数量/清单”中涉及 19 张表的表述；对照 `V19__feedback.sql` 核对
- [x] 6.3 运行 `openspec validate user-feedback --strict`，确认提案、规格、设计与任务全部通过校验

## 7. 测试与验收

- [x] 7.1 新增 `FeedbackIntegrationTest`，覆盖合法提交、参数校验、敏感词、每日限流、本人历史隔离、管理员筛选/统计/详情/更新、审计写入与未授权访问；运行 `mvn test -Dtest=FeedbackIntegrationTest` 全部通过
- [x] 7.2 运行注销相关测试，确认反馈匿名化未破坏既有注销流程；`mvn test -Dtest=AuthIntegrationTest` 或对应测试通过
- [x] 7.3 运行后端全量测试与前端 `npm run build`，确认无回归；手工走查“用户提交 → 管理员处理 → 用户看到状态”闭环
- [x] 7.4 前后端联调后运行 `openspec validate user-feedback --strict` 与 `git diff --check`，确认无格式问题且规格与实现一致