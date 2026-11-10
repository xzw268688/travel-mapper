## ADDED Requirements

### Requirement: Drawer trip status groups
国家抽屉 MUST 按旅行的有效状态分组展示：

- `IN_PROGRESS` → “进行中的旅行”分组，仅当存在进行中的旅行时显示；
- `PLANNED` → “计划中的旅行”分组；
- `COMPLETED` → “已完成的旅行”分组。

手动标记的旅行 MUST 显示“手动标记”标签；自动判断为计划中且已过期的旅行 MUST 显示“已过期”标签。抽屉 MUST NOT 单独展示未加入旅行汇总；所有旅行卡片 MUST 使用有效状态，而不是仅使用持久化的手动状态。

#### Scenario: In-progress group appears
- **WHEN** 某国家存在今天正处于起止日期内的旅行
- **THEN** 抽屉显示“进行中的旅行”分组，该旅行不在“计划中的旅行”或“已完成的旅行”中

#### Scenario: Manual tag is shown
- **WHEN** 旅行状态由用户手动设置
- **THEN** 卡片显示“手动标记”标签与“恢复自动”按钮

#### Scenario: Expired tag is shown
- **WHEN** 自动判断为计划中且结束日期已过、也没有游记/消费
- **THEN** 卡片显示“已过期”标签

#### Scenario: Ungrouped summary is removed
- **WHEN** 抽屉中存在未加入旅行的历史数据
- **THEN** 抽屉不显示“另有 N 条未加入旅行”提示，历史数据迁移后以未命名旅行卡片展示

#### Scenario: Zero statistics are hidden
- **WHEN** 旅行只有行程、没有游记和消费
- **THEN** 卡片只显示“行程 N”，不显示“游记 0”“花费 ¥0.00”

#### Scenario: Missing dates are hidden
- **WHEN** 旅行没有任何可用日期（包括由记录日期补出的日期）
- **THEN** 卡片不显示日期占位行