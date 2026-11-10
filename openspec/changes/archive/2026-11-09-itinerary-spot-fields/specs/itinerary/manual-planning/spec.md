## MODIFIED Requirements

### Requirement: Manual draft editing
手动草稿 MUST 复用与 AI 草稿相同的编辑能力：逐天修改城市与日期、增加/删除天、添加/编辑/删除景点、天内排序与跨天移动。新增或编辑景点时 MUST 允许填写八个扩展字段（类别、门票、亮点、最佳时段、最近车站、需预约、区域、避雷备注），字段可全部留空。新增景点 MUST 标记为 `source=MANUAL`；每天最多 10 个景点，整个草稿手动景点总数最多 30 个。删除或移动天时 MUST 保持 `dayIndex` 连续并同步日期区间。

#### Scenario: Manually added spot is marked manual
- **WHEN** 用户在第 1 天添加景点
- **THEN** 该景点以 `source=MANUAL` 显示并随保存提交

#### Scenario: Day count stays within limits
- **WHEN** 草稿已有 15 天
- **THEN** 「增加一天」不可用并提示最多 15 天

#### Scenario: Per-day and total spot limits are enforced
- **WHEN** 某天已有 10 个景点或草稿已有 30 个手动景点
- **THEN** 前端阻止继续添加并给出提示

#### Scenario: Day removal keeps dates continuous
- **WHEN** 用户删除中间某天
- **THEN** 剩余天重新编号，日期从开始日期起重新连续排列

#### Scenario: Extended fields can be entered manually
- **WHEN** 用户手动添加景点并填写门票、需预约与避雷备注
- **THEN** 草稿保留这些字段并随保存提交

#### Scenario: Empty extended fields are allowed
- **WHEN** 用户只填写景点名称就添加
- **THEN** 扩展字段为空，草稿与保存均正常