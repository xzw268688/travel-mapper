## ADDED Requirements

### Requirement: Guide links display
规划页的生成草稿与已保存行程详情 MUST 在存在攻略链接时展示「参考攻略」区域，按接口返回顺序列出链接标题，并 MUST 支持在新标签页打开。链接缺失或为空数组时 MUST NOT 展示该区域。展示行为 MUST NOT 影响行程生成、保存、编辑、额度或地图状态。

#### Scenario: Generated draft shows guide links
- **WHEN** 生成响应中的 `guides` 包含至少一条链接
- **THEN** 规划页展示「参考攻略」区域并列出这些链接

#### Scenario: Saved itinerary shows guide links
- **WHEN** 用户打开一条保存了攻略链接的行程
- **THEN** 详情页展示「参考攻略」区域

#### Scenario: Empty guides hide the section
- **WHEN** 生成响应或行程详情的 `guides` 为空
- **THEN** 页面不展示「参考攻略」区域

#### Scenario: Opening a guide does not leave the app flow
- **WHEN** 用户点击某条攻略链接
- **THEN** 链接在新标签页打开，当前页面状态保持不变