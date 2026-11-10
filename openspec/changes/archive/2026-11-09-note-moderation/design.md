## Context

管理端现有 `AdminNoteView` 只承载标题与元信息，`NoteService.adminList` 的 SQL 不查询 `content`；用户游记详情 `GET /notes/{id}` 通过 `requireOwned` 限制为作者本人。分享页 `GET /s/{token}` 是他人接触游记的唯一公开路径。平台已有 `SensitiveWordFilter`、`RedisRateLimiter`、`AuditLogService`、`StorageUrlService.presignDownload` 和 `AdminInterceptor`，可直接复用。

本变更的 `account/lifecycle` 与 `platform/persistence` 增量基于 `user-feedback` 归档后的状态，归档顺序必须先 `user-feedback` 再 `note-moderation`。详见 `proposal.md` 与 `specs/`。

## Goals / Non-Goals

**Goals:**

- 管理员能查看任意游记完整内容，作为下架与举报处理的判断依据。
- 分享页访客能低门槛举报，同时用去重和限流控制滥用。
- 举报证据在作者改稿/删稿后仍可追溯，且注销时按隐私规则清理。
- 采纳举报与现有下架/撤销分享/审计流程复用同一套逻辑。

**Non-Goals:**

- 不实现敏感词命中自动审核队列或自动封禁。
- 不实现独立申诉工单、举报结果通知或举报人进度页。
- 不引入图片/视频内容识别、人工审核排班或审核员角色。

## Decisions

### 1. 管理员游记详情

新增 `GET /api/admin/notes/{id}`，由 `AdminNoteController` 暴露，复用 `AdminInterceptor`。查询使用新增的 `TravelNoteMapper.selectByIdIncludingDeleted`（`@Select` 不带 `deleted` 过滤），再补作者信息与签名 URL：

- 正文直接返回清洗后的 HTML，由管理端前端渲染；前端沿用游记详情页的净化展示方式。
- 图片/视频使用 `storageUrlService.presignDownload` 生成 600 秒 URL；不返回 objectKey 之外的长期直链。
- 分享状态只返回 `hasActiveShare` 与到期时间，不返回令牌，避免管理员误复制外链。
- 查看为只读，不写审计。

### 2. `note_report` 表

```sql
CREATE TABLE `note_report` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `note_id`           BIGINT UNSIGNED NOT NULL,
  `reporter_user_id`  BIGINT UNSIGNED NULL COMMENT '登录举报人；匿名为 NULL',
  `reason`            VARCHAR(16)     NOT NULL COMMENT 'SPAM/ABUSE/PORN/ILLEGAL/PRIVACY/OTHER',
  `description`       VARCHAR(200)    NULL,
  `note_title`        VARCHAR(50)     NOT NULL COMMENT '举报时的标题快照',
  `note_content_text` TEXT            NULL COMMENT '举报时的纯文本摘要快照',
  `note_updated_at`   DATETIME        NULL COMMENT '举报时的游记更新时间',
  `status`            VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED',
  `handled_by`        BIGINT UNSIGNED NULL,
  `handled_at`        DATETIME        NULL,
  `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_time` (`status`, `created_at`),
  KEY `idx_note_time` (`note_id`, `created_at`),
  KEY `idx_reporter_note` (`reporter_user_id`, `note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游记举报';
