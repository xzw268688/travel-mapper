## Context

见 `proposal.md`。现状：`resolveForDate` 只向后匹配已有归档，匹配不到就保存为未归组；归档只能由行程保存创建。

## Decisions

1. `resolveForDate` 改为对称窗口：记录日期与归档区间距离 ≤30 天即命中，取距离最小、结束日期最晚的一次。
2. 新增 `resolveOrCreateForDate`：匹配不到时创建归档，`start = end = 记录日期`，名称“国家 · 年-月”。
3. 游记使用 `LocalDate.now()`（创建时间），消费使用 `expense_date`。
4. 重算时对未归组的游记/消费调用 `resolveOrCreateForDate`，历史数据也补齐。
5. 不新增游记日期字段，不恢复手动选择。

## Risks / Trade-offs

- **补写很久以前的游记会生成新归档** → 符合“不猜测”的原则，用户可后续通过日期修正。
- **±30 天可能把两次较近的短途合并** → 与行程归组规则一致，属可接受取舍。

## Migration Plan

1. 备份数据库。
2. 部署后调用 `POST /api/admin/trips/recluster` 补齐历史未归组。

## Open Questions

无。
