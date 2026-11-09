# announcements Specification

## Purpose
让管理员发布维护通知与平台公告，让登录用户在地图首页看到当前生效的公告，并逐条记录已读状态。公告支持置顶、生效窗口与阅读统计，用于维护通知、功能介绍与运营告知。

## Requirements

### Requirement: Announcement management

管理员 MUST 能创建、编辑、预览、发布、下线与软删除公告。公告 MUST 包含标题、纯文本正文（保留换行）、类型（通知/公告）、是否置顶、开始时间与结束时间。状态 MUST 为 `DRAFT`、`PUBLISHED` 或 `OFFLINE`。公告删除 MUST 为软删除，并 MUST 保留已读记录。所有变更 MUST 写入审计 `ANNOUNCEMENT_CREATE`、`ANNOUNCEMENT_UPDATE`、`ANNOUNCEMENT_PUBLISH`、`ANNOUNCEMENT_OFFLINE` 或 `ANNOUNCEMENT_DELETE`。

#### Scenario: Admin creates a draft
- **WHEN** 管理员填写标题与正文并保存草稿
- **THEN** 公告以 `DRAFT` 状态保存，用户端不可见

#### Scenario: Admin publishes an announcement
- **WHEN** 管理员发布草稿
- **THEN** 公告状态变为 `PUBLISHED`，并按开始/结束时间对用户生效

#### Scenario: Soft delete keeps read records
- **WHEN** 管理员删除一条已读公告
- **THEN** 公告不再出现在管理列表与用户端，已读记录保留

### Requirement: Announcement effective window

公告 MUST 仅在 `status=PUBLISHED`、开始时间已到（或为空）且结束时间未过（或为空）时对用户生效。生效判定 MUST 基于查询时间实时计算，MUST NOT 依赖定时任务修改状态。

#### Scenario: Future announcement is hidden
- **WHEN** 公告已发布但开始时间在未来
- **THEN** 用户端不展示该公告

#### Scenario: Expired announcement is hidden
- **WHEN** 公告结束时间已过
- **THEN** 用户端不再展示，但管理端仍可查看

### Requirement: User announcement banner

用户端 MUST 在地图首页展示当前生效公告。存在多条时 MUST 按「置顶优先、开始时间倒序」展示最新一条，并 MUST 提供「共 N 条 / 未读 N」入口查看全部公告与详情。公告正文 MUST 以纯文本展示并保留换行。

#### Scenario: Pinned announcement wins
- **WHEN** 同时存在置顶与非置顶的生效公告
- **THEN** 首页横幅展示置顶公告

#### Scenario: View all announcements
- **WHEN** 用户点击「共 N 条」入口
- **THEN** 打开当前生效公告列表，并可查看每条详情

#### Scenario: No active announcement
- **WHEN** 没有任何生效公告
- **THEN** 首页不显示公告横幅

### Requirement: Announcement read tracking

系统 MUST 按用户与公告逐条记录已读。用户打开公告详情时 MUST 标记该公告已读；已读记录 MUST 幂等。用户 MUST 能查询当前生效公告的未读数量。已读接口 MUST 需登录。

#### Scenario: Opening detail marks read
- **WHEN** 用户打开一条未读公告的详情
- **THEN** 系统新增该用户与该公告的已读关系，未读数减一

#### Scenario: Marking read twice is idempotent
- **WHEN** 用户重复打开同一条公告
- **THEN** 不新增重复已读记录，也不报错

#### Scenario: Anonymous cannot mark read
- **WHEN** 未登录请求调用标记已读接口
- **THEN** 系统返回 `1006`

### Requirement: Announcement read statistics

管理员列表 MUST 展示每条公告的已读人数与阅读率。阅读率 MUST 以当前未注销普通用户数为分母；公告详情 MUST 返回相同口径的统计。

#### Scenario: Admin sees read counts
- **WHEN** 管理员查看公告列表
- **THEN** 每条公告展示已读人数与阅读率

### Requirement: Announcement access control

管理端接口 MUST 仅对 `ADMIN` 开放；用户端公告接口 MUST 仅对登录用户开放。用户 MUST NOT 能看到草稿、已下线、未开始或已结束的公告。

#### Scenario: Normal user cannot manage announcements
- **WHEN** 普通用户调用公告管理接口
- **THEN** 系统返回 `4002`

#### Scenario: Draft is invisible to users
- **WHEN** 公告仍为草稿
- **THEN** 用户端列表与横幅都不展示