```

不建 `deleted` 列。登录用户「同一游记只保留一条 PENDING」通过查询判断，不建唯一索引：同一用户在不同时间可以对新一轮违规再次举报，唯一索引会永久阻断。并发下的极小概率重复由事务内 `SELECT ... FOR UPDATE` 或二次查询兜底；V1 接受该边界。

### 3. 公开举报接口与身份

在 `ShareController` 增加 `POST /s/{token}/reports`；`SecurityPaths` 已把 `/s/` 前缀视为公开，无需改安全配置。身份来源：

- 请求带有效 JWT 时，`RequestContext.userIdOrNull()` 返回举报人 ID；JWT 失效时按公开接口容错为匿名。
- 无 JWT 时为匿名。
- 作者本人（`userId == note.userId`）举报返回 `2001`。

限流 key：

- 匿名去重：`report:anon:{sha256(ip+":"+token)}`，TTL 24 小时。
- 每日上限：`report:ip:{sha256(ip)}:{yyyyMMdd}`，limit 5，TTL 86400。
- IP 只参与 key 计算，不写库；日志也不记录原始 IP。

登录用户去重不走 Redis，查询 `note_report WHERE reporter_user_id=? AND note_id=? AND status='PENDING'`；命中则返回 `duplicate=true`。

### 4. 快照与展示

举报时从 `travel_note` 读取 `title`、`content_text`（最长 600 字）与 `updated_at` 写入快照。管理员列表的关键词匹配快照标题、举报说明、当前标题与作者用户名/昵称；详情同时展示快照和当前内容入口，前端在两者 `updated_at` 不一致时提示“举报后内容已修改”。

### 5. 举报处理与下架联动

- `PUT /api/admin/reports/{id}/accept` 请求体 `{ "noteReason": "..." }`，`noteReason` 1–100 字必填。
- 事务内：校验举报为 `PENDING`；若游记存在且未软删除，调用现有 `adminTakedown` 逻辑（下架、撤销分享、写 `NOTE_TAKEDOWN`）；无论游记是否已删除，将该游记全部 `PENDING` 举报置为 `ACCEPTED` 并写入 `handled_by`/`handled_at`；写 `REPORT_ACCEPT` 审计，详情含举报 ID、游记 ID、原因。
- `PUT /api/admin/reports/{id}/reject`：只把当前举报置为 `REJECTED` 并写 `REPORT_REJECT`，不改游记与其他举报。
- 已处理举报再次操作返回 `2001`。
- 作者历史被采纳举报数按 `note_report JOIN travel_note WHERE travel_note.user_id=? AND status='ACCEPTED'` 统计，包含当前这条；仅用于辅助判断，不自动封禁。

### 6. 作者可见的下架信息

`NoteDtos.Response` 增加 `takedownReason` 与 `takedownAt`（可能为 null），不做数据库变更。游记详情页在下架提示中展示原因，并链接到 `/profile` 的「意见反馈」入口作为申诉通道。

### 7. 标题敏感词过滤

`NoteService` 创建与编辑标题时，先 trim 与长度校验，再调用 `sensitiveWordFilter.filter(title)`，最后若超过 50 字截断到 50 字。正文沿用现有 `sanitize` 中的过滤。该顺序保证超长标题不会因替换字符扩张而写入失败。

### 8. 注销匿名化

在 `AccountDeletionService` 事务中追加：

```sql
UPDATE note_report SET reporter_user_id = NULL
WHERE reporter_user_id = ?;

UPDATE note_report r
JOIN travel_note n ON n.id = r.note_id
SET r.note_title = '', r.note_content_text = NULL
WHERE n.user_id = ?;
```

保留举报原因、状态、时间与 `note_updated_at`，便于统计；不保留任何用户文本。举报人显示为「匿名访客」。

### 9. 前端

- `AdminNotesView.vue`：表格增加「查看内容」按钮，右侧抽屉渲染正文、媒体与下架信息；抽屉内复用现有下架/恢复调用。
- 新增 `AdminReportsView.vue` 与 `/admin/reports` 路由，管理菜单增加「举报处理」；顶部统计卡片、筛选栏、分页表格、详情抽屉，抽屉内提供「采纳并下架」「驳回」。
- `ShareView.vue`：底部「举报」按钮打开对话框，原因单选 + 说明输入；提交成功提示“已收到举报”。
- `NoteDetailView.vue`：下架提示展示原因与时间，并提供「去反馈」链接。
- 新增 `api/reports.ts`；管理员笔记详情与举报接口并入 `api/admin.ts`。

### 10. 错误码

新增 `REPORT_NOT_FOUND(2032, "举报不存在", 404)`。其余复用 `NOTE_NOT_FOUND`、`SHARE_INVALID`、`PARAM_INVALID`、`RATE_LIMITED`、`FORBIDDEN_ADMIN`。

## Risks / Trade-offs

- [管理员可查看全部私密内容] → 这是本次明确选择的能力；通过仅 ADMIN 接口、只读、不写审计控制，后续如需更严格可增加查看审计。
- [匿名举报被刷] → IP+游记 24 小时去重 + 每 IP 每日 5 条；NAT 后多人共 IP 可能误伤，阈值保守。
- [IP 哈希仍可用于关联] → 只在 Redis 短期存在，不落库；日志不记录原始 IP。
- [快照包含用户文本] → 注销时清空；举报记录本身保留用于统计与审计。
- [举报后作者改稿] → 快照与 `note_updated_at` 保留证据，管理端提示内容已修改。
- [采纳时游记已删除] → 只关闭举报，不尝试恢复或下架；前端明确展示“作者已删除”。
- [归档顺序冲突] → `user-feedback` 必须先归档，否则 `platform/persistence` 与 `account/lifecycle` 的增量会互相覆盖。

## Migration Plan

1. 先归档 `user-feedback`，确认 `feedback` 表与反馈匿名化进入主规格。
2. 执行 Flyway `V20__note_report.sql`（纯新增表）。
3. 发布后端接口，再发布前端页面与分享页举报入口。
4. 回滚：后端回滚到旧版本即可，`note_report` 表保留不影响旧代码；如需彻底回滚，手工 `DROP TABLE note_report`（会丢失举报数据，需先备份）。

## Open Questions

- 举报量增大后是否需要管理员查看审计或独立举报详情页，取决于实际使用情况。
- 是否需要把作者历史被采纳举报数展示到用户管理页，后续可单独调整。