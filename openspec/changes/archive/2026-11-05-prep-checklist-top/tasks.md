## 1. 顶部行前准备

- [x] 1.1 新增 `PrepChecklist.vue`：10 项静态清单、时间节点、默认展开、完成进度、按国家保存 localStorage
- [x] 1.2 在 `PlanView` 顶部接入组件；删除旧的固定勾选“行前清单”；将下方 AI 行前卡片改名为“AI 行前提示”；确认页面结构和国家切换正常

## 2. 验收

- [x] 2.1 运行前端 `npm run build` 与 `openspec validate prep-checklist-top --strict`，确认通过
- [x] 2.2 实际打开规划页，确认顶部显示 10 项清单、可折叠、勾选后刷新仍保留；切换国家后进度独立