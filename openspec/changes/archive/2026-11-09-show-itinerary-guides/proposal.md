## Why

管理员配置「攻略搜索」后，后端生成行程时能正常返回最多 3 条攻略链接（实测成功），但规划页与行程详情页都没有渲染 `guides`，用户配置后看不到任何效果，容易误以为搜索配置无效。

## What Changes

- 规划页在生成的草稿存在攻略链接时，展示「参考攻略」卡片，列出标题并支持新标签页打开。
- 已保存行程的编辑/详情页在 `guides` 非空时展示同样的「参考攻略」区块。
- 无攻略链接时不展示该区域，不影响行程生成、保存、编辑或额度。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `itinerary/management`: 新增攻略链接展示要求。

## Impact

- 前端：`PlanView.vue`、`ItineraryEditView.vue`。
- 后端与接口：无变更（`guides` 已存在于生成响应与行程详情）。
- 测试：新增临时浏览器验证，确认两个页面均展示攻略链接；Playwright 全量回归通过。