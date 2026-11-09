## MODIFIED Requirements

### Requirement: Deletion anonymization checklist
注销成功后，系统 MUST 按以下规则处理：`user` 改为 `status=DELETED`、`deleted=1`、用户名 `deleted_{id}`、邮箱 `deleted_{id}@deleted.local`、昵称“已注销用户”、清空密码与 VIP；`email_token` 全部置已使用；`trip`、`itinerary`、`travel_note`、`expense`、`trip_checklist` 软删除并匿名化文本；`note_share` 全部撤销；`quota_pack` 物理删除；`quota_ledger` 与 `audit_log` 保留并脱敏；`feedback` MUST 保留记录骨架，但清空标题、正文、联系方式与管理员内部备注；`note_report` MUST 保留举报记录、原因与状态，但把该用户作为举报人的 `reporter_user_id` 置空，并清空该用户所写游记相关举报的标题快照与纯文本快照。

#### Scenario: Personal content is no longer searchable
- **WHEN** 账号注销后管理员在后台检索该用户内容
- **THEN** 行程、游记、消费、旅行均不可检索；用户列表仅显示已注销账号壳

#### Scenario: Shares are immediately invalid
- **WHEN** 账号注销后访问该用户此前的分享链接
- **THEN** 分享页返回 `2007`“分享已失效”

#### Scenario: Quota assets are removed
- **WHEN** 账号注销完成
- **THEN** 该用户的 `quota_pack` 批次被物理删除，`quota_ledger` 仍保留用于审计

#### Scenario: Audit remains traceable
- **WHEN** 注销后查询审计日志
- **THEN** 相关记录仍存在，但详情已脱敏，不包含昵称、邮箱或正文

#### Scenario: Feedback skeleton remains without content
- **WHEN** 账号注销后管理员查看该用户此前的反馈
- **THEN** 反馈记录仍存在，显示「已注销用户」，标题、正文、联系方式与内部备注为空，分类、状态与时间保留

#### Scenario: Report identity and snapshots are anonymized
- **WHEN** 账号注销后管理员查看该用户提交的举报，或查看该用户游记相关的举报
- **THEN** 举报人显示「匿名访客」，相关标题与纯文本快照为空，原因、状态与时间保留