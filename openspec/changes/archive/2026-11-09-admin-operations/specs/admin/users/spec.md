## ADDED Requirements

### Requirement: User detail
管理员 MUST 能查看指定用户详情：基础资料、角色与状态、当日免费额度用量、次数包余额、VIP 到期时间、游记/行程/旅行数量，以及最近的卡密兑换记录。已注销用户 MUST 只返回账号壳，不返回内容统计。详情接口 MUST 仅管理员可访问。

#### Scenario: Admin opens a user detail
- **WHEN** 管理员在用户列表点击某个用户
- **THEN** 展示额度、VIP、内容数量与兑换记录

#### Scenario: Deleted user has no content stats
- **WHEN** 管理员查看已注销用户
- **THEN** 只返回匿名化资料，不返回游记/行程统计

#### Scenario: Normal user cannot read admin detail
- **WHEN** 普通用户调用用户详情接口
- **THEN** 系统返回 `4002`

### Requirement: VIP expiry adjustment
管理员 MUST 能将用户 VIP 到期时间设置为指定时间；设置为过去时间 MUST 立即结束 VIP，设置为 `null` MUST 清除 VIP。按天数延长 MUST 继续使用现有的额度调整接口。两种操作 MUST 写审计日志，且 MUST 立即影响额度判断。

#### Scenario: Set a future expiry
- **WHEN** 管理员把用户 VIP 到期日设为下个月
- **THEN** 用户立即成为 VIP，额度按 VIP 上限计算

#### Scenario: Setting a past expiry revokes VIP
- **WHEN** 管理员把 VIP 到期日设为昨天
- **THEN** 用户立即失去 VIP 权限，审计记录该操作

#### Scenario: Extending days still works
- **WHEN** 管理员通过额度调整增加 VIP 天数
- **THEN** 到期日从「当前到期日」与「今天」中较晚者开始累加