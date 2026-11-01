## 1. 自动归组规则

- [x] 1.1 `TripService` 增加行程窗口匹配（同国家、间隔 ≤30 天、取最小间隔）并替换 `attachOrCreate` 的默认分支；集成测试覆盖 29/30/31 天边界与多匹配
- [x] 1.2 增加按日期解析旅行：消费用 `expense_date`，游记用 `created_at` 且允许结束后 30 天；测试覆盖命中、多命中取最近、未命中返回空
- [x] 1.3 `ExpenseService.create` 与 `NoteService.create` 在未指定 `tripId` 时自动归类；测试覆盖自动归组与未归组

## 2. 前端移除手动选择

- [x] 2.1 `NotesView` 移除旅行下拉与 `tripId` 入参；`PlanView` 移除旅行选择与 `tripId` 入参；构建通过
- [x] 2.2 `TripDetailView` 的“写游记/规划行程”不再带 `tripId`；记账筛选保留
- [x] 2.3 E2E 更新：新建游记/保存行程后自动出现在对应旅行，无需选择

## 3. 历史数据重算

- [x] 3.1 新增 `POST /api/admin/trips/recluster`（ADMIN）：重新聚类、合并旅行、重挂游记/消费、删除空旅行；返回统计
- [x] 3.2 集成测试覆盖合并、空旅行删除、普通用户 `4002`
- [x] 3.3 新增 `scripts/maintenance/recluster-trips.sh`：先备份再调用接口

## 4. 文档与验收

- [x] 4.1 更新 API、SRS、README 与本地开发文档
- [x] 4.2 运行后端全量测试、构建、E2E；在开发库执行一次重算并核对抽屉统计
