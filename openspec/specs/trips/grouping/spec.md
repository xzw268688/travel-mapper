# Trips Grouping Specification

## Purpose

定义同一国家多次旅行的归组方式，使行程、游记与消费可以挂载到具体旅行或进入“未归组”，并保证旅行日期区间随关联行程自动维护。

## Requirements

### Requirement: Automatic trip creation on first itinerary save
保存行程且未指定 `tripId` 时，系统 MUST 在同一用户、同一国家下查找已有旅行：若新行程与某次旅行的日期间隔 ≤30 天，MUST 归入间隔最小的那次；否则 MUST 新建旅行，默认名称为“国家 · yyyy-MM”，初始日期取该行程起止日期。匹配与保存 MUST 在同一事务内完成。

#### Scenario: First save creates a trip
- **WHEN** 用户第一次保存一条日本行程
- **THEN** 系统创建新旅行，名称形如“日本 · 2025-06”，行程挂载到该 `tripId`

#### Scenario: Close trips share one group
- **WHEN** 用户在日本已有一条 6 月 1–5 日行程，再保存一条 6 月 20–24 日行程
- **THEN** 新行程归入同一旅行，不新建

#### Scenario: Second trip in same country is separate
- **WHEN** 新行程与最近一次旅行的日期间隔为 31 天以上
- **THEN** 系统新建旅行，两次旅行互不覆盖

#### Scenario: Nearest trip wins when several match
- **WHEN** 新行程与两次已有旅行的间隔都 ≤30 天
- **THEN** 归入间隔更小的那次旅行

### Requirement: Attaching to an existing trip
挂载已有旅行时，系统 MUST 校验旅行属于当前用户（否则 `2015`）且旅行国家与数据国家一致（否则 `2001`）；挂载成功后 MUST 重算该旅行的起止日期为关联行程的最小/最大日期。

#### Scenario: Cross-country attachment is rejected
- **WHEN** 用户把日本行程挂到泰国旅行
- **THEN** 系统返回 `2001`，行程不挂载

#### Scenario: Other user's trip is hidden
- **WHEN** 用户尝试把行程挂到他人的 `tripId`
- **THEN** 系统返回 `2015`，不泄露该旅行是否存在

#### Scenario: Date range expands after attaching
- **WHEN** 一条日期更早的行程挂入已有旅行
- **THEN** 旅行的 `startDate` 更新为该更早日期

### Requirement: Trip rename
旅行名称 MUST 可由本人修改，长度为 1–30 字；空名或超长返回 `2001`；改名 MUST NOT 修改任何已关联行程、游记或消费。

#### Scenario: Rename affects only the trip label
- **WHEN** 用户把“日本 · 2025-06”改为“日本关西行”
- **THEN** 旅行名称更新，关联数据与 `trip_id` 保持不变

#### Scenario: Empty name is rejected
- **WHEN** 用户提交空名称或超过 30 字的名称
- **THEN** 系统返回 `2001`，原名称保持不变

### Requirement: Derived trip date range
`trip.startDate/endDate` MUST 是关联未删除行程的最小/最大值；行程保存、编辑日期、增删天、删除行程后 MUST 重算；没有关联行程时 MUST 置为 NULL，前端显示“无日期”。

#### Scenario: Deleting the earliest itinerary shifts start date
- **WHEN** 用户删除旅行中日期最早的一条行程
- **THEN** 旅行 `startDate` 更新为剩余行程的最早日期

#### Scenario: No itineraries leaves dates empty
- **WHEN** 旅行下已没有任何行程
- **THEN** 旅行日期区间为 NULL，旅行仍存在

### Requirement: Trip deletion and detaching
删除旅行 MUST 只软删除旅行本身，不得级联删除挂载数据；存在挂载行程/游记/消费且 `force` 不为 `true` 时 MUST 返回 `2017`；`force=true` 时 MUST 在同一事务内把这些数据的 `trip_id` 置为 NULL，使其进入“未归组”。

#### Scenario: Deletion without force is blocked
- **WHEN** 旅行下仍有挂载数据且请求 `force=false`
- **THEN** 系统返回 `2017`，不删除旅行、不修改挂载数据

#### Scenario: Forced deletion detaches instead of deleting
- **WHEN** 用户确认后以 `force=true` 删除旅行
- **THEN** 旅行被软删除，挂载的行程、游记、消费全部保留且 `trip_id` 为空

#### Scenario: Empty trip deletes directly
- **WHEN** 旅行没有任何挂载数据
- **THEN** 无需 `force=true` 即可软删除

