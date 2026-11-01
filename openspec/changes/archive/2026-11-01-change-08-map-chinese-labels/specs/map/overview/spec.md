## ADDED Requirements

### Requirement: Chinese country names on the map
世界地图的悬浮提示与悬停高亮标签 MUST 显示中文国家名。名称解析 MUST 按以下优先级：目录中该国家的中文名 → 内置中英对照表中的中文名 → 底图原始名称。展示 MUST NOT 因缺少中文映射而报错或留空。系统 MUST NOT 因该展示需求调整任何边界、归属或地区称谓。

#### Scenario: Lit country shows catalog Chinese name
- **WHEN** 用户将鼠标移到有行程或游记的国家
- **THEN** 提示与该国高亮标签显示目录中的中文名，并保持行程/游记/总计口径

#### Scenario: Unlit country shows translated Chinese name
- **WHEN** 用户将鼠标移到没有业务数据的国家
- **THEN** 提示显示中文名与“暂无记录”

#### Scenario: Missing translation falls back to source name
- **WHEN** 底图要素在对照表中没有对应中文名
- **THEN** 提示与标签回退显示底图原始名称，不报错

#### Scenario: Boundaries are not altered
- **WHEN** 用户查看地图
- **THEN** 国家边界与归属完全来自底图，系统不因显示中文名而修改或新增边界

#### Scenario: Fallback list keeps working
- **WHEN** 底图加载失败并降级为国家列表
- **THEN** 列表仍显示中文国家名并可打开详情