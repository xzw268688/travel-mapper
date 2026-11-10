## 1. 移除入口

- [x] 1.1 删除 `PlanView.vue` 中的“已有行程 ID”输入框、“编辑已保存行程”按钮、`openItineraryId` 状态与 `openSavedItinerary` 函数
- [x] 1.2 运行前端 `npm run build` 与 `openspec validate remove-itinerary-id-input --strict`，确认通过

## 2. 验收

- [x] 2.1 打开规划页，确认 ID 输入框与按钮已消失，生成/手动规划按钮正常；从旅行详情与时间线仍可进入已保存行程