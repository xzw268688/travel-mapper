# admin/reports Specification

## Purpose
为管理员提供不当游记举报的集中查看、证据核对、采纳下架与驳回能力，使内容治理可追踪、可审计，并与现有下架/恢复流程保持一致。

## Requirements

### Requirement: Report listing and statistics
管理员 MUST 能分页查询全部举报，并可按状态、原因分类、创建日期范围与关键词筛选；结果 MUST 按创建时间倒序。关键词 MUST 匹配举报说明、举报时标题快照、游记当前标题以及作者用户名/昵称。系统 MUST 提供待处理、已采纳、已驳回与总数的统计。举报人已注销时 MUST 显示「匿名访客」；作者已注销时 MUST 显示「已注销用户」。

#### Scenario: Filter by status and reason
- **WHEN** 管理员按 `status=PENDING` 与 `reason=ABUSE` 查询
- **THEN** 只返回同时满足条件的举报，按创建时间倒序

#### Scenario: Keyword matches snapshot and author
- **WHEN** 管理员以某关键词查询，该词出现在举报说明、标题快照或作者昵称中
- **THEN** 对应举报出现在结果中

#### Scenario: Statistics reflect current counts
- **WHEN** 管理员请求举报统计
- **THEN** 返回待处理、已采纳、已驳回与总数

#### Scenario: Deleted identities are anonymized
- **WHEN** 举报人或游记作者已注销
- **THEN** 管理端分别显示「匿名访客」与「已注销用户」

### Requirement: Report detail
管理员 MUST 能查看举报详情，包含原因、补充说明、举报时的标题与纯文本快照、举报时间、举报人摘要、游记当前摘要（标题、作者、可见性、下架/删除状态、当前更新时间）以及该作者历史被采纳举报数。举报不存在时 MUST 返回 `2032`。

#### Scenario: Detail includes evidence and context
- **WHEN** 管理员打开一条举报详情
- **THEN** 返回举报快照、举报人、游记当前状态与作者历史被采纳举报数

#### Scenario: Unknown report
- **WHEN** 管理员请求不存在的举报 ID
- **THEN** 系统返回 `2032`

### Requirement: Accept report and take down note
管理员 MUST 能采纳举报并填写下架原因（1–100 字）。采纳时系统 MUST 下架对应游记、撤销其全部分享令牌，并把该游记所有 `PENDING` 举报置为 `ACCEPTED`；若游记已被作者软删除，采纳 MUST 只将举报置为 `ACCEPTED`，不要求恢复或再次下架。采纳 MUST 记录处理人与处理时间，并写入 `REPORT_ACCEPT` 审计；游记下架本身继续写 `NOTE_TAKEDOWN` 审计。已处理举报 MUST NOT 重复处理，返回 `2001`。

#### Scenario: Accept takes down note and closes all pending reports
- **WHEN** 管理员采纳一条针对正常游记的举报并填写下架原因
- **THEN** 游记被下架、分享令牌全部撤销，该游记全部待处理举报变为已采纳

#### Scenario: Accept on deleted note only closes reports
- **WHEN** 管理员采纳一条针对已被作者删除游记的举报
- **THEN** 举报变为已采纳，游记保持删除状态

#### Scenario: Accept is audited
- **WHEN** 管理员采纳举报
- **THEN** `audit_log` 记录 `REPORT_ACCEPT`，包含举报 ID、游记 ID 与下架原因

#### Scenario: Already handled report is rejected
- **WHEN** 管理员再次处理已采纳或已驳回的举报
- **THEN** 系统返回 `2001`，状态不变

### Requirement: Reject report
管理员 MUST 能驳回举报而不改变游记状态，只把当前举报置为 `REJECTED`，记录处理人与处理时间并写入 `REPORT_REJECT` 审计；同一游记的其他待处理举报 MUST NOT 被联动处理。

#### Scenario: Reject leaves note untouched
- **WHEN** 管理员驳回一条举报
- **THEN** 游记保持原状态，仅该举报变为已驳回

#### Scenario: Other reports are unaffected
- **WHEN** 同一游记存在多条待处理举报且管理员驳回其中一条
- **THEN** 其余举报仍为待处理

#### Scenario: Reject is audited
- **WHEN** 管理员驳回举报
- **THEN** `audit_log` 记录 `REPORT_REJECT`

### Requirement: Admin-only report management
举报列表、统计、详情、采纳与驳回接口 MUST 仅允许 `ADMIN` 角色访问；普通用户 MUST 返回 `4002`。

#### Scenario: Normal user cannot manage reports
- **WHEN** 普通用户调用举报管理接口
- **THEN** 系统返回 `4002`
