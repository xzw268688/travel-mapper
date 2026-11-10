# admin/feedback Specification

## Purpose
为平台管理员提供用户反馈的集中查看、筛选、状态处理和内部备注能力，确保处理过程可追踪、可审计，且不向用户泄露内部信息。

## Requirements

### Requirement: Feedback listing and statistics
管理员 MUST 能分页查询全部用户反馈，并可按分类、状态、关键词和创建日期范围筛选；结果 MUST 按创建时间倒序。关键词 MUST 匹配标题、正文、联系方式以及提交人用户名/昵称。系统 MUST 提供按状态汇总的总数统计。已注销用户提交的反馈 MUST 显示为「已注销用户」，不得展示其原用户名或昵称。

#### Scenario: Filter by status and type
- **WHEN** 管理员按 `status=NEW` 与 `type=BUG` 查询
- **THEN** 只返回同时满足两个条件的反馈，按创建时间倒序

#### Scenario: Keyword matches content and submitter
- **WHEN** 管理员以某关键词查询，该词出现在正文或提交人昵称中
- **THEN** 对应反馈出现在结果中

#### Scenario: Date range filters by creation time
- **WHEN** 管理员指定 `from` 与 `to` 日期
- **THEN** 只返回创建日期落在闭区间内的反馈

#### Scenario: Statistics reflect current counts
- **WHEN** 管理员请求统计
- **THEN** 返回待处理、处理中、已处理的数量以及总数

#### Scenario: Deleted submitter is anonymized
- **WHEN** 反馈来自已注销用户
- **THEN** 列表与详情显示「已注销用户」，不显示原用户名或昵称

### Requirement: Feedback detail
管理员 MUST 能查看单条反馈详情，包含完整正文、联系方式、页面路径、UA、版本号、状态、内部备注、处理人与处理时间，以及提交人摘要。反馈不存在时 MUST 返回 `2031`。

#### Scenario: Detail includes context
- **WHEN** 管理员打开一条反馈详情
- **THEN** 返回正文、联系方式、页面路径、UA、版本号与处理信息

#### Scenario: Unknown feedback
- **WHEN** 管理员请求不存在的反馈 ID
- **THEN** 系统返回 `2031`

### Requirement: Update status and internal note
管理员 MUST 能将反馈状态更新为 `NEW`、`PROCESSING` 或 `RESOLVED` 之一，并可填写或修改仅管理员可见的内部备注；内部备注 MUST ≤1000 字。请求 MUST 至少包含状态或内部备注之一。成功更新后系统 MUST 记录最后处理人、处理时间，并写入审计日志 `FEEDBACK_UPDATE`，审计详情 MUST 包含反馈 ID、原状态与新状态，MUST NOT 包含反馈正文或内部备注全文。

#### Scenario: Status update is audited
- **WHEN** 管理员将反馈从 `NEW` 改为 `PROCESSING`
- **THEN** 状态更新成功，`handledBy`、`handledAt` 被记录，审计日志新增 `FEEDBACK_UPDATE`

#### Scenario: Internal note can be saved alone
- **WHEN** 管理员只提交内部备注而不改变状态
- **THEN** 备注保存成功，状态保持不变，仍写入审计日志

#### Scenario: Invalid status is rejected
- **WHEN** 管理员提交枚举外的状态值或超过 1000 字的备注
- **THEN** 系统返回 `2001`，反馈保持不变

#### Scenario: Empty update is rejected
- **WHEN** 管理员提交既无状态也无备注的请求
- **THEN** 系统返回 `2001`

### Requirement: Admin-only access
全部反馈管理接口 MUST 仅允许 `ADMIN` 角色访问；普通用户 MUST 返回 `4002`。

#### Scenario: Normal user cannot access admin feedback
- **WHEN** 普通用户调用反馈列表、统计、详情或更新接口
- **THEN** 系统返回 `4002`
