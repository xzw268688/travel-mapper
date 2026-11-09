# Itinerary Manual-planning Specification

## Purpose

为 `/plan` 提供不依赖 AI 的手动规划模式：用户自行选择国家、城市、天数与日期，在空白草稿中逐天添加景点，并复用现有保存流程创建归档，全程不调用 LLM、不消耗额度。

## Requirements

### Requirement: Planning mode selection
`/plan` MUST 提供「AI 生成」与「手动规划」两种模式，默认选中「AI 生成」。切换模式 MUST 保留各模式最近一次的表单输入；若当前存在未保存草稿，切换前 MUST 二次确认，确认后 MUST 清空当前草稿并回到表单状态。

#### Scenario: Default mode remains AI
- **WHEN** 用户首次打开 `/plan`
- **THEN** 默认处于「AI 生成」模式，生成入口与偏好输入可见

#### Scenario: Switch clears unsaved draft after confirmation
- **WHEN** 用户已有未保存草稿并切换到另一模式
- **THEN** 系统二次确认；确认后草稿被清空，取消则保持当前模式与草稿

#### Scenario: Switch without draft is immediate
- **WHEN** 用户没有草稿时切换模式
- **THEN** 立即切换，不弹出确认

### Requirement: Manual draft creation
手动模式 MUST 要求选择国家、1–5 个属于该国家且已启用的城市、1–15 天以及开始日期。创建操作 MUST 只在本地生成草稿，MUST NOT 调用 AI、MUST NOT 扣减额度、MUST NOT 写入数据库。草稿 MUST 包含与天数相同的自然日，`dayIndex` 从 1 连续递增，日期从开始日期起逐日递增；每个自然日 MUST 默认分配到首个所选城市且景点为空。

#### Scenario: Draft has one empty day per requested day
- **WHEN** 用户选择 3 天并创建手动行程
- **THEN** 草稿包含第 1–3 天，日期连续，每天景点为空

#### Scenario: First selected city is the default
- **WHEN** 用户选择东京和大阪并创建手动行程
- **THEN** 每一天默认城市为东京，用户可逐天改为大阪

#### Scenario: Invalid input is rejected locally
- **WHEN** 未选城市、天数不在 1–15 或开始日期为空
- **THEN** 前端提示错误，不创建草稿

#### Scenario: No AI charge
- **WHEN** 用户创建手动草稿
- **THEN** 不发起 LLM 请求，额度与当日已用次数不变

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

### Requirement: Manual draft save
手动草稿 MUST 通过现有 `POST /api/itinerary/save` 保存，`guides` 为空数组、`preparations` 为空值，景点 `source=MANUAL`。保存 MUST 走与 AI 行程相同的校验、旅行自动归组、地图点亮与标题规则；保存成功 SHOULD 展示与 AI 保存一致的归档入口。手动保存 MUST NOT 因没有景点而失败（每天允许 0 个景点）。

#### Scenario: Empty manual itinerary can be saved
- **WHEN** 用户创建 2 天草稿但未添加任何景点并保存
- **THEN** 保存成功，创建归档并将该国家点亮为计划中

#### Scenario: Save reuses trip grouping
- **WHEN** 用户手动保存一条与已有归档日期相近的同国行程
- **THEN** 行程按现有 30 天窗口规则归入归档或创建新归档

#### Scenario: Manual spot limit rejects save
- **WHEN** 保存请求中 `MANUAL` 景点总数超过 30
- **THEN** 后端返回 `2006`，整单不落库

#### Scenario: Quota is not consumed
- **WHEN** 手动草稿保存成功
- **THEN** 用户额度与当日免费次数保持不变
