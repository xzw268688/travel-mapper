## Purpose

定义已保存行程的保存、查看、编辑、删除、重新生成、景点与天数管理规则，确保行程结构、地图点亮与旅行归组始终一致，且手动景点不因重新生成而丢失。

## ADDED Requirements

### Requirement: Itinerary save
保存行程 MUST 校验：标题 ≤50 字；`endDate >= startDate`；天数等于 `days.length`；每天城市属于该国家且已启用；每天 0–10 个景点；全行程 `MANUAL` 景点总数 ≤30；`guides` 最多 3 条且 URL 合法；同一天内 `sortOrder` 按数组顺序写入。保存成功 MUST 落库、点亮地图为黄色，并返回 `countryStatusAfter`。

#### Scenario: First save creates trip and lights map
- **WHEN** 用户首次保存一条日本行程且未传 `tripId`
- **THEN** 系统创建旅行、返回 `tripCreated=true` 与 `countryStatusAfter=YELLOW`

#### Scenario: Manual spot limit rejects whole save
- **WHEN** 保存请求中的 `MANUAL` 景点总数超过 30
- **THEN** 系统返回 `2006`，整单不落库

#### Scenario: Invalid city rejects save
- **WHEN** 某天的城市不属于该国家或已停用
- **THEN** 系统返回 `2002`，不保存行程

#### Scenario: Saving an existing trip updates its date range
- **WHEN** 行程挂载到已有旅行且日期超出原区间
- **THEN** 旅行日期区间按关联行程重算

### Requirement: Itinerary detail
行程详情 MUST 只返回本人未删除行程；响应 MUST 包含旅行信息、国家名、标题、日期、`status`、`expired`、`guides`、`preparations`、按天景点、`manualSpotCount` 和城市停用标记；非本人或不存在的行程 MUST 统一返回 `2003`。

#### Scenario: Owner can read detail
- **WHEN** 用户读取自己的行程
- **THEN** 返回完整行程结构，含每天 `dayId`、`date`、`cityCode` 与景点 `spotId`

#### Scenario: Other user's itinerary is hidden
- **WHEN** 用户读取他人行程
- **THEN** 系统返回 `2003`，不区分“不存在”与“无权”

#### Scenario: Disabled city is flagged
- **WHEN** 行程中某天的城市已被停用
- **THEN** 详情仍正常展示，并返回 `cityDisabled=true`

### Requirement: Itinerary basic info edit
编辑 MUST 支持修改标题、开始日期和结束日期；修改日期 MUST 按 `dayIndex` 平移所有 `day.date`；结束日期修改 MUST NOT 改变天数；编辑 MUST NOT 改变地图点亮状态；增删天或改当天城市 MUST 使用天数管理接口。

#### Scenario: Date shift moves all days
- **WHEN** 用户把开始日期后移一天
- **THEN** 每天日期按 `dayIndex` 同步后移，天数和景点不变

#### Scenario: Editing does not change map status
- **WHEN** 用户修改已保存行程的标题或日期
- **THEN** 该国点亮状态保持不变

#### Scenario: Changing length via edit is rejected
- **WHEN** 用户通过编辑接口尝试改变天数
- **THEN** 系统返回 `2001`，引导使用增删天接口

### Requirement: Itinerary deletion
删除行程 MUST 为软删除；MUST NOT 删除所属旅行；MUST 返回删除后该国的 `countryStatusAfter`（`GREEN`/`YELLOW`/`GRAY`）；删除后旅行日期区间 MUST 重算。

#### Scenario: Last itinerary turns country gray
- **WHEN** 用户删除某国最后一条行程且该国没有游记
- **THEN** 返回 `countryStatusAfter=GRAY`，地图恢复暗灰

#### Scenario: Trip remains after last itinerary is deleted
- **WHEN** 某旅行下的最后一条行程被删除
- **THEN** 旅行仍存在，日期区间变为空

### Requirement: Regeneration with manual spot merge
重新生成 MUST 更新原 `itineraryId` 与 `tripId`；`keepManualSpots=true` 时 MUST 在扣额度前预校验现有手动景点数，已超过 30 MUST 返回 `2006` 且不调 AI；合并 MUST 按同一 `dayIndex` 且同城市追加、同天异城市追加并提示、目标天不存在则放到第 1 天并提示；合并不增加 AI 额度消耗；地图状态不变。

