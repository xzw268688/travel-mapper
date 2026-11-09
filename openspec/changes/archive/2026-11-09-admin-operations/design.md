## Context

见 `proposal.md`。已核实：`card_key` 已有 `batch_id`、`used_by`、`used_at`、`status` 与 `(batch_id,status)` 索引；`audit_log` 已按 `user_id/action/target` 建索引且写入时已用 `SensitiveDataMasker` 脱敏；VIP 按 `user.vip_expire_at` 判断，现有 `adjustVip` 仅支持按天累加；用户列表已返回额度与 VIP；内容下架接口已实现，但没有列表查询。

## Goals / Non-Goals

**Goals**
- 运营可在后台完成用户补偿、卡密对账、内容治理与操作追溯，无需改数据库。
- 新增查询均为只读，不改动现有业务写入路径。

**Non-Goals**
- 不做收入对账（售价为估算口径，不代表实际收款）。
- 不引入订单、支付、举报、公告与看板。
- 不删除城市删除接口，仅在前端隐藏入口。

## Decisions

### D1：卡密售价与统计
- `V18` 增加 `price_cny DECIMAL(10,2) NULL`；生成请求新增可选 `priceCny`，负数或超过两位小数返回 `2001`。
- 批次统计按 `batch_id` 分组，`GROUP BY batch_id, type, value, price_cny`，用条件聚合统计各状态数量；估算收入在服务层计算：`price == null ? null : price × usedCount`。
- 兑换记录查询 `status='USED'` 的卡密并关联 `user`，支持 `userId` 过滤；作废/未使用天然排除。
- 导出 CSV 在原有固定列后追加 `priceCny`，保持旧列顺序不变。

### D2：用户详情与 VIP 到期日
- 详情接口返回基础资料、`freeUsedToday`、`packBalance`、`vipExpireAt`、游记/行程/旅行数量与最近 10 条兑换记录；已注销用户只返回账号壳并跳过内容统计。
- 新增 `PUT /admin/users/{id}/vip` 接收 `expireAt`（可空）：直接写入 `vip_expire_at` 并写 `audit_log`（action `USER_VIP_SET`）。该操作不是天数增量，**不写 `quota_ledger`**，避免账本口径混乱；页面同时保留现有「按天数调整」入口。
- 设置为过去时间即收回 VIP；额度服务每次按当前时间判断，无需清缓存。

### D3：审计日志查看
- 新增 `GET /admin/audit`，参数 `action`、`userId`、`targetType`、`from`、`to`、`page`、`size`，按 `created_at DESC, id DESC` 分页。
- 直接返回已脱敏的 `detail` 字符串，前端展开显示；不提供写接口。

### D4：内容管理列表
- 新增 `GET /admin/notes`，参数 `keyword`（标题或作者）、`status`（`TAKEDOWN`/`NORMAL`）、`from`、`to`、`page`、`size`；返回标题、作者、国家城市、可见性、下架状态与时间，**不返回正文**。
- 列表操作复用现有 `PUT /admin/notes/{id}/takedown` 与 `/restore`；下架原因必填。

### D5：导航与移除
- `App.vue` 管理菜单新增「审计日志」，移除「官方链接」入口；`/admin/official-links` 路由保留。
- 城市维护移除删除按钮，仅保留停用/启用；删除 API 不动以兼容脚本与测试。

## Risks / Trade-offs

- [售价是估算而非实收] → 页面标注「估算收入（已用 × 售价）」，并允许不填售价。
- [管理员列表看到用户私密游记元信息] → 只返回元信息，不返回正文；满足治理需要且控制隐私暴露。
- [审计 detail 增长] → 分页查询 + 现有索引；只读无额外写入。
- [设置 VIP 到期日不进账本] → 以审计日志为准，避免伪造天数增量；按天调整仍走账本。

## Migration Plan

1. 发布前执行 `V18`（仅新增可空列，向后兼容）。
2. 部署后端新接口，再部署前端。
3. 回滚：旧代码忽略新列；新页面可独立回退。

## Open Questions

无。