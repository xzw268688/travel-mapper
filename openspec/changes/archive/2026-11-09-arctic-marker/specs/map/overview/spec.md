## MODIFIED Requirements

### Requirement: Polar regions on the world map
世界地图 MUST 在同一张底图中展示南极洲与北极地区：

- 南极洲 MUST 以多边形区域显示在地图底部。
- 北极地区 MUST 以地图顶部约 88°N、0° 处的可点击点标记显示，MUST NOT 再以填充区域或横贯地图顶部的色带出现。
- 北极标记 MUST 始终显示“北极地区”文字标签。
- 北极标记颜色 MUST 跟随三态：无记录灰色、有行程黄色、有游记绿色；悬浮 MUST 显示中文名与行程/游记/总计数；点击 MUST 打开“北极地区”抽屉。
- 底图纬度范围 MUST 继续容纳南极洲（-90°~90°），主地图自动缩放。
- 极地区域的展示 MUST NOT 修改其他国家的边界、归属或称谓。

#### Scenario: Antarctica is visible
- **WHEN** 用户打开世界地图
- **THEN** 南极洲作为可点击区域显示在地图底部

#### Scenario: Arctic is visible
- **WHEN** 用户打开世界地图
- **THEN** 北极地区以带“北极地区”标签的点标记显示在地图顶部，地图上不再出现横贯顶部的色带

#### Scenario: Polar regions open drawers
- **WHEN** 用户点击南极洲区域或北极点标记
- **THEN** 分别打开“南极洲”或“北极地区”抽屉

#### Scenario: Polar regions follow three-state colors
- **WHEN** 用户在南极洲或北极地区记录行程或游记
- **THEN** 对应区域或标记按同一规则显示黄色或绿色，其余国家不受影响

#### Scenario: Arctic marker shows counts on hover
- **WHEN** 用户悬浮在北极点标记上
- **THEN** 提示显示“北极地区”与行程/游记/总计数

#### Scenario: Other boundaries are unchanged
- **WHEN** 地图加载极地展示
- **THEN** 其他国家的边界与归属保持原样