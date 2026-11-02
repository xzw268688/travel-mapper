## ADDED Requirements

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
