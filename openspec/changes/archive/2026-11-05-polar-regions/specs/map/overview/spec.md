## ADDED Requirements

### Requirement: Polar regions on the world map
世界地图 MUST 在同一张底图中展示南极洲与北极地区，渲染方式与普通国家区域一致：

- 南极洲 MUST 以多边形区域显示在地图底部。
- 北极地区 MUST 以 84°N–90°N 的极冠区域显示在地图顶部，且 MUST NOT 覆盖格陵兰、俄罗斯、加拿大等既有陆地。
- 底图纬度范围 MUST 扩展到 -90°~90°，主地图 MUST 自动缩放以容纳极地区域。
- 两个区域 MUST 支持三态着色、悬浮显示中文名与计数、点击打开对应国家抽屉。
- 新增极地区域 MUST NOT 修改其他国家的边界、归属或称谓。

#### Scenario: Antarctica is visible
- **WHEN** 用户打开世界地图
- **THEN** 南极洲作为可点击区域显示在地图底部

#### Scenario: Arctic is visible
- **WHEN** 用户打开世界地图
- **THEN** 北极地区作为极冠区域显示在地图顶部，不遮挡其他陆地

#### Scenario: Polar regions open drawers
- **WHEN** 用户点击南极洲或北极地区
- **THEN** 分别打开“南极洲”或“北极地区”的国家抽屉

#### Scenario: Polar regions follow three-state colors
- **WHEN** 用户在南极洲记录行程或游记
- **THEN** 南极洲按同一规则显示黄色或绿色，其余国家不受影响

#### Scenario: Other boundaries are unchanged
- **WHEN** 地图加载极地区域
- **THEN** 其他国家的边界与归属保持原样