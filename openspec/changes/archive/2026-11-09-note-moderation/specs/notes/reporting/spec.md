## Purpose

让分享页访客在不公开身份的前提下举报不恰当游记，并保存可追溯的内容证据与严格的去重、限流约束，为管理员审核提供可靠输入。

## ADDED Requirements

### Requirement: Submit report from share page
任何访问有效分享页的用户（含未登录访客）MUST 能举报该游记。举报 MUST 包含原因分类：`SPAM`（垃圾广告）、`ABUSE`（辱骂攻击）、`PORN`（色情低俗）、`ILLEGAL`（违法信息）、`PRIVACY`（侵犯隐私）、`OTHER`（其他）；补充说明 MAY 为空且 MUST ≤200 字，选择 `OTHER` 时说明 MUST 必填。分享令牌不存在、已撤销、已过期、游记已软删除、已下架或作者已注销时 MUST 返回 `2007`。登录用户提交时 MUST 记录 `reporter_user_id`，匿名访客 MUST 记录为 NULL。作者本人 MUST NOT 举报自己的游记，返回 `2001`。

#### Scenario: Anonymous visitor reports a shared note
- **WHEN** 未登录访客在有效分享页提交合法原因分类
- **THEN** 举报保存成功，`reporter_user_id` 为 NULL

#### Scenario: Logged-in user is recorded
- **WHEN** 登录用户通过分享页举报
- **THEN** 举报保存成功并记录其用户 ID

#### Scenario: Other reason requires description
- **WHEN** 举报原因选择 `OTHER` 但未填写说明
- **THEN** 系统返回 `2001`，不保存

#### Scenario: Invalid share token is rejected
- **WHEN** 使用失效令牌提交举报
- **THEN** 系统返回 `2007`

#### Scenario: Author cannot report own note
- **WHEN** 游记作者通过分享页举报自己的游记
- **THEN** 系统返回 `2001`，不保存举报

### Requirement: Report evidence snapshot
举报成功时系统 MUST 保存举报时刻的游记标题、纯文本正文摘要与 `note_updated_at`。快照 MUST NOT 对举报人或其他普通用户公开，仅管理员可见。作者后续编辑或删除游记 MUST NOT 改变已保存的快照；作者注销账号时快照按账号注销规则清空。

#### Scenario: Snapshot survives later edits
- **WHEN** 举报后作者修改游记标题与正文
- **THEN** 举报记录仍保留举报时的标题与纯文本摘要

#### Scenario: Snapshot is admin-only
- **WHEN** 普通用户或匿名访客查询举报结果
- **THEN** 响应不包含快照正文

### Requirement: Report deduplication and rate limits
同一登录用户对同一游记 MUST 只保留一条 `PENDING` 举报；重复提交 MUST 返回成功且 `duplicate=true`，不新增记录。匿名举报 MUST 按「IP + 游记」24 小时去重，同一 IP 每天最多提交 5 条举报；超过限制 MUST 返回 `5004`。IP MUST NOT 持久化存储。

#### Scenario: Logged-in duplicate is collapsed
- **WHEN** 同一登录用户对同一游记再次提交待处理举报
- **THEN** 返回成功但 `duplicate=true`，待处理举报仍只有一条

#### Scenario: Anonymous duplicate within 24 hours
- **WHEN** 同一 IP 在 24 小时内对同一游记再次举报
- **THEN** 返回成功但 `duplicate=true`，不新增记录

#### Scenario: Daily anonymous limit
- **WHEN** 同一 IP 当天已提交 5 条举报后再次提交
- **THEN** 系统返回 `5004`

#### Scenario: IP is not stored
- **WHEN** 查询举报数据
- **THEN** 不存在举报人 IP 字段

### Requirement: Report response privacy
举报提交响应 MUST 只返回举报标识、是否重复与创建时间；MUST NOT 返回其他举报、举报总数、管理员处理状态或游记作者信息。

#### Scenario: Response contains only receipt fields
- **WHEN** 举报提交成功
- **THEN** 响应仅含 `reportId`、`duplicate` 与 `createdAt`