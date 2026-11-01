## Context

见 `proposal.md`。抽屉现有数据结构已包含 trips 与 ungrouped，本次只改渲染与文案。

## Decisions

1. 每个 `trip` 渲染一张卡片：名称、日期范围、行程数、游记数、花费；整卡可点击进入 `/trips/:id`。
2. 行程数/游记数由 `trip.cities` 下的数组长度汇总得出，无需新接口。
3. 未归组仅在 `itineraries + notes + expenses` 任一非空时渲染，显示计数与花费，不展示明细。
4. 文案“旅行”统一替换为“归档”，覆盖抽屉、归档详情、记账筛选与表单标签。

## Risks / Trade-offs

- **抽屉不能再直接点开单条行程** → 需先进归档详情；这是本次明确选择。
- **文案替换遗漏** → 用 grep 检查用户可见字符串。

## Migration Plan

纯前端改动，构建后发布。

## Open Questions

无。
