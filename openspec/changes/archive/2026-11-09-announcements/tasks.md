## 1. 数据库与实体

- [x] 1.1 新增 `V23__announcements.sql`：`announcement`（标题、正文、类型、置顶、状态、开始/结束时间、软删除）与 `announcement_read`（公告+用户唯一），验证方式：Flyway 迁移成功且索引存在
- [x] 1.2 新增 `Announcement`、`AnnouncementRead` 实体与 Mapper，验证方式：后端编译通过

## 2. 管理端

- [x] 2.1 新增 `GET/POST/PUT/DELETE /api/admin/announcements`：列表（状态/类型/关键字/分页）、详情、创建、更新、软删除，验证方式：集成测试覆盖 CRUD 与软删除
- [x] 2.2 新增发布/下线接口：发布设置 `PUBLISHED` 与开始时间，下线设置 `OFFLINE`，验证方式：状态变更且用户端可见性同步
- [x] 2.3 列表与详情返回已读人数与阅读率（分母为当前正常用户数），验证方式：构造已读后统计正确
- [x] 2.4 所有变更写审计 `ANNOUNCEMENT_*`，验证方式：集成测试断言 action 与 target

## 3. 用户端

- [x] 3.1 新增 `GET /api/announcements/active`：按生效窗口返回公告、`read` 标记与未读数，验证方式：草稿/未来/过期公告不出现
- [x] 3.2 新增 `POST /api/announcements/{id}/read`：幂等写入已读，未登录返回 `1006`，验证方式：重复调用不产生重复记录
- [x] 3.3 生效公告按置顶优先、开始时间倒序排序，验证方式：集成测试断言顺序

## 4. 前端

- [x] 4.1 新增 `AdminAnnouncementsView`：列表、筛选、创建/编辑弹窗、预览、发布/下线、删除、阅读率展示，验证方式：`npm run build` 通过
- [x] 4.2 地图首页新增公告横幅与「共 N 条 / 未读 N」入口，打开详情时调用标记已读；无生效公告时不显示，验证方式：页面可展示与关闭
- [x] 4.3 `App.vue`「管理」菜单新增通知公告、路由与 `api/announcements.ts`，验证方式：管理员可见并可进入

## 5. 测试与文档

- [x] 5.1 新增后端公告集成测试：CRUD、发布/下线、生效窗口、已读幂等、未读数、权限与审计
- [x] 5.2 更新 `TravelMapper API  V1.0.md` 与 `TravelMapper DB  V1.0.md`：接口、字段、表结构与审计动作
- [x] 5.3 后端全量测试与前端构建通过，验证方式：`mvn test`（或项目等价命令）与 `npm run build` 退出码为 0