#### Scenario: Same day and city appends silently
- **WHEN** 手动景点原属第 2 天东京，新行程第 2 天也是东京
- **THEN** 手动景点追加到第 2 天末尾，无提示

#### Scenario: City mismatch appends with warning
- **WHEN** 手动景点原属大阪，但新行程同一天是东京
- **THEN** 景点追加到该天并返回 `cityMismatch` 提示

#### Scenario: Fewer days moves to first day
- **WHEN** 手动景点原属第 5 天，但新行程只有 3 天
- **THEN** 景点追加到第 1 天并返回 `movedToFirstDay` 提示

#### Scenario: Pre-check prevents wasting quota
- **WHEN** 现有手动景点已达到 30 且用户选择保留
- **THEN** 系统在扣费前返回 `2006`，不调用 AI

#### Scenario: Do not keep overwrites manual spots
- **WHEN** 用户选择 `keepManualSpots=false`
- **THEN** 新行程全量覆盖，旧手动景点被删除

### Requirement: Manual spot CRUD
已保存行程 MUST 支持新增、编辑、删除手动景点；新增 MUST 标记 `source=MANUAL` 并在达到 30 时返回 `2006`；编辑 MUST NOT 改变 `source`；删除 MUST 重新紧凑排序；操作 MUST NOT 消耗 AI 额度或改变地图状态。

#### Scenario: Add manual spot
- **WHEN** 用户在已保存行程的某天添加景点
- **THEN** 景点以 `source=MANUAL` 保存，返回最新 `manualSpotCount`

#### Scenario: Limit reached
- **WHEN** 行程已有 30 个手动景点时再次添加
- **THEN** 系统返回 `2006`，不新增景点

#### Scenario: Delete spot compacts order
- **WHEN** 用户删除一天中间的景点
- **THEN** 剩余景点 `sortOrder` 连续，不出现空位

### Requirement: Spot move and reorder
系统 MUST 支持天内拖拽排序与“移动到第 X 天”；目标天 MUST 属于同一行程；移动 MUST NOT 改变 `source`、不消耗额度、不做上限校验；跨天拖拽手势不属于 V1。

#### Scenario: Move spot to another day
- **WHEN** 用户把景点移动到第 3 天指定位置
- **THEN** 景点出现在目标天，两个受影响天的顺序重新紧凑

#### Scenario: Target day from another itinerary is rejected
- **WHEN** 目标 `dayId` 属于其他行程
- **THEN** 系统返回 `2015` 或 `2016`

### Requirement: Day management
系统 MUST 支持新增一天、修改某天的日期/城市、删除某天；单条行程 MUST 保持 1–15 天；插入/删除 MUST 正确平移 `dayIndex` 且不触发唯一键冲突；日期 MUST 保持连续；修改城市 MUST NOT 删除该天景点；所有天数操作 MUST NOT 消耗 AI 额度或改变地图状态。

#### Scenario: Insert day shifts later days
- **WHEN** 用户在第 3 天位置插入一天
- **THEN** 原第 3 天及之后的天 `dayIndex` 加 1，新天景点为空

#### Scenario: Delete day moves later days forward
- **WHEN** 用户删除第 2 天
- **THEN** 该天及其景点被物理删除，原第 3 天变为第 2 天

#### Scenario: Cannot delete the last day
- **WHEN** 行程只剩 1 天时删除该天
- **THEN** 系统返回 `2001`，提示应删除整条行程

#### Scenario: Changing city keeps spots
- **WHEN** 用户把某天城市从东京改为京都
- **THEN** 该天已有景点原样保留，仅城市与日期更新

#### Scenario: Date range is recalculated
- **WHEN** 新增、删除或修改某天日期后
- **THEN** 行程与国家侧边抽屉中的日期范围同步更新

### Requirement: Row-level isolation for all itinerary operations
所有行程、天、景点、重新生成操作 MUST 校验资源属于当前用户；越权 MUST 返回 `2003`、`2015` 或 `2016`，不得暴露资源是否存在。

#### Scenario: Cross-user spot edit is hidden
- **WHEN** 用户编辑他人景点
- **THEN** 系统返回 `2016`

#### Scenario: Cross-user regeneration is hidden
- **WHEN** 用户重新生成他人行程
- **THEN** 系统返回 `2003`，不扣额度
