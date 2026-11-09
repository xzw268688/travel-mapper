## MODIFIED Requirements

### Requirement: Archive status
每个旅行 MUST 具有有效状态 `PLANNED`、`IN_PROGRESS` 或 `COMPLETED`，并返回 `effectiveStatus`、`statusManual`、`expired`。有效状态按以下规则计算：

- 当 `statusManual=true` 时，有效状态 MUST 等于手动保存的状态，不再自动变化。
- 当 `statusManual=false` 时：
  - 旅行有游记或消费，且今天晚于旅行结束日期 → `COMPLETED`；
  - 今天在旅行起止日期内（含首尾） → `IN_PROGRESS`；
  - 没有旅行起止日期时，使用游记创建日期与消费日期的最早/最晚值作为有效起止：今天不早于最晚记录日期 → `COMPLETED`；今天在有效区间内但未到最后一天 → `IN_PROGRESS`；今天早于最早记录日期 → `PLANNED`；
  - 其余情况 → `PLANNED`。
- 只有 `statusManual=false`、有效状态为 `PLANNED`、结束日期已过且没有游记/消费时，`expired` MUST 为 `true`。

旅行所有者 MUST 能通过 `PUT /api/trips/{id}/status` 设置手动状态并标记 `statusManual=true`；通过 `DELETE /api/trips/{id}/status` 恢复自动判断并清除手动标记。非本人 MUST 返回 `2015`；非法状态 MUST 返回 `2001`。有效状态 MUST 在抽屉、旅行详情与时间线中一致展示。

#### Scenario: New archive is planned by default
- **WHEN** 系统自动创建旅行且没有游记或消费
- **THEN** 有效状态为 `PLANNED`，`statusManual=false`

#### Scenario: Owner marks an archive completed
- **WHEN** 用户把旅行状态改为 `COMPLETED`
- **THEN** 接口成功，`statusManual=true`，抽屉与旅行详情显示已完成

#### Scenario: Owner can revert
- **WHEN** 用户把已完成的旅行改回 `PLANNED`
- **THEN** 手动状态更新为 `PLANNED`，`statusManual=true`

#### Scenario: Other user cannot change status
- **WHEN** 用户尝试修改他人旅行状态
- **THEN** 系统返回 `2015`

#### Scenario: Pre-trip expense stays planned
- **WHEN** 出发前记录一笔酒店预订消费，今天早于旅行开始日期
- **THEN** 有效状态为 `PLANNED`，不会提前变成已完成

#### Scenario: Ongoing trip is in progress
- **WHEN** 今天在旅行起止日期内，且旅行中记录了消费或游记
- **THEN** 有效状态为 `IN_PROGRESS`，不会显示已完成

#### Scenario: Finished trip with records is completed
- **WHEN** 旅行结束日期已过，且旅行下有至少一篇游记或一笔消费
- **THEN** 有效状态为 `COMPLETED`

#### Scenario: Expired plan without records
- **WHEN** 旅行结束日期已过，但只有行程、没有游记和消费
- **THEN** 有效状态保持 `PLANNED`，`expired=true`

#### Scenario: Manual completion without records
- **WHEN** 用户去了但不写游记/不记账，手动把旅行标记为已完成
- **THEN** 有效状态为 `COMPLETED`，`statusManual=true`，后续日期变化不影响它

#### Scenario: Manual planned overrides records
- **WHEN** 旅行有游记，但用户手动把状态改为 `PLANNED`
- **THEN** 有效状态保持 `PLANNED`，`statusManual=true`

#### Scenario: Reset to automatic
- **WHEN** 用户对已手动标记的旅行执行恢复自动
- **THEN** `statusManual=false`，有效状态按当前日期与游记/消费重新计算

#### Scenario: Record-only trip completed same day
- **WHEN** 旅行没有起止日期，用户今天写游记或记账
- **THEN** 使用记录日期作为有效日期，当天即视为 `COMPLETED`，不显示进行中

#### Scenario: Future record-only trip stays planned
- **WHEN** 旅行没有起止日期，但记录的消费日期在未来
- **THEN** 今天早于有效开始日期，有效状态为 `PLANNED`

### Requirement: Trip deletion and detaching
删除旅行 MUST 只软删除旅行本身，不得级联删除挂载数据；存在挂载行程/游记/消费且 `force` 不为 `true` 时 MUST 返回 `2017`。

