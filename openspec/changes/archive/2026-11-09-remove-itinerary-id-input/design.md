## Context

`PlanView.vue` 顶部有一个 `openItineraryId` 输入框和“编辑已保存行程”按钮，作用只是 `router.push('/itineraries/{id}')`。用户无法在界面中得知行程 ID，且正常入口已存在：

- 地图 → 国家抽屉 → 旅行卡片 → 旅行详情 → 行程条目
- 首页 → 时间线 → 行程卡片

## Goals / Non-Goals

**Goals:**

- 移除无意义的内部 ID 输入入口。
- 保持生成、手动规划与保存流程不变。

**Non-Goals:**

- 不新增已保存行程列表或选择器。
- 不改变任何后端接口。

## Decisions

### 删除输入框、按钮与相关状态

移除模板中的输入框和按钮，以及 `openItineraryId` ref 和 `openSavedItinerary()` 函数。用户仍可通过旅行详情和时间线进入行程编辑页。

备选：改成“已保存行程”下拉列表，需要新增行程列表数据与交互，超出本次清理范围。

## Risks / Trade-offs

- **习惯直接输 ID 的高级用户失去快捷入口** → 正常入口已覆盖，且 ID 从未在界面中展示。

## Migration Plan

1. 前端删除入口，随版本发布。
2. 无数据库或接口变更。

## Open Questions

（无）