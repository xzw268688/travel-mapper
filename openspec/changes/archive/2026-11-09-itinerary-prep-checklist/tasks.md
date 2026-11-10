## 1. 数据库与后端

- [x] 1.1 新增 `V16__itinerary_prep_checklist.sql`：放宽 `item_key`，增加标题/说明/提前天数/排序/自定义字段，回填旧 7 项；在干净库与已有库执行验证
- [x] 1.2 新增 `PrepChecklistTemplate`（10 项默认定义）并扩展 `ChecklistDtos.Item`；提供 `GET /api/prep/checklist/template`
- [x] 1.3 扩展 `ChecklistService.get`：合并默认 10 项与已保存勾选，追加自定义项按排序返回；测试默认项、勾选恢复、跨用户隔离
- [x] 1.4 扩展 `PUT /api/prep/checklist` 支持默认项与自定义项勾选；新增 `POST /items` 添加自定义项、`DELETE /items/{id}` 删除自定义项；测试增删、默认项不可删除
- [x] 1.5 新增 `PUT /api/prep/checklist/batch` 批量保存：更新默认勾选、创建/更新自定义项、软删除未提交自定义项；测试新建行程保存后状态一致

## 2. 前端

- [x] 2.1 `api/prep.ts` 增加 10 项模板、完整 Item、自定义项增删、批量保存的类型与请求
- [x] 2.2 改造 `PrepChecklist.vue` 为受控组件：默认项不可删、自定义项可删、底部“+ 添加一项”（标题/天数/说明）、移除 localStorage
- [x] 2.3 `PlanView` 加载模板；在草稿卡片内、逐日安排之前渲染清单；保存行程成功后调用批量保存；新建规划时重置清单
- [x] 2.4 `ItineraryEditView` 按行程加载清单，勾选/新增/删除实时保存；放在编辑页顶部
- [x] 2.5 运行前端 `npm run build`，确认无类型错误

## 3. 测试与验收

- [x] 3.1 新增/扩展后端集成测试：模板、读取、勾选、自定义项增删、批量保存、旧数据迁移、跨用户隔离
- [x] 3.2 运行相关后端测试（清单、行程保存、数据迁移）与前端构建，确认无回归
- [x] 3.3 实际打开规划页：勾选、加自定义项、保存行程后重新打开行程编辑页，确认清单状态与自定义项仍在；删除自定义项生效

## 4. 文档与规格

- [x] 4.1 更新 API 文档与 README：10 项默认清单、自定义项、按行程保存、新接口
- [x] 4.2 运行 `openspec validate itinerary-prep-checklist --strict`，确认规格与任务格式通过