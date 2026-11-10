## MODIFIED Requirements

### Requirement: Itinerary save
保存行程 MUST 校验：标题 ≤50 字；`endDate >= startDate`；天数等于 `days.length`；每天城市属于该国家且已启用；每天 0–10 个景点；全行程 `MANUAL` 景点总数 ≤30；`guides` 最多 3 条且 URL 合法；同一天内 `sortOrder` 按数组顺序写入。每个景点 MUST 接收并保存 `name`、`description`、`suggestedDuration` 以及 `category`、`ticketPrice`、`highlights`、`bestTime`、`nearestStation`、`reservationRequired`、`area`、`warningNotes` 扩展字段；扩展字段长度超出上限时 MUST 截断保存，MUST NOT 导致整单保存失败；扩展字段缺失 MUST 存为空值。保存成功 MUST 落库、点亮地图为黄色，并返回 `countryStatusAfter`。

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

#### Scenario: Extended spot fields are persisted across all sources
- **WHEN** 保存请求中的 AI 或 MANUAL 景点带有扩展字段
- **THEN** 这些字段按景点逐条写入，详情接口可原样读回

#### Scenario: Overlong extended field is truncated
- **WHEN** 某个扩展字段超过长度上限
- **THEN** 该字段按上限截断保存，其余景点与行程正常落库

### Requirement: Itinerary detail
行程详情 MUST 只返回本人未删除行程；响应 MUST 包含旅行信息、国家名、标题、日期、`status`、`expired`、`guides`、`preparations`、按天景点、`manualSpotCount` 和城市停用标记；每个景点 MUST 返回 `name`、`description`、`suggestedDuration` 与八个扩展字段；扩展字段为空的景点 MUST 返回空值而非缺字段；非本人或不存在的行程 MUST 统一返回 `2003`。

#### Scenario: Owner can read detail
- **WHEN** 用户读取自己的行程
- **THEN** 返回完整行程结构，含每天 `dayId`、`date`、`cityCode` 与景点 `spotId`

#### Scenario: Other user's itinerary is hidden
- **WHEN** 用户读取他人行程
- **THEN** 系统返回 `2003`，不区分“不存在”与“无权”

#### Scenario: Disabled city is flagged
- **WHEN** 行程中某天的城市已被停用
- **THEN** 详情仍正常展示，并返回 `cityDisabled=true`

#### Scenario: Extended fields are always present in the response shape
- **WHEN** 用户读取一条变更前保存、尚无扩展字段的行程
- **THEN** 每个景点仍返回全部扩展字段，值为空

### Requirement: Manual spot CRUD
已保存行程 MUST 支持新增、编辑、删除手动景点；新增 MUST 标记 `source=MANUAL` 并在达到 30 时返回 `2006`；编辑 MUST NOT 改变 `source`；删除 MUST 重新紧凑排序；操作 MUST NOT 消耗 AI 额度或改变地图状态。新增与编辑 MUST 接受并保存全部八个扩展字段，长度超限 MUST 截断；编辑任一扩展字段 MUST NOT 改变 `source`。

#### Scenario: Add manual spot
- **WHEN** 用户在已保存行程的某天添加景点
- **THEN** 景点以 `source=MANUAL` 保存，返回最新 `manualSpotCount`

#### Scenario: Limit reached
- **WHEN** 行程已有 30 个手动景点时再次添加
- **THEN** 系统返回 `2006`，不新增景点

#### Scenario: Delete spot compacts order
- **WHEN** 用户删除一天中间的景点
- **THEN** 剩余景点 `sortOrder` 连续，不出现空位

#### Scenario: Editing extended fields keeps source and billing
- **WHEN** 用户修改某个 AI 景点的门票或避雷备注
- **THEN** 该景点 `source` 保持原值，不消耗 AI 额度，地图状态不变

#### Scenario: Overlong extended field is truncated on edit
- **WHEN** 用户提交超过长度上限的扩展字段
- **THEN** 系统按上限截断保存，不返回错误