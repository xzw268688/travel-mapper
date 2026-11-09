## Why

系统按「对外运营、卡密站外售卖」定位后，管理端缺少运营必需的可观测与处置能力：看不到用户全貌、无法精确调整 VIP、卡密只有单张状态没有批次与兑换统计、审计日志只能写不能看、内容下架只能手输游记 ID；同时城市「删除」是高风险操作，「官方链接维护」在未投入内容运营时占用菜单位。

## What Changes

- **用户详情与 VIP 调整**：新增用户详情接口与抽屉，展示额度、VIP、兑换记录与内容数量；新增「设置 VIP 到期日」操作（设为过去即收回），保留现有的按天数调整；均写审计。
- **卡密统计与兑换记录**：`card_key` 增加可选 `price_cny`（V18 迁移）；生成卡密可填售价；新增批次统计（生成/已用/未用/作废/估算收入）与兑换记录查询。
- **审计日志查看**：新增只读列表接口与「审计日志」页面，支持按时间、动作、操作人筛选，展开查看 `detail`。
- **内容管理重做**：新增管理员游记列表（标题、作者、国家城市、可见性、下架状态、时间，不含正文），支持筛选与列表内下架/恢复，替换手输 ID。
- **城市维护**：前端移除「删除」按钮，仅保留停用/启用；删除接口保留不动。
- **导航**：隐藏「官方链接维护」菜单入口，路由、接口与用户端回退逻辑保留；「管理」新增「审计日志」。
- **非目标**：运营看板、举报入口、公告与邮件模板、汇率自动同步、站内支付与订单。

## Capabilities

### New Capabilities

- `admin/audit-log`: 管理员查看审计日志的能力（筛选、分页、详情展示）。

### Modified Capabilities

- `admin/users`: 新增用户详情与 VIP 到期日精确调整要求。
- `billing/card-key`: 新增卡密售价、批次统计与兑换记录要求。
- `admin/moderation`: 新增管理员游记列表要求（元信息筛选、列表内下架/恢复）。
- `ui/branding`: 「管理」菜单新增审计日志、移除官方链接入口。

## Impact

- 数据库：`V18__card_key_price.sql`（`price_cny DECIMAL(10,2) NULL`）。
- 后端：`AdminUserController/Service`、`QuotaService`（设置 VIP 到期日）、`AdminCardController/CardKeyService`、`AdminNoteController/NoteService`、`AuditLogService`、新增审计查询；DTO 若干。
- 前端：`AdminUsersView`、`AdminCardsView`、`AdminNotesView`、`AdminCitiesView`、新增 `AdminAuditView`、`App.vue`、路由、`api/admin.ts`。
- 文档：`TravelMapper API  V1.0.md` 增补管理端接口。
- 测试：管理端 E2E 用例更新；后端单元/集成测试覆盖统计、VIP 到期日与审计筛选。