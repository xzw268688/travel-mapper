## MODIFIED Requirements

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

## REMOVED Requirements

### Requirement: Manual grouping for notes and expenses
**Reason**: 归组改为按“国家 + 时间”自动完成，用户不再手动选择旅行。
**Migration**: 无需迁移；未指定 `tripId` 的请求按自动规则归类，历史数据通过 `POST /api/admin/trips/recluster` 重算。

## ADDED Requirements

### Requirement: Trip recluster migration
系统 MUST 提供仅管理员可调用的一次性重算能力：按用户与国家、30 天窗口重新聚类行程，合并旧旅行并重挂游记/消费，删除不再被引用的空旅行。重算 MUST 返回处理的行程数、合并的旅行数与删除的空旅行数。

#### Scenario: Recluster merges close trips
- **WHEN** 管理员对存在两次相隔 20 天旅行的国家触发重算
- **THEN** 两次旅行合并为一次，相关行程、游记与消费指向同一 `tripId`

#### Scenario: Unreferenced trips are removed
- **WHEN** 重算后有旅行不再关联任何行程、游记或消费
- **THEN** 该旅行被删除，接口返回删除数量

#### Scenario: Normal user cannot recluster
- **WHEN** 普通用户调用重算接口
- **THEN** 系统返回 `4002`

### Requirement: Automatic grouping for notes and expenses
游记/消费关联行程时 MUST 自动继承该行程的 `trip_id`。未关联行程时，系统 MUST 自动归类：消费按 `expense_date` 落在旅行日期区间内；游记按 `created_at` 落在旅行区间内或结束后 30 天内；命中多个时取时间最近的一次。无法命中时 MUST 保存为未归组（`trip_id = NULL`）。系统 MUST NOT 要求用户手动选择旅行。

#### Scenario: Linked note inherits trip
- **WHEN** 用户发布游记并关联某条行程
- **THEN** 游记的 `trip_id` 自动等于该行程的 `trip_id`

#### Scenario: Expense inside a trip is grouped
- **WHEN** 用户记账日期落在某次旅行区间内且未指定旅行
- **THEN** 该消费的 `trip_id` 自动指向该旅行

#### Scenario: Note shortly after the trip is grouped
- **WHEN** 游记创建时间在旅行结束后 30 天内
- **THEN** 游记归入该旅行

#### Scenario: Unmatched data stays ungrouped
- **WHEN** 消费日期或游记创建时间不匹配任何旅行
- **THEN** 数据保存为未归组，不报错

#### Scenario: No manual selection is required
- **WHEN** 用户创建游记或消费
- **THEN** 界面不出现旅行选择项，系统自行归类
