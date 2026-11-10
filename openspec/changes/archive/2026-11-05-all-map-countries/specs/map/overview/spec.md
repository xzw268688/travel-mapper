## MODIFIED Requirements

### Requirement: Map client interaction
地图前端 MUST 根据统计结果渲染 `GREEN`、`YELLOW` 与默认暗灰；悬浮 MUST 展示国家名与行程数/游记数/总次数。

点击底图上任意有名称的区域 MUST 打开对应国家抽屉，无论该国家是否有业务数据。若区域无法解析为国家代码，前端 MUST 给出“该区域暂未收录”提示，而不是静默无反应。

移动端 MUST 支持点击打开抽屉，并（P1）支持双指缩放与单指拖拽且不与页面滚动冲突。

#### Scenario: Colors match statistics
- **WHEN** 地图加载完成
- **THEN** 有游记的国家显示绿色，仅有行程的国家显示黄色，其余显示暗灰

#### Scenario: Click opens the correct drawer
- **WHEN** 用户点击日本
- **THEN** 打开日本抽屉并展示该国的旅行与未归组数据

#### Scenario: Clicking an unlit country opens its drawer
- **WHEN** 用户点击没有任何行程或游记的德国
- **THEN** 打开德国抽屉，展示灰色状态、空汇总与 AI 国家攻略入口

#### Scenario: Unresolved region shows a prompt
- **WHEN** 点击的底图区域无法解析出国家代码
- **THEN** 页面提示“该区域暂未收录”，不静默忽略

#### Scenario: Mobile gestures are usable
- **WHEN** 用户在 iOS Safari 15+、Android Chrome 100+ 或微信内置浏览器中缩放/拖拽地图
- **THEN** 地图正常缩放或移动，页面滚动不被意外触发