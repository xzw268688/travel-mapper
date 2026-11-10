## Context

项目现有内容表（游记、反馈、举报）使用软删除与 `created_at/updated_at`；管理员写操作通过 `AuditLogService` 留痕。地图首页是登录后的主入口，适合承载公告横幅。参见 `proposal.md` 与 `specs/announcements/spec.md`。

## Goals / Non-Goals

**Goals**

- 管理员可独立完成公告的草稿、发布、下线与删除。
- 用户端横幅轻量、不打扰，能查看全部并逐条记录已读。
- 生效判定实时计算，不依赖定时任务。

**Non-Goals**

- 不做富文本、图片、受众定向、定时任务、站内信/邮件推送、按用户查看阅读明细。

## Decisions

### 两张表：公告与已读关系

`announcement` 保存标题、正文、类型、置顶、状态、开始/结束时间、创建人与软删除标记。`announcement_read` 以 `(announcement_id, user_id)` 唯一键记录已读时间。软删除只影响公告可见性，已读关系保留用于统计。替代方案把已读时间放在用户表只能做到“最近阅读”，无法逐条判断。

### 生效窗口实时计算

生效条件：`status='PUBLISHED'`、`deleted=0`、`publish_at <= now OR publish_at IS NULL`、`expire_at > now OR expire_at IS NULL`。管理端列表展示真实状态；`DRAFT/OFFLINE` 不参与用户查询。这样不需要定时任务，也不会出现状态漂移。

### 用户端一次返回生效公告

`GET /api/announcements/active` 返回当前生效公告（最多 20 条）及每条 `read` 标记与总未读数。公告正文较小，直接在列表返回，避免额外的详情请求；`POST /api/announcements/{id}/read` 幂等写入已读。横幅使用「置顶优先、`publish_at` 倒序」的第一条，并提供「共 N 条 / 未读 N」入口打开全部。

### 阅读率口径

管理端列表使用 `LEFT JOIN` 聚合已读人数；阅读率分母为当前 `role=USER AND status='NORMAL' AND deleted=0` 的用户数。分母为 0 时阅读率显示 0。

### 审计与权限

管理端接口走 `/api/admin/announcements/**`（`AdminInterceptor` 自动鉴权）；创建、更新、发布、下线、删除分别写 `ANNOUNCEMENT_*` 审计，详情与列表不写。用户端接口走 `/api/announcements/**`，需登录。

### 纯文本正文

正文按纯文本保存与展示，保留换行；前端使用 `white-space: pre-wrap`。不解析 HTML，避免 XSS 与清洗成本。

## Risks / Trade-offs

- [`announcement_read` 随用户×公告增长] → 唯一键避免重复；公告数量有限，按需清理已删除公告的已读记录可后续补充。
- [公告横幅打扰用户] → 仅展示一条，已读后可在「查看全部」中回看；不强制弹窗。
- [时区导致开始/结束时间偏差] → 沿用项目 `Asia/Shanghai` 配置与 `LocalDateTime`。
- [软删除后仍保留正文] → 管理端不可见；如需彻底清除可后续增加物理清理任务。

## Migration Plan

1. 执行 `V23__announcements.sql` 新增两张表（只增不改）。
2. 部署后端：提供管理端与用户端接口。
3. 部署前端：管理菜单与首页横幅。
4. 回滚时前端隐藏入口；新表为增量，不影响既有功能。

## Open Questions

无。