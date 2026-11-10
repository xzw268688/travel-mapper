## MODIFIED Requirements

### Requirement: Trip recluster migration
系统 MUST 提供仅管理员可调用的一次性重算能力：按用户与国家、30 天窗口重新聚类行程，合并旧归档并重挂游记/消费；未归组的游记/消费 MUST 匹配已有归档或新建归档；删除不再被引用的空归档。重算 MUST 返回处理的行程数、合并的归档数与删除的空归档数。

#### Scenario: Recluster merges close trips
- **WHEN** 管理员对存在两次相隔 20 天行程的国家触发重算
- **THEN** 两次归档合并为一次，相关行程、游记与消费指向同一 `tripId`

#### Scenario: Orphan records get an archive
- **WHEN** 重算时存在未归组的消费或游记
- **THEN** 它们按日期归入匹配归档，匹配不到则新建归档

#### Scenario: Unreferenced trips are removed
- **WHEN** 重算后有归档不再关联任何行程、游记或消费
- **THEN** 该归档被删除，接口返回删除数量

#### Scenario: Normal user cannot recluster
- **WHEN** 普通用户调用重算接口
- **THEN** 系统返回 `4002`

## REMOVED Requirements

### Requirement: Automatic grouping for notes and expenses
**Reason**: 该需求规定“匹配不到就保留未归组”，与新规则“匹配不到就新建归档”冲突。
**Migration**: 无需数据迁移；部署后调用 `POST /api/admin/trips/recluster` 补齐历史未归组。

## ADDED Requirements

### Requirement: Archive creation and grouping for notes and expenses
游记/消费关联行程时 MUST 自动继承该行程的 `trip_id`。未关联行程时，系统 MUST 按日期自动归类：消费使用 `expense_date`、游记使用创建时间；记录日期与已有归档区间的前后距离 ≤30 天时 MUST 归入距离最小的归档。匹配不到时 MUST 新建归档，初始区间为该记录日期单日。系统 MUST NOT 要求用户手动选择归档。

#### Scenario: Linked note inherits trip
- **WHEN** 用户发布游记并关联某条行程
- **THEN** 游记的 `trip_id` 自动等于该行程的 `trip_id`

#### Scenario: Expense inside a trip is grouped
- **WHEN** 用户记账日期落在一段归档区间内
- **THEN** 该消费自动归入该归档

#### Scenario: Note shortly after the trip is grouped
- **WHEN** 游记创建时间在归档结束后 30 天内
- **THEN** 游记归入该归档

#### Scenario: Note before the trip is grouped
- **WHEN** 用户先记账，10 天后才保存对应行程
- **THEN** 消费与行程归入同一归档，不产生两个相邻归档

#### Scenario: No matching archive creates one
- **WHEN** 用户在没有归档的国家写游记或记账
- **THEN** 系统新建归档，区间为该记录日期单日

#### Scenario: No manual selection is required
- **WHEN** 用户创建游记或消费
- **THEN** 界面不出现归档选择项，系统自行归类

