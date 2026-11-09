## Context

TravelMapper 后端是 Spring Boot + MyBatis-Plus + Flyway，用户数据普遍按 `user_id` 行级隔离；管理端接口位于 `/api/admin/**`，由 `AdminInterceptor` 统一校验 `ADMIN` 角色，管理员列表查询多使用 `JdbcTemplate` 拼装动态 WHERE。平台已有三项可复用能力：`RedisRateLimiter`（按 key 计数）、`SensitiveWordFilter`（替换敏感词）和 `AuditLogService`（写 `audit_log`）。前端是 Vue 3 + Element Plus，管理端使用独立 `/admin/*` 路由和顶部下拉菜单，个人中心为单页卡片布局。

详见 `proposal.md` 的动机与 `specs/` 下的行为契约。

## Goals / Non-Goals

**Goals:**

- 用一张无软删除的表承载反馈，覆盖提交、本人历史、管理员筛选/处理三条路径。
- 复用既有鉴权、限流、敏感词和审计设施，不引入新依赖。
- 保持用户侧与管理员侧字段可见性严格分离（内部备注绝不出现在用户接口）。
- 让注销匿名化策略与现有 `AccountDeletionService` 的“清空内容、保留骨架”一致。

**Non-Goals:**

- 不做双向对话、通知、邮件、未处理徽标、附件上传或公开反馈墙。
- 不引入新的限流组件、消息队列或全文检索引擎。
- 不重构 `SensitiveWordFilter` 的包位置。

## Decisions

### 1. 两张控制器、一个服务

新增 `FeedbackController`（`/api/feedback`）与 `AdminFeedbackController`（`/api/admin/feedback`），共享 `FeedbackService`。用户接口只从 `CurrentUser.id()` 取用户，绝不接受 `userId` 入参；管理员接口依赖现有 `AdminInterceptor`。

替代方案是把管理接口并入用户控制器，但那会让 `/api/feedback` 的权限边界变模糊，且无法复用 `/api/admin/**` 的统一拦截。

### 2. `feedback` 表设计

```sql
CREATE TABLE `feedback` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT UNSIGNED NOT NULL,
  `type`        VARCHAR(16)     NOT NULL COMMENT 'FEATURE / BUG / UX / OTHER',
  `title`       VARCHAR(50)     NOT NULL,
  `content`     TEXT            NOT NULL,
  `contact`     VARCHAR(100)    NULL,
  `page_path`   VARCHAR(255)    NULL,
  `user_agent`  VARCHAR(512)    NULL,
  `app_version` VARCHAR(32)     NULL,
  `status`      VARCHAR(16)     NOT NULL DEFAULT 'NEW' COMMENT 'NEW / PROCESSING / RESOLVED',
  `admin_note`  VARCHAR(1000)   NULL,
  `handled_by`  BIGINT UNSIGNED NULL,
  `handled_at`  DATETIME        NULL,
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `created_at`),
  KEY `idx_status_time` (`status`, `created_at`),
  KEY `idx_type_time` (`type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户反馈';