`force=true` 时，系统 MUST 在同一事务内创建一个新的“未命名旅行 · yyyy-MM”（月份取原旅行挂载记录中最早的行程开始日期、游记创建日期或消费日期），把原行程、游记、消费的 `trip_id` 迁移到新旅行，再软删除原旅行。新旅行 MUST `statusManual=false`，其有效状态按自动规则计算，并在响应中返回 `newTripId`、`newTripName` 与迁移数量。

没有挂载数据时 MUST 直接软删除旅行，不创建新旅行。

#### Scenario: Deletion without force is blocked
- **WHEN** 旅行下仍有挂载数据且请求 `force=false`
- **THEN** 系统返回 `2017`，不删除旅行、不修改挂载数据

#### Scenario: Forced deletion detaches instead of deleting
- **WHEN** 用户确认后以 `force=true` 删除旅行
- **THEN** 原行程、游记、消费全部保留并迁移到新的“未命名旅行”，原旅行被软删除

#### Scenario: Unnamed trip uses earliest record month
- **WHEN** 被删除旅行的最早记录日期是 2022-09
- **THEN** 新旅行名称为“未命名旅行 · 2022-09”

#### Scenario: Empty trip deletes directly
- **WHEN** 旅行没有任何挂载数据
- **THEN** 无需 `force=true` 即可软删除，且不创建未命名旅行

### Requirement: Derived trip date range
`trip.startDate/endDate` MUST 是关联未删除行程的最小/最大值；行程保存、编辑日期、增删天、删除行程后 MUST 重算。没有关联行程时，日期区间 MUST 置为 NULL；若此时旅行也没有游记和消费，旅行 MUST 被自动软删除。对外展示时，若旅行没有行程日期但存在游记/消费，系统 MAY 使用记录日期补出有效展示日期；仍没有日期时前端 MUST 隐藏日期行。

#### Scenario: Deleting the earliest itinerary shifts start date
- **WHEN** 用户删除旅行中日期最早的一条行程
- **THEN** 旅行 `startDate` 更新为剩余行程的最早日期

#### Scenario: No itineraries leaves dates empty
- **WHEN** 旅行下已没有任何行程，但仍存在游记或消费
- **THEN** 旅行日期区间为 NULL，旅行仍存在，展示时使用记录日期补全或隐藏日期行

#### Scenario: Empty trip is removed after last itinerary
- **WHEN** 旅行下最后一条行程被删除，且没有游记和消费
- **THEN** 旅行日期区间为空，旅行被自动软删除

## ADDED Requirements

### Requirement: Empty trip cleanup
当旅行的未删除行程、游记、消费数量全部为 0 时，系统 MUST 自动软删除该旅行。清理 MUST 在删除最后一条内容的同一事务内完成；删除接口 MUST 返回 `tripDeleted=true`，前端 MUST 提示并离开已删除旅行的详情页。升级迁移 MUST 清理现有空旅行。空旅行 MUST NOT 出现在国家抽屉、旅行列表或时间线中。

#### Scenario: Last content deletion removes the trip
- **WHEN** 用户删除某旅行的最后一条行程、游记或消费
- **THEN** 旅行被软删除，接口返回 `tripDeleted=true`

#### Scenario: Trip with remaining content stays
- **WHEN** 用户删除一条内容但旅行仍有其他行程、游记或消费
- **THEN** 旅行保留，`tripDeleted=false`

#### Scenario: Migration removes existing empty trips
- **WHEN** 升级时数据库中已存在没有任何内容的旅行
- **THEN** 这些旅行被软删除，不再出现在列表中

### Requirement: Orphan records migration to unnamed trips
升级迁移 MUST 把现有 `trip_id` 为 NULL 的行程、游记和消费按“用户 + 国家”归入“未命名旅行 · yyyy-MM”（月份取该组最早记录日期），并保持所有记录可访问。迁移后的新旅行 MUST `statusManual=false`，按自动规则计算有效状态。正常业务创建流程 MUST NOT 再产生未归组数据。

#### Scenario: Orphan records are grouped
- **WHEN** 某用户某国家存在未归组行程、游记或消费
- **THEN** 迁移为该用户创建一个未命名旅行，并把该组记录全部迁移进去

#### Scenario: Orphan migration keeps records accessible
- **WHEN** 迁移完成后用户打开国家抽屉
- **THEN** 原未归组记录以未命名旅行卡片展示，不再出现未加入旅行提示

#### Scenario: New records always get a trip
- **WHEN** 用户创建行程、游记或消费且没有显式选择旅行
- **THEN** 系统自动创建或归入旅行，不写入 `trip_id IS NULL`