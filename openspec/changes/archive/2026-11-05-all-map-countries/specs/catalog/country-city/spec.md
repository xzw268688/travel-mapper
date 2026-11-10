## ADDED Requirements

### Requirement: World map country coverage
国家目录 MUST 覆盖世界地图中全部有名称的地理区域（当前底图 215 个唯一名称），每个区域 MUST 对应一条启用国家记录，包含 `code`、中文名、英文名与大洲（无法确定时大洲可为空）。

`name_en` MUST 与底图 feature 的英文名一致，使地图可以按名称解析国家代码，不依赖额外的映射文件。没有标准 ISO 3166-1 代码的区域 MUST 使用不与 ISO 冲突的两字母保留码（当前为 `X1` 锡亚琴冰川、`X2` 北塞浦路斯）。

新增国家 MUST 以启用状态提供，使国家列表、国家抽屉与 AI 国家攻略可以直接使用。系统 MUST NOT 因该覆盖需求修改底图边界、归属或地区称谓。

#### Scenario: Every named map region has a catalog entry
- **WHEN** 校验脚本读取底图全部 feature 名称
- **THEN** 每个有名称的区域都能在国家目录中找到对应记录

#### Scenario: Newly covered country opens an empty drawer
- **WHEN** 用户点击原有目录中没有的德国
- **THEN** 抽屉正常打开，返回德国名称、灰色状态与空汇总

#### Scenario: Regions without ISO codes use reserved codes
- **WHEN** 用户查看锡亚琴冰川或北塞浦路斯
- **THEN** 它们分别以 `X1`、`X2` 收录，并可以正常打开抽屉

#### Scenario: Existing countries stay unchanged
- **WHEN** 执行新增国家迁移
- **THEN** 原有国家的中文名、英文名、状态与排序保持不变

#### Scenario: Map boundaries are not altered
- **WHEN** 用户查看世界地图
- **THEN** 边界、归属与地区称谓完全来自底图，不因国家目录扩展而改变