## Why

规划页顶部的“已有行程 ID”输入框要求用户手动输入内部行程 ID 才能打开编辑页，普通用户既拿不到 ID，也不知道它的用途；通过旅行卡片或时间线进入已保存行程已经足够。

## What Changes

- 删除规划页的“已有行程 ID”输入框和“编辑已保存行程”按钮。
- 删除对应的 `openItineraryId` 状态与跳转函数。
- 不改变生成、手动规划、保存流程或任何后端接口。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

（无；仅移除开发者快捷入口，`skip_specs: true`）

## Impact

- 前端：`PlanView.vue`。
- 后端/数据库：无变更。
- 文档/规格：无需更新；用户仍可从旅行卡片与时间线进入已保存行程。