```

枚举以 `VARCHAR` 存储，与 `user.status`、`travel_note.visibility` 等既有列保持一致；不建 `deleted` 列，因为反馈没有用户删除入口。不存 IP：限流按 `user_id`，滥用追溯使用 `audit_log` 的管理员操作记录即可。

### 3. 每日限流

提交前调用 `rateLimiter.check("feedback:submit:" + userId + ":" + LocalDate.now(), 5, 86400)`。key 内含日期，跨天自然换 key；`RedisRateLimiter` 读取 `app.rate-limit.enabled`，测试环境可关闭。超限抛 `RATE_LIMITED`（5004/429），不落库。

已知取舍：Redis 计数在数据库插入失败时仍会消耗一次配额；相比“先入库后计数”更简单，也避免并发下超发，V1 接受。

### 4. 内容处理

提交时对 `title` 与 `content` 调用 `SensitiveWordFilter.filter`，复用 `sys_config` 的 `sensitive.words`。该组件当前位于 `service.note` 包，跨包引用虽然不够干净，但提取到公共包会牵动游记模块；本变更先直接注入，后续可单独重构。

### 5. 管理员列表与统计

列表沿用 `AdminNoteView` 的做法，用 `JdbcTemplate` + `LEFT JOIN user` 拼动态 WHERE：

- `type` / `status` 先 `toUpperCase` 校验枚举，非法值返回 `2001`。
- `keyword` 匹配 `f.title`、`f.content`、`f.contact`、`u.username`、`u.nickname`。
- `from` / `to` 按 `created_at` 半开区间过滤（`to + 1 天`）。
- 已注销用户（`u.status = 'DELETED'`）在视图层显示「已注销用户」，不回显原用户名/昵称。
- 统计用一条 `GROUP BY status` 查询，返回 `{ total, new, processing, resolved }`。

### 6. 状态更新与审计

`PUT /api/admin/feedback/{id}` 接收可选 `status` 与 `adminNote`，至少一项非空。校验后更新 `status`、`adminNote`、`handledBy = CurrentUser.id()`、`handledAt = now()`。审计写入：

```text
action=FEEDBACK_UPDATE, targetType=FEEDBACK, targetId=<feedbackId>,
detail={oldStatus, newStatus, noteChanged: true/false}
```

审计详情不写正文与备注全文，避免内部信息二次扩散。状态允许在三态之间任意流转，包括原地更新备注。

### 7. 注销匿名化

在 `AccountDeletionService` 的现有事务中追加：

```sql
UPDATE feedback
SET title = '', content = '', contact = NULL, admin_note = NULL
WHERE user_id = ?
```

保留 `type`、`status`、`created_at`、`updated_at` 等骨架字段，供后台统计与处理记录追踪；清空 `admin_note`，因为内部备注可能引用用户内容。管理员列表通过 `user.status` 显示「已注销用户」。

### 8. 前端交互

- 新增 `components/FeedbackDialog.vue`，`v-model:visible` 控制，内部负责表单、校验、提交与成功提示；在 `App.vue` 用户下拉的「意见反馈」和 `ProfileView.vue` 的「我的反馈」卡片中复用。
- `App.vue` 用户下拉新增 `command="feedback"`；`onUserCommand` 打开对话框。
- `ProfileView.vue` 新增「我的反馈」卡片：分页表格/列表 + 详情抽屉，状态标签颜色化。
- 新增 `views/AdminFeedbackView.vue`：顶部三个统计卡片 + 筛选栏 + 分页表格 + 右侧处理抽屉；`router/index.ts` 增加 `meta.admin` 路由，`App.vue` 的「管理 ▾」增加「用户反馈」。
- 新增 `api/feedback.ts`；管理接口追加到 `api/admin.ts`，类型定义沿用现有 `type` 导出风格。

### 9. 版本号注入

在 `vite.config.ts` 中读取 `package.json` 的 `version`，通过 `define: { __APP_VERSION__: JSON.stringify(pkg.version) }` 注入；`env.d.ts` 声明 `declare const __APP_VERSION__: string`。提交时把该常量作为 `appVersion` 传给后端。这样避免在源码里硬编码版本号。

### 10. 错误码与响应

新增 `FEEDBACK_NOT_FOUND(2031, "反馈不存在", 404)`。其余复用 `PARAM_INVALID`、`RATE_LIMITED`、`JWT_INVALID`、`FORBIDDEN_ADMIN`。所有接口返回现有 `ApiResponse` / `PageResult` 包装。

## Risks / Trade-offs

- [限流可被多账号绕过] → V1 接受；后续可按 IP 或设备维度增加二级限流。
- [敏感词过滤会破坏错误日志/URL 中的技术细节] → 管理员仍可从页面路径与 UA 定位；如问题严重，后续调整为“原文留存 + 管理端标记”，但需要额外的隐私评估。
- [用户不会被主动通知状态变化] → 个人中心历史是唯一入口；符合本次范围，后续可加站内通知。
- [反馈表无归档策略] → V1 数据量小；后续按创建时间分区或归档。
- [管理员内部备注在注销时被清空] → 可能丢失处理上下文；这是隐私优先的取舍，审计日志仍保留状态变更记录。
- [前端提交的 `pagePath`/`appVersion` 不可信] → 仅作辅助定位，不参与权限或统计决策。

## Migration Plan

1. 发布后端：执行 Flyway `V19__feedback.sql`（纯新增表，无破坏性变更），部署新接口。
2. 发布前端：新菜单、对话框与管理页；旧前端不受影响。
3. 回滚：后端回滚到旧版本即可，`feedback` 表保留不影响旧代码；如需彻底回滚，手工 `DROP TABLE feedback`（会丢失已收集反馈，需先备份）。

## Open Questions

- 反馈数据的保留期限与清理策略（例如超过 N 年归档）尚未确定，不影响本次实现。
- 是否需要在未来引入用户可见回复，取决于反馈量和管理员实际使用情况。