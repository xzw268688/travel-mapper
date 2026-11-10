# Prep Checklist Specification

## Purpose

为每条已保存行程提供固定七项行前清单，按用户、国家与行程持久化勾选状态，跨设备保持一致且不消耗 AI 额度。

## Requirements

### Requirement: Checklist read
读取清单 MUST 校验行程属于当前用户；响应 MUST 返回默认十项（固定顺序）以及该行程的自定义项（按排序值、创建顺序排列）。默认项未落库时 MUST 返回 `checked=false`；已落库项 MUST 返回保存状态。每项 MUST 包含 `itemKey`、标题、说明、提前天数、勾选状态、是否自定义与排序值。结果 MUST 按“用户 + 行程”隔离。

#### Scenario: Unchecked defaults are returned
- **WHEN** 用户首次打开某行程的清单
- **THEN** 默认十项全部返回且 `checked=false`

#### Scenario: Saved state is restored
- **WHEN** 用户勾选“签证”后刷新或换设备登录
- **THEN** 该项仍返回 `checked=true`

#### Scenario: Other user's checklist is hidden
- **WHEN** 用户 A 请求用户 B 的行程清单
- **THEN** 系统返回 `2003`

#### Scenario: Custom items are appended
- **WHEN** 行程存在自定义清单项
- **THEN** 默认十项在前，自定义项按排序值追加在后

### Requirement: Checklist toggle
勾选/取消 MUST 使用 upsert 语义，以 `(user_id, itinerary_id, item_key)` 唯一；重复勾选或取消 MUST 保持幂等；操作 MUST NOT 消耗额度、MUST NOT 影响地图点亮。默认项与自定义项 MUST 都支持勾选。

#### Scenario: Toggle persists
- **WHEN** 用户勾选“机票”
- **THEN** 系统写入 `checked=true` 并返回最新 `updatedAt`

#### Scenario: Repeated toggle is idempotent
- **WHEN** 用户连续两次提交相同的 `checked` 值
- **THEN** 结果一致，不产生重复行

#### Scenario: Checklist does not affect quota or map
- **WHEN** 用户勾选任意清单项
- **THEN** AI 额度与地图状态均不变

#### Scenario: Custom item can be checked
- **WHEN** 用户勾选自己添加的自定义项
- **THEN** 该项的勾选状态保存成功

### Requirement: Default checklist and custom items
系统 MUST 提供默认十项清单，顺序固定：签证（出发前 60 天）、机票（45 天）、住宿（30 天）、保险（30 天）、电话卡/eSIM（14 天）、换汇与银行卡（14 天）、转换头+插排（7 天）、App 准备（7 天）、必备物品（7 天）、入境卡与海关（1 天）。每项 MUST 包含说明。

用户 MUST 能在默认项之后添加自定义项，自定义项包含标题（1–64 字）、提前天数（0–365）和可选说明（≤255 字）。默认项 MUST NOT 删除；自定义项 MUST 可删除。

#### Scenario: All ten defaults are returned
- **WHEN** 用户打开任一行程的清单
- **THEN** 默认十项按固定顺序返回，并包含标题、说明和提前天数

#### Scenario: Custom item is appended
- **WHEN** 用户添加一个自定义准备项
- **THEN** 该项排在默认十项之后，并带有“自定义”标记

#### Scenario: Default item cannot be deleted
- **WHEN** 客户端尝试删除某个默认项
- **THEN** 系统拒绝删除，默认项保持不变

### Requirement: Custom checklist item management
系统 MUST 支持新增和删除自定义清单项。新增 MUST 校验行程属于当前用户、标题长度、提前天数与说明长度；生成的 `item_key` MUST 以 `CUSTOM_` 开头并保持唯一。删除 MUST 只允许自定义项，默认项删除 MUST 返回参数错误；其他用户的数据 MUST 返回 `2003`。

#### Scenario: Add custom item
- **WHEN** 用户在行程清单中添加“租车”并设置提前 10 天
- **THEN** 返回新自定义项，排序在默认项之后

#### Scenario: Delete custom item
- **WHEN** 用户删除自己添加的自定义项
- **THEN** 该项被删除，默认项不受影响

#### Scenario: Cannot delete default item
- **WHEN** 用户尝试删除“签证”默认项
- **THEN** 系统返回 `2001` 或 `2022`，默认项保留

#### Scenario: Other user's custom item is hidden
- **WHEN** 用户操作他人的行程清单
- **THEN** 系统返回 `2003`

### Requirement: Checklist batch save
系统 MUST 支持按行程批量保存清单状态，用于新建行程保存时一次性提交默认项与自定义项。批量保存 MUST 在同一事务内完成：更新默认项勾选状态、创建或更新自定义项、删除本次提交中不存在的自定义项；默认项 MUST 始终保留。

#### Scenario: Batch save after itinerary save
- **WHEN** 用户保存新行程并提交清单
- **THEN** 行程与清单同时保存，重新打开清单时状态一致

#### Scenario: Batch removes deleted custom items
- **WHEN** 批量提交的清单中不再包含某个自定义项
- **THEN** 该自定义项被删除，默认十项仍保留

### Requirement: Legacy checklist migration
升级迁移 MUST 把旧的七项勾选状态保留到新的默认项：`VISA`、`FLIGHT`、`HOTEL`、`INSURANCE`、`CURRENCY`、`PLUG`、`SIM` MUST 继续对应同名默认项；新增的 `APPS`、`PACKING`、`CUSTOMS` MUST 默认未勾选。

#### Scenario: Legacy checked states are preserved
- **WHEN** 升级前用户已勾选“签证”和“机票”
- **THEN** 升级后这两项仍为已勾选

#### Scenario: New defaults start unchecked
- **WHEN** 升级后首次打开清单
- **THEN** App 准备、必备物品、入境卡与海关三项为未勾选