### Requirement: Trip listing
系统 MUST 按国家返回本人未删除旅行，包含名称、日期区间、行程数、游记数、消费数、消费小计和创建时间；排序 MUST 为 `startDate` 倒序，无日期时按 `createdAt` 倒序。

#### Scenario: Trip list shows counts
- **WHEN** 用户查询日本旅行列表
- **THEN** 每条旅行返回其挂载的行程数、游记数、消费数和 CNY 小计

#### Scenario: Other users' trips never appear
- **WHEN** 任意用户查询旅行列表
- **THEN** 结果只包含当前用户的旅行

### Requirement: Trip detail aggregation
系统 MUST 提供按旅行聚合的详情接口，返回旅行概览、汇总、行程列表、游记列表与最近消费。行程与游记 MUST 返回全部未删除记录；消费 MUST 只返回最近 10 条，并额外返回消费笔数与 CNY 合计。非本人或不存在旅行 MUST 返回 `2015`。聚合口径 MUST 与地图抽屉一致（同一旅行、同一用户、排除已删除数据）。

#### Scenario: Owner opens trip detail
- **WHEN** 用户请求本人某次旅行的详情
- **THEN** 返回旅行名称、国家、日期区间、行程数、游记数、消费笔数、CNY 合计，以及行程与游记完整列表

#### Scenario: Recent expenses are capped
- **WHEN** 某次旅行有 30 条消费
- **THEN** 响应最多返回最近 10 条消费，同时 `expenseCount=30` 且合计包含全部消费

#### Scenario: Other user's trip is hidden
- **WHEN** 用户请求他人的旅行详情
- **THEN** 系统返回 `2015`，不泄露旅行是否存在

#### Scenario: Unconfigured currency is reported
- **WHEN** 旅行中存在未配置汇率的消费
- **THEN** 汇总返回未配置币种列表，合计只包含已折算部分

### Requirement: Trip-aware entry points
从旅行详情页进入录入页时 MUST 带上旅行上下文：记账页 MUST 按该旅行筛选；游记页 MUST 预选该旅行并在保存时写入 `trip_id`；行程页 MUST 支持将新行程归入该旅行。用户 MUST 仍可在录入页改选其他旅行或“未归组”。

#### Scenario: Expense page filters by trip
- **WHEN** 用户从旅行详情页点击“记一笔”
- **THEN** 记账页自动按该旅行筛选列表与汇总

#### Scenario: Note creation keeps the trip
- **WHEN** 用户从旅行详情页进入游记创建并选择该旅行后保存
- **THEN** 游记的 `trip_id` 为该旅行，出现在旅行详情页

#### Scenario: Itinerary can join an existing trip
- **WHEN** 用户在行程规划页选择“归到现有旅行”并保存
- **THEN** 新行程挂到该旅行下，而不是新建旅行

#### Scenario: User can still choose ungrouped
- **WHEN** 用户在录入页把旅行选择清空
- **THEN** 数据保存为未归组，不强制关联

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

### Requirement: Archive status
每个归档 MUST 具有状态 `PLANNED` 或 `COMPLETED`，默认 `PLANNED`。归档所有者 MUST 能手动切换状态；非本人 MUST 返回 `2015`；非法状态 MUST 返回 `2001`。状态 MUST 持久化，并在地图抽屉与归档详情中一致展示。

#### Scenario: New archive is planned by default
- **WHEN** 系统自动创建归档
- **THEN** 其状态为 `PLANNED`

#### Scenario: Owner marks an archive completed
- **WHEN** 用户把归档状态改为 `COMPLETED`
- **THEN** 接口成功，抽屉「已完成」分组与归档详情都显示已完成

#### Scenario: Owner can revert
- **WHEN** 用户把已完成的归档改回 `PLANNED`
- **THEN** 状态更新，归档回到「计划中」分组

#### Scenario: Other user cannot change status
- **WHEN** 用户尝试修改他人归档状态
- **THEN** 系统返回 `2015`

### Requirement: Timeline view
系统 MUST 提供时间线视图，返回本人全部未删除归档，按起始日期正序排列（无日期时使用创建日期），节点 MUST 包含归档名称、国家名、日期区间、状态、行程数、游记数、消费数与花费。前端 MUST 允许在地图与时间线之间切换，点击节点进入归档详情。

#### Scenario: Timeline is chronological
- **WHEN** 用户打开时间线
- **THEN** 归档按日期从早到晚排列

#### Scenario: Node opens archive detail
- **WHEN** 用户点击某个时间线节点
- **THEN** 打开对应归档详情页

#### Scenario: Other users' archives are excluded
- **WHEN** 用户查看时间线
- **THEN** 只包含本人的归档
