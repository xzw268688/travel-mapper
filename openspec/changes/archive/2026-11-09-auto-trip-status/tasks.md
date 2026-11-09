## 1. 数据库与迁移

- [x] 1.1 新增 `V15__trip_status_auto.sql`：为 `trip` 添加 `status_manual`，按“与自动完成判断不一致才保留手动”的规则回填历史状态，并软删除现有空旅行；在干净库与已有库执行，确认字段、标记与清理结果正确
- [x] 1.2 在 V15 中用存储过程迁移现有 `trip_id IS NULL` 的行程/游记/消费：按用户+国家创建“未命名旅行 · yyyy-MM”，迁移记录并删除临时过程；确认迁移后无孤儿记录、名称与日期正确

## 2. 后端有效状态

- [x] 2.1 新增 `TripStatusResolver`，实现手动优先、进行中/计划中/已完成、过期、记录日期补全规则；用单元测试覆盖所有分支
- [x] 2.2 `Trip` 实体增加 `statusManual`；`TripView`、`TripDetailView`、抽屉 `TripGroup`、时间线节点增加 `effectiveStatus`、`statusManual`、`expired`；确认编译通过
- [x] 2.3 `TripService.list/detail` 查询增加记录与日期聚合，调用 Resolver 填充有效状态；集成测试断言三态与过期
- [x] 2.4 `MapService.drawer` 的旅行分组返回有效状态与手动/过期标记；集成测试覆盖计划中/进行中/已完成抽屉分组数据
- [x] 2.5 `ItineraryService.timeline` 的行程节点与兜底旅行节点改用有效状态；确认时间线集成测试通过

## 3. 手动状态与删除行为

- [x] 3.1 `PUT /api/trips/{id}/status` 设置 `statusManual=true`；新增 `DELETE /api/trips/{id}/status` 恢复自动并返回重新计算的状态；测试手动完成、手动计划、恢复自动
- [x] 3.2 `DELETE /api/trips/{id}?force=true` 改为创建“未命名旅行 · yyyy-MM”并迁移挂载数据，返回 `newTripId`/`newTripName`/迁移数量；测试有数据与无数据两种删除
- [x] 3.3 新增 `TripService.deleteIfEmpty`：行程/游记/消费删除后若旅行无内容则自动软删除；相关删除响应返回 `tripDeleted`；测试最后一条内容删除与仍有内容两种路径
- [x] 3.4 DTO 增加 `effectiveStartDate`/`effectiveEndDate`：优先旅行日期，其次游记/消费记录日期；确认计划/详情/抽屉返回正确

## 4. 前端

- [x] 4.1 `api/trips.ts`、`api/itinerary.ts` 增加有效状态、手动标记、过期字段与恢复自动请求；确认类型检查通过
- [x] 4.2 `MapView` 抽屉改为“进行中的旅行 / 计划中的旅行 / 已完成的旅行”三组，进行中仅在有数据时显示；确认分组正确
- [x] 4.3 旅行卡显示“手动标记”“已过期”标签；手动状态显示“恢复自动”，调用新接口；确认按钮行为正确
- [x] 4.4 移除抽屉中的 `ungroupedNote` 与“另有 N 条未加入旅行”提示；确认历史数据迁移后以未命名旅行卡片展示
- [x] 4.5 旅行详情页展示有效状态与手动/过期标记，并提供恢复自动；时间线节点使用有效状态；确认页面一致
- [x] 4.6 旅行详情页固定显示四个统计框（0 也显示），抽屉卡片只显示非零项；没有有效日期时隐藏日期行；确认单项统计不再被拉伸
- [x] 4.7 删除最后一条内容后根据 `tripDeleted` 提示“旅行已无内容，已自动清理”并离开详情页；确认交互正确
- [x] 4.8 运行前端 `npm run build`，确认无类型错误
- [x] 4.9 给“手动标记”和“恢复自动”增加悬浮/触碰提示，说明手动优先与恢复自动的含义；确认提示文案与交互生效

## 5. 测试与验收

- [x] 5.1 新增/扩展后端集成测试：出发前消费、旅行中、结束后完成、过期计划、无日期同天完成、未来记录、手动覆盖、恢复自动、强制删除、孤儿迁移、空旅行自动清理；确认全部通过
- [x] 5.2 运行 `CatalogIntegrationTest`、`MapIntegrationTest`、`TripIntegrationTest`、时间线相关测试，确认无回归
- [x] 5.3 启动前后端，截图确认三种分组、手动/过期标签、恢复自动按钮、未命名旅行与无“另有 N 条未加入旅行”提示

## 6. 文档与规格

- [x] 6.1 更新 API 文档、README、本地开发说明：三种有效状态、恢复自动接口、删除旅行新行为、未命名旅行
- [x] 6.2 运行 `openspec validate auto-trip-status --strict`，确认规格与任务格式通过