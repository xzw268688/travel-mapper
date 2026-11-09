Triage: ready-for-agent

## Why

平台没有向用户发布维护通知、新功能介绍或活动告知的渠道，只能站外沟通。管理员需要一个可控的公告发布入口，用户需要在进入站点时看到当前生效的公告，并知道自己是否已读。

## What Changes

- 新增「通知公告」：管理端在「管理 → 通知公告」创建、编辑、预览、发布/下线、置顶与软删除公告。
- 公告字段：标题、纯文本正文（保留换行）、类型（通知/公告）、置顶、开始/结束时间；状态为草稿/已发布/已下线。
- 用户端：地图首页顶部公告横幅，按「置顶优先、时间倒序」展示最新一条，并显示「共 N 条 / 未读 N」入口；点击打开公告列表与详情，打开详情时标记已读。
- 新增 `announcement` 与 `announcement_read` 两张表（`V23__announcements.sql`），按用户逐条记录已读；公告软删除并保留已读关系。
- 管理端列表展示已读人数与阅读率；公告变更写审计 `ANNOUNCEMENT_CREATE / UPDATE / PUBLISH / OFFLINE / DELETE`。
- 生效判定：`status=PUBLISHED` 且当前时间落在开始/结束窗口内；不使用定时任务。

非目标：富文本、图片、受众定向（全体/VIP/指定用户）、定时发布任务、邮件或站内推送、按用户查看阅读明细。

## Capabilities

### New Capabilities

- `announcements`: 管理员发布与管理通知公告，用户查看生效公告并记录已读的能力。

### Modified Capabilities

- `ui/branding`: 「管理」菜单新增通知公告；顶栏新增「监控」管理员菜单。

## Impact

- 数据库：新增 `V23__announcements.sql`（`announcement`、`announcement_read`）。
- 后端：新增 `Announcement`、`AnnouncementRead` 实体与 Mapper、`AnnouncementService`、管理端与用户端 Controller、DTO。
- 前端：新增 `AdminAnnouncementsView`、地图首页公告横幅与公告弹窗；`App.vue`、路由、`api/admin.ts`、`api/announcements.ts`。
- 文档：API 与 DB 文档补充公告接口与表结构。
- 测试：公告 CRUD、发布/下线、软删除、用户生效列表、已读与未读数、权限与审计的集成测